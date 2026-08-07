// SPDX-FileCopyrightText: Copyright 2023 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

package org.yuzu.yuzu_emu.utils

import org.yuzu.yuzu_emu.model.Game

object GameMetadata {
    external fun getGame(path: String): Game?

    external fun getTitle(path: String): String

    external fun getProgramId(path: String): String

    external fun getDeveloper(path: String): String

    external fun getVersion(path: String, reload: Boolean): String

    external fun getIcon(path: String): ByteArray

    external fun getIsHomebrew(path: String): Boolean

    external fun removeMetadata(path: String)

    external fun resetMetadata()
}
