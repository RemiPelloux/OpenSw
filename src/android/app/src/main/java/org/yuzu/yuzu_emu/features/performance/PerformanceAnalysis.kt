// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import kotlin.math.ceil

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
    return nearestRankPercentile(samples, 0.95)
}

internal fun nearestRankPercentile(samples: Collection<Double>, percentile: Double): Double {
    if (samples.isEmpty()) return 0.0
    val sorted = samples.sorted()
    val rank = percentile.coerceIn(0.0, 1.0)
    val index = (ceil(sorted.size * rank).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
}

internal data class PerformanceCaptureSummary(
    val frameTimeP50Ms: Double,
    val frameTimeP95Ms: Double,
    val frameTimeP99Ms: Double,
    val medianFps: Double,
    val sampleCount: Int,
    val maxRssMb: Long,
    val maxTemperatureC: Float
)

internal fun summarizeCapture(samples: Collection<PerformanceSnapshot>): PerformanceCaptureSummary {
    val frameTimes = samples.map(PerformanceSnapshot::frameTimeMs)
        .filter { it.isFinite() && it > 0.0 }
    val fps = samples.map(PerformanceSnapshot::fps).filter { it.isFinite() && it > 0.0 }
    return PerformanceCaptureSummary(
        frameTimeP50Ms = nearestRankPercentile(frameTimes, 0.50),
        frameTimeP95Ms = nearestRankPercentile(frameTimes, 0.95),
        frameTimeP99Ms = nearestRankPercentile(frameTimes, 0.99),
        medianFps = nearestRankPercentile(fps, 0.50),
        sampleCount = frameTimes.size,
        maxRssMb = samples.maxOfOrNull(PerformanceSnapshot::appRssMb) ?: 0L,
        maxTemperatureC = samples.maxOfOrNull(PerformanceSnapshot::batteryTemperatureC) ?: 0f
    )
}

internal enum class PerformanceHealth {
    WAITING,
    STABLE,
    UNEVEN,
    SLOW,
    THERMAL
}

internal fun performanceHealth(snapshot: PerformanceSnapshot): PerformanceHealth {
    if (snapshot.thermalWarning) return PerformanceHealth.THERMAL
    if (snapshot.performanceWarning) return PerformanceHealth.SLOW
    if (snapshot.fps <= 0.0 || snapshot.frameTimeP95Ms <= 0.0) return PerformanceHealth.WAITING

    val frameBudgetMs = 1_000.0 / snapshot.fps.coerceAtLeast(1.0)
    return if (
        snapshot.emulationSpeed >= 0.95 &&
        snapshot.frameTimeP95Ms <= frameBudgetMs * 1.25
    ) {
        PerformanceHealth.STABLE
    } else {
        PerformanceHealth.UNEVEN
    }
}
