// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.migration

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant

enum class EdenImportCategory(
    val label: Int,
    val sourcePath: String,
    val destinationPath: String
) {
    Keys(R.string.eden_import_keys, "keys", "keys"),
    Firmware(
        R.string.eden_import_firmware,
        "nand/system/Contents/registered",
        "nand/system/Contents/registered"
    ),
    Profiles(
        R.string.eden_import_profiles,
        "nand/system/save/8000000000000010",
        "nand/system/save/8000000000000010"
    ),
    Saves(R.string.eden_import_saves, "nand/user/save", "nand/user/save"),
    Settings(R.string.eden_import_settings, "config", "config"),
    Mods(R.string.eden_import_mods, "load", "load"),
    Cheats(R.string.eden_import_cheats, "load", "load")
}

data class EdenImportReport(
    val importedFiles: Int,
    val importedBytes: Long,
    val backups: Int,
    val skippedCategories: List<EdenImportCategory>
)

class EdenImportManager(private val context: Context) {
    private data class SourceFile(
        val category: EdenImportCategory,
        val document: DocumentFile,
        val relativePath: String,
        val size: Long
    )

    private data class AppliedFile(
        val destination: File,
        val backup: File?
    )

    fun import(treeUri: Uri, categories: Set<EdenImportCategory>): EdenImportReport {
        val sourceRoot = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Eden provider root is inaccessible")
        val destinationRoot = File(
            DirectoryInitialization.userDirectory
                ?: throw IOException("OpenSw storage is not initialized")
        ).canonicalFile
        val stagingRoot = File(context.cacheDir, "eden-import-${System.currentTimeMillis()}")
        val backupRoot = File(
            destinationRoot,
            "import-backups/${Instant.now().toString().replace(':', '-')}"
        )

        val skipped = mutableListOf<EdenImportCategory>()
        val sources = categories.flatMap { category ->
            val categoryRoot = resolve(sourceRoot, category.sourcePath)
            if (categoryRoot == null) {
                skipped += category
                emptyList()
            } else {
                collect(category, categoryRoot)
            }
        }
        val totalBytes = sources.sumOf { it.size.coerceAtLeast(0L) }
        val backupBytes = sources.sumOf { source ->
            checkedDestination(destinationRoot, source.category, source.relativePath)
                .takeIf(File::exists)
                ?.length()
                ?: 0L
        }
        val largestFile = sources.maxOfOrNull { it.size.coerceAtLeast(0L) } ?: 0L
        val requiredBytes = totalBytes + backupBytes + largestFile
        val availableBytes = StatFs(destinationRoot.absolutePath).availableBytes
        if (availableBytes < requiredBytes) {
            throw IOException(
                "Insufficient space: $requiredBytes bytes required, $availableBytes available"
            )
        }

        stagingRoot.mkdirs()
        val hashes = mutableMapOf<SourceFile, String>()
        try {
            sources.forEach { source ->
                val staged = checkedDestination(stagingRoot, source.category, source.relativePath)
                staged.parentFile?.mkdirs()
                val sourceHash = copyAndHash(source.document, staged)
                val stagedHash = hash(staged)
                if (sourceHash != stagedHash || (source.size > 0 && staged.length() != source.size)) {
                    throw IOException("Incomplete staging copy: ${source.relativePath}")
                }
                hashes[source] = stagedHash
            }

            val applied = mutableListOf<AppliedFile>()
            var backups = 0
            try {
                sources.forEach { source ->
                    val staged = checkedDestination(
                        stagingRoot,
                        source.category,
                        source.relativePath
                    )
                    val destination = checkedDestination(
                        destinationRoot,
                        source.category,
                        source.relativePath
                    )
                    destination.parentFile?.mkdirs()
                    val backup = if (destination.exists()) {
                        checkedDestination(backupRoot, source.category, source.relativePath).also {
                            it.parentFile?.mkdirs()
                            copyWithSync(destination, it)
                            if (hash(it) != hash(destination)) {
                                throw IOException("Backup verification failed: ${source.relativePath}")
                            }
                            backups++
                        }
                    } else {
                        null
                    }

                    installStagedFile(staged, destination, hashes.getValue(source), backup)
                    applied += AppliedFile(destination, backup)
                }
            } catch (error: Exception) {
                applied.asReversed().forEach { file ->
                    runCatching { restore(file.destination, file.backup) }
                        .exceptionOrNull()
                        ?.let(error::addSuppressed)
                }
                throw error
            }

            return EdenImportReport(sources.size, totalBytes, backups, skipped.distinct())
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun resolve(root: DocumentFile, path: String): DocumentFile? =
        path.split('/').fold(root as DocumentFile?) { current, segment ->
            current?.findFile(segment)
        }

    private fun collect(
        category: EdenImportCategory,
        root: DocumentFile,
        relativePath: String = ""
    ): List<SourceFile> {
        if (root.isFile) {
            return if (include(category, relativePath)) {
                listOf(SourceFile(category, root, relativePath.ifEmpty { root.name.orEmpty() }, root.length()))
            } else {
                emptyList()
            }
        }
        return root.listFiles().flatMap { child ->
            val name = checkedName(child.name)
            val childPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            collect(category, child, childPath)
        }
    }

    private fun include(category: EdenImportCategory, relativePath: String): Boolean {
        val normalized = "/${relativePath.lowercase()}"
        val isCheat = normalized.contains("/cheats/") ||
            normalized.substringAfterLast('/').startsWith("cheat_")
        return when (category) {
            EdenImportCategory.Mods -> !isCheat
            EdenImportCategory.Cheats -> isCheat
            else -> true
        }
    }

    private fun checkedName(name: String?): String {
        val value = name ?: throw IOException("A source document has no name")
        if (value == "." || value == ".." || value.contains('/') || value.contains('\\')) {
            throw IOException("Unsafe source document name")
        }
        return value
    }

    private fun checkedDestination(
        root: File,
        category: EdenImportCategory,
        relativePath: String
    ): File {
        val categoryRoot = File(root, category.destinationPath).canonicalFile
        val destination = File(categoryRoot, relativePath).canonicalFile
        if (destination != categoryRoot &&
            !destination.path.startsWith(categoryRoot.path + File.separator)
        ) {
            throw IOException("Unsafe import path")
        }
        return destination
    }

    private fun copyAndHash(source: DocumentFile, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(source.uri).use { input ->
            if (input == null) throw IOException("Cannot read ${source.name}")
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        return digest.digest().toHex()
    }

    private fun installStagedFile(
        staged: File,
        destination: File,
        expectedHash: String,
        backup: File?
    ) {
        val temporary = File.createTempFile(".opensw-import-", ".tmp", destination.parentFile)
        try {
            copyWithSync(staged, temporary)
            if (hash(temporary) != expectedHash) {
                throw IOException("Commit verification failed: ${destination.name}")
            }
            Os.rename(temporary.absolutePath, destination.absolutePath)
        } catch (error: Exception) {
            runCatching { restore(destination, backup) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        } finally {
            temporary.delete()
        }
    }

    private fun restore(destination: File, backup: File?) {
        if (backup == null) {
            if (destination.exists() && !destination.delete()) {
                throw IOException("Cannot roll back ${destination.name}")
            }
            return
        }
        destination.parentFile?.mkdirs()
        val temporary = File.createTempFile(".opensw-rollback-", ".tmp", destination.parentFile)
        try {
            copyWithSync(backup, temporary)
            if (hash(temporary) != hash(backup)) {
                throw IOException("Rollback verification failed: ${destination.name}")
            }
            Os.rename(temporary.absolutePath, destination.absolutePath)
        } finally {
            temporary.delete()
        }
    }

    private fun copyWithSync(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
