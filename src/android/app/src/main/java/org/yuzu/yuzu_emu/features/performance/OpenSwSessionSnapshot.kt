// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

enum class OpenSwSessionState { STOPPED, STARTING, RUNNING, PAUSED, STOPPING }

data class OpenSwSessionSnapshot(
    val generation: Long,
    val state: OpenSwSessionState,
    val titleId: Long,
    val surfaceAttached: Boolean
) {
    companion object {
        private const val SCHEMA_VERSION = 1L

        fun from(values: LongArray): OpenSwSessionSnapshot? {
            if (values.size < 5 || values[0] != SCHEMA_VERSION) return null
            val state = OpenSwSessionState.entries.getOrNull(values[2].toInt()) ?: return null
            return OpenSwSessionSnapshot(values[1], state, values[3], values[4] != 0L)
        }
    }
}
