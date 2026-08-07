// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cheats

import org.yuzu.yuzu_emu.utils.Log

object BundledCheats {
    internal val foretales60Fps = CatalogCheat(
        titleId = "010026801939E000",
        buildId = "F95A034961AF4385",
        source = "Foretales-60FPS",
        author = "OpenSw",
        date = "2026-08-06",
        sha256 = "ffbad1932b1e94e9d4a324160836a2f552082616b82dba0209e63be7ea5b0a42",
        text = """
            {OpenSw Foretales 60 FPS}
            04000000 028F4194 52800020
            04000000 028F41A0 52800780
        """.trimIndent()
    )

    fun installDefaults() {
        listOf(foretales60Fps).forEach { cheat ->
            runCatching {
                val destination = CheatInstaller.destination(cheat)
                if (!destination.exists()) {
                    CheatInstaller.install(cheat, overwrite = false)
                    Log.info("[OpenSw] Installed bundled cheat ${cheat.source}")
                }
            }.onFailure { error ->
                Log.warning("[OpenSw] Failed to install ${cheat.source}: ${error.message}")
            }
        }
    }
}
