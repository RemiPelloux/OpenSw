// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

internal class SustainedCondition(private val durationMs: Long) {
    private var activeSinceMs: Long? = null

    fun update(nowMs: Long, active: Boolean): Boolean {
        if (!active) {
            activeSinceMs = null
            return false
        }
        if (activeSinceMs == null) activeSinceMs = nowMs
        return nowMs - activeSinceMs!! >= durationMs
    }

    fun reset() {
        activeSinceMs = null
    }
}

internal fun percentile95(samples: Collection<Double>): Double {
    if (samples.isEmpty()) return 0.0
    val sorted = samples.sorted()
    val index = ((sorted.size - 1) * 0.95).toInt().coerceIn(sorted.indices)
    return sorted[index]
}
