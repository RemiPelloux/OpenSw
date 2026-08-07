// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

// SPDX-FileCopyrightText: 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

package org.yuzu.yuzu_emu.utils

import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.preference.PreferenceManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.model.GameDir
import org.yuzu.yuzu_emu.model.MinimalDocumentFile
import androidx.core.content.edit
import androidx.core.net.toUri

object GameHelper {
    private const val KEY_OLD_GAME_PATH = "game_path"
    const val KEY_GAMES = "Games"

    @Volatile
    var cachedGameList: List<Game> = emptyList()

    private lateinit var preferences: SharedPreferences
    private val metadataFingerprints = mutableMapOf<String, MetadataFingerprint>()

    @Synchronized
    fun getGames(forceMetadataRefresh: Boolean = false): List<Game> {
        val games = mutableListOf<Game>()
        val context = YuzuApplication.appContext
        preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val gameDirs = mutableListOf<GameDir>()
        val oldGamesDir = preferences.getString(KEY_OLD_GAME_PATH, "") ?: ""
        if (oldGamesDir.isNotEmpty()) {
            gameDirs.add(GameDir(oldGamesDir, true))
            preferences.edit() { remove(KEY_OLD_GAME_PATH) }
        }
        gameDirs.addAll(NativeConfig.getGameDirs())

        // Ensure keys are loaded so that ROM metadata can be decrypted.
        NativeLibrary.reloadKeys()

        // Remove previous filesystem provider information so we can get up to date version info
        NativeLibrary.clearFilesystemProvider()

        val directoryListings = mutableMapOf<String, Array<MinimalDocumentFile>>()
        val mountedContainerUris = mutableSetOf<String>()
        mountExternalContentDirectories(mountedContainerUris, directoryListings)

        val badDirs = mutableListOf<Int>()
        val gameFiles = linkedMapOf<String, MinimalDocumentFile>()
        val visitedGameDirectories = mutableSetOf<String>()
        gameDirs.forEachIndexed { index: Int, gameDir: GameDir ->
            val gameDirUri = gameDir.uriString.toUri()
            val isValid = FileUtil.isTreeUriValid(gameDirUri)
            if (isValid) {
                val scanDepth = if (gameDir.deepScan) 3 else 1
                collectFilesRecursive(
                    gameFiles,
                    listFilesCached(gameDirUri, directoryListings),
                    scanDepth,
                    directoryListings,
                    visitedGameDirectories
                )
            } else {
                badDirs.add(index)
            }
        }

        // Register every container before reading metadata so updates are visible regardless of
        // the directory iteration order. The collected list also avoids a second SAF traversal.
        gameFiles.values.forEach { file ->
            val extension = file.extension
            val filePath = file.uri.toString()
            if (externalContentExtensions.contains(extension) &&
                mountedContainerUris.add(filePath)
            ) {
                NativeLibrary.addGameFolderFileToFilesystemProvider(filePath)
            }
        }
        val launchableFiles = gameFiles.values.filter { Game.extensions.contains(it.extension) }
        updateMetadataCache(launchableFiles, forceMetadataRefresh)
        launchableFiles.forEach { file ->
            getGame(file.uri, false, file.filename)?.let(games::add)
        }

        // Remove all game dirs with insufficient permissions from config
        if (badDirs.isNotEmpty()) {
            var offset = 0
            badDirs.forEach {
                gameDirs.removeAt(it - offset)
                offset++
            }
        }
        NativeConfig.setGameDirs(gameDirs.toTypedArray())
        storeAddedTimes(games)

        // Cache list of games found on disk
        val serializedGames = mutableSetOf<String>()
        games.forEach {
            serializedGames.add(Json.encodeToString(it))
        }
        preferences.edit() {
            remove(KEY_GAMES)
                .putStringSet(KEY_GAMES, serializedGames)
        }

        cachedGameList = games.toList()
        return games.toList()
    }

    @Synchronized
    fun restoreContentForGame(game: Game) {
        NativeLibrary.reloadKeys()

        val mountedContainerUris = mutableSetOf<String>()
        val directoryListings = mutableMapOf<String, Array<MinimalDocumentFile>>()
        mountExternalContentDirectories(mountedContainerUris, directoryListings)
        mountGameFolderContent(Uri.parse(game.path), mountedContainerUris, directoryListings)
        NativeLibrary.addFileToFilesystemProvider(game.path)
    }

    // File extensions considered as external content, buuut should
    // be done better imo.
    private val externalContentExtensions = setOf("nsp", "xci")

    private fun scanContentContainersRecursive(
        files: Array<MinimalDocumentFile>,
        depth: Int,
        directoryListings: MutableMap<String, Array<MinimalDocumentFile>>,
        visitedDirectories: MutableSet<String>,
        onContainerFound: (MinimalDocumentFile) -> Unit
    ) {
        if (depth <= 0) {
            return
        }

        files.forEach {
            if (it.isDirectory) {
                if (visitedDirectories.add(it.uri.toString())) {
                    scanContentContainersRecursive(
                        listFilesCached(it.uri, directoryListings),
                        depth - 1,
                        directoryListings,
                        visitedDirectories,
                        onContainerFound
                    )
                }
            } else {
                if (externalContentExtensions.contains(it.extension)) {
                    onContainerFound(it)
                }
            }
        }
    }

    private fun collectFilesRecursive(
        output: MutableMap<String, MinimalDocumentFile>,
        files: Array<MinimalDocumentFile>,
        depth: Int,
        directoryListings: MutableMap<String, Array<MinimalDocumentFile>>,
        visitedDirectories: MutableSet<String>
    ) {
        if (depth <= 0) {
            return
        }

        files.forEach { file ->
            if (file.isDirectory) {
                if (visitedDirectories.add(file.uri.toString())) {
                    collectFilesRecursive(
                        output,
                        listFilesCached(file.uri, directoryListings),
                        depth - 1,
                        directoryListings,
                        visitedDirectories
                    )
                }
            } else {
                output.putIfAbsent(file.uri.toString(), file)
            }
        }
    }

    private fun mountExternalContentDirectories(
        mountedContainerUris: MutableSet<String>,
        directoryListings: MutableMap<String, Array<MinimalDocumentFile>>
    ) {
        val uniqueExternalContentDirs = linkedSetOf<String>()
        NativeConfig.getExternalContentDirs().forEach { externalDir ->
            if (externalDir.isNotEmpty()) {
                uniqueExternalContentDirs.add(externalDir)
            }
        }

        val visitedDirectories = mutableSetOf<String>()
        for (externalDir in uniqueExternalContentDirs) {
            val externalDirUri = externalDir.toUri()
            if (FileUtil.isTreeUriValid(externalDirUri)) {
                if (!visitedDirectories.add(externalDir)) {
                    continue
                }
                scanContentContainersRecursive(
                    listFilesCached(externalDirUri, directoryListings),
                    3,
                    directoryListings,
                    visitedDirectories
                ) {
                    val containerUri = it.uri.toString()
                    if (mountedContainerUris.add(containerUri)) {
                        NativeLibrary.addFileToFilesystemProvider(containerUri)
                    }
                }
            }
        }
    }

    private fun mountGameFolderContent(
        gameUri: Uri,
        mountedContainerUris: MutableSet<String>,
        directoryListings: MutableMap<String, Array<MinimalDocumentFile>>
    ) {
        if (gameUri.scheme == "content") {
            val parentUri = getParentDocumentUri(gameUri) ?: return
            scanContentContainersRecursive(
                listFilesCached(parentUri, directoryListings),
                1,
                directoryListings,
                mutableSetOf(parentUri.toString())
            ) {
                val containerUri = it.uri.toString()
                if (mountedContainerUris.add(containerUri)) {
                    NativeLibrary.addGameFolderFileToFilesystemProvider(containerUri)
                }
            }
            return
        }

        val gameFile = File(gameUri.path ?: gameUri.toString())
        val parentDir = gameFile.parentFile ?: return
        parentDir.listFiles()?.forEach { sibling ->
            if (!sibling.isFile) {
                return@forEach
            }

            val extension = sibling.extension.lowercase()
            if (externalContentExtensions.contains(extension)) {
                val containerUri = Uri.fromFile(sibling).toString()
                if (mountedContainerUris.add(containerUri)) {
                    NativeLibrary.addGameFolderFileToFilesystemProvider(containerUri)
                }
            }
        }
    }

    private fun getParentDocumentUri(uri: Uri): Uri? {
        return try {
            val documentId = DocumentsContract.getDocumentId(uri)
            val separatorIndex = documentId.lastIndexOf('/')
            if (separatorIndex == -1) {
                null
            } else {
                val parentDocumentId = documentId.substring(0, separatorIndex)
                DocumentsContract.buildDocumentUriUsingTree(uri, parentDocumentId)
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getGame(
        uri: Uri,
        registerFilesystemProvider: Boolean = true,
        knownFilename: String? = null
    ): Game? {
        val filePath = uri.toString()
        if (registerFilesystemProvider) {
            // Needed to update installed content information
            NativeLibrary.addFileToFilesystemProvider(filePath)
        }

        val metadata = GameMetadata.getGame(filePath) ?: return null
        var name = metadata.title

        // If the game's title field is empty, use the filename.
        if (name.isEmpty()) {
            name = knownFilename ?: FileUtil.getFilename(uri)
        }
        var programId = metadata.programId

        // If the game's ID field is empty, use the filename without extension.
        if (programId.isEmpty()) {
            programId = name.substring(0, name.lastIndexOf("."))
        }

        val newGame = Game(
            name,
            filePath,
            programId,
            metadata.developer,
            metadata.version,
            metadata.isHomebrew
        )
        Log.info("[GameHelper] Metadata ${newGame.programIdHex} version=${newGame.version}")

        return newGame
    }

    private fun listFilesCached(
        uri: Uri,
        directoryListings: MutableMap<String, Array<MinimalDocumentFile>>
    ): Array<MinimalDocumentFile> =
        directoryListings.getOrPut(uri.toString()) { FileUtil.listFiles(uri) }

    private fun updateMetadataCache(
        files: List<MinimalDocumentFile>,
        forceMetadataRefresh: Boolean
    ) {
        if (forceMetadataRefresh) {
            GameMetadata.resetMetadata()
        } else {
            val currentPaths = files.mapTo(mutableSetOf()) { it.uri.toString() }
            metadataFingerprints.keys
                .filterNot(currentPaths::contains)
                .forEach(GameMetadata::removeMetadata)
            files.forEach { file ->
                val path = file.uri.toString()
                val fingerprint = MetadataFingerprint(file.size, file.lastModified)
                if (!fingerprint.isReliable || metadataFingerprints[path] != fingerprint) {
                    GameMetadata.removeMetadata(path)
                }
            }
        }

        metadataFingerprints.clear()
        files.associateTo(metadataFingerprints) { file ->
            file.uri.toString() to MetadataFingerprint(file.size, file.lastModified)
        }
    }

    private fun storeAddedTimes(games: List<Game>) {
        val newGames = games.filter { preferences.getLong(it.keyAddedToLibraryTime, 0L) == 0L }
        if (newGames.isEmpty()) return

        val addedTime = System.currentTimeMillis()
        preferences.edit {
            newGames.forEach { game -> putLong(game.keyAddedToLibraryTime, addedTime) }
        }
    }

    private data class MetadataFingerprint(val size: Long?, val lastModified: Long?) {
        val isReliable: Boolean = size != null && lastModified != null && lastModified > 0L
    }

    private val MinimalDocumentFile.extension: String
        get() = filename.substringAfterLast('.', "").lowercase()
}
