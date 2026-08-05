// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cheats

import android.system.Os
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class CheatDiff(
    val added: Set<String>,
    val removed: Set<String>,
    val modified: Set<String>
)

object CheatInstaller {
    fun destination(cheat: CatalogCheat): File {
        val titleId = CheatTextValidator.requireBuildId(cheat.titleId)
        val buildId = CheatTextValidator.requireBuildId(cheat.buildId)
        val safeSource = cheat.source.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val userDirectory = DirectoryInitialization.userDirectory
            ?: throw IOException("OpenSw storage is not initialized")
        return File(
            userDirectory,
            "load/$titleId/OpenSw-$safeSource/cheats/$buildId.txt"
        )
    }

    fun diff(existing: String, incoming: String): CheatDiff {
        val oldSections = CheatTextValidator.validate(existing).sections
        val newSections = CheatTextValidator.validate(incoming).sections
        return CheatDiff(
            newSections.keys - oldSections.keys,
            oldSections.keys - newSections.keys,
            newSections.keys.intersect(oldSections.keys).filterTo(mutableSetOf()) {
                newSections[it] != oldSections[it]
            }
        )
    }

    fun install(cheat: CatalogCheat, overwrite: Boolean): File {
        CheatTextValidator.validate(cheat.text)
        val destination = destination(cheat)
        destination.parentFile?.mkdirs()
        if (destination.exists() && !overwrite) {
            throw FileAlreadyExistsException(destination)
        }
        if (destination.exists()) {
            destination.copyTo(
                File(destination.parentFile, "${destination.name}.bak-${System.currentTimeMillis()}"),
                overwrite = false
            )
        }
        val temporary = File.createTempFile(".${destination.name}", ".tmp", destination.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(cheat.text.toByteArray())
                output.fd.sync()
            }
            Os.rename(temporary.absolutePath, destination.absolutePath)
            return destination
        } finally {
            temporary.delete()
        }
    }
}
