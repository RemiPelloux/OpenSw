// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cheats

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipInputStream

class CheatImportManager(private val context: Context) {
    fun fromDocument(uri: Uri, titleId: String): List<CatalogCheat> {
        val name = DocumentFile.fromSingleUri(context, uri)?.name
            ?: throw IOException("Selected document has no filename")
        return if (name.endsWith(".zip", ignoreCase = true)) {
            fromZip(uri, titleId)
        } else {
            val buildId = CheatTextValidator.requireBuildId(name.substringBeforeLast('.'))
            val text = context.contentResolver.openInputStream(uri).use { input ->
                input?.readLimited(CheatTextValidator.MAX_FILE_BYTES)
                    ?: throw IOException("Cannot read $name")
            }.toString(Charsets.UTF_8)
            listOf(candidate(titleId, buildId, text))
        }
    }

    fun fromTree(uri: Uri, titleId: String): List<CatalogCheat> {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("Selected tree is inaccessible")
        val normalizedTitleId = CheatTextValidator.requireBuildId(titleId)
        val candidates = mutableListOf<CatalogCheat>()
        collectTree(root, safeName(root.name), normalizedTitleId, candidates)
        return candidates.distinctBy { it.buildId to it.sha256 }
    }

    private fun collectTree(
        document: DocumentFile,
        path: String,
        titleId: String,
        output: MutableList<CatalogCheat>
    ) {
        require(output.size < MAX_ENTRIES) { "Import contains more than $MAX_ENTRIES files" }
        if (document.isDirectory) {
            document.listFiles().forEach { child ->
                val name = safeName(child.name)
                collectTree(child, if (path.isEmpty()) name else "$path/$name", titleId, output)
            }
            return
        }
        if (!path.endsWith(".txt", ignoreCase = true) ||
            !path.split('/').any { it.equals("cheats", ignoreCase = true) }
        ) return
        if (!CheatImportPolicy.acceptsTreePath(path, titleId)) return
        val buildId = CheatTextValidator.requireBuildId(path.substringAfterLast('/').substringBeforeLast('.'))
        val text = context.contentResolver.openInputStream(document.uri).use { input ->
            input?.readLimited(CheatTextValidator.MAX_FILE_BYTES)
                ?: throw IOException("Cannot read ${document.name}")
        }.toString(Charsets.UTF_8)
        output += candidate(titleId, buildId, text)
    }

    private fun fromZip(uri: Uri, titleId: String): List<CatalogCheat> {
        val output = mutableListOf<CatalogCheat>()
        var totalBytes = 0
        context.contentResolver.openInputStream(uri).use { raw ->
            if (raw == null) throw IOException("Cannot read ZIP")
            ZipInputStream(raw).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    require(count <= MAX_ENTRIES) { "ZIP contains more than $MAX_ENTRIES entries" }
                    val path = CheatImportPolicy.safeZipPath(entry.name)
                    if (!entry.isDirectory && path.endsWith(".txt", ignoreCase = true) &&
                        path.split('/').any { it.equals(titleId, ignoreCase = true) } &&
                        path.split('/').any { it.equals("cheats", ignoreCase = true) }
                    ) {
                        val buildId = CheatTextValidator.requireBuildId(
                            path.substringAfterLast('/').substringBeforeLast('.')
                        )
                        val bytes = zip.readLimited(CheatTextValidator.MAX_FILE_BYTES)
                        totalBytes += bytes.size
                        require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "ZIP exceeds 16 MiB" }
                        output += candidate(titleId, buildId, bytes.toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                }
            }
        }
        return output.distinctBy { it.buildId to it.sha256 }
    }

    private fun candidate(titleId: String, buildId: String, text: String): CatalogCheat {
        CheatTextValidator.validate(text)
        val sha = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHex()
        return CatalogCheat(
            CheatTextValidator.requireBuildId(titleId),
            buildId,
            "Imported",
            "Local import",
            Instant.now().toString(),
            sha,
            text
        )
    }

    private fun safeName(name: String?): String {
        val value = name ?: throw IOException("Document without filename")
        require(value != "." && value != ".." && !value.contains('/') && !value.contains('\\')) {
            "Unsafe document path"
        }
        return value
    }

    private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Imported file exceeds $limit bytes" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_ENTRIES = 256
        private const val MAX_UNCOMPRESSED_BYTES = 16 * 1024 * 1024
    }
}

internal object CheatImportPolicy {
    fun acceptsTreePath(path: String, titleId: String): Boolean {
        val titleDirectories = path.split('/').mapNotNull { segment ->
            runCatching { CheatTextValidator.requireBuildId(segment) }.getOrNull()
        }
        return titleDirectories.isEmpty() || titleId in titleDirectories
    }

    fun safeZipPath(name: String): String {
        val normalized = name.replace('\\', '/')
        require(
            !normalized.startsWith('/') &&
                normalized.split('/').none { it == ".." || it == "." }
        ) { "Unsafe ZIP path" }
        return normalized
    }
}
