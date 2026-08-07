// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.performance

import kotlin.math.ceil

data class PerformanceScenario(
    val name: String,
    val titleId: String,
    val gameVersion: String,
    val save: String,
    val route: String,
    val camera: String,
    val resolution: String,
    val profile: String,
    val firmwareHash: String,
    val driverHash: String
)

data class PerformanceSummary(
    val frameCount: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val medianFps: Double,
    val maxRssBytes: Long,
    val maxTemperatureC: Double,
    val scenario: PerformanceScenario
)

object PerformancePercentiles {
    fun nearestRank(values: List<Double>, percentile: Int): Double {
        require(percentile in 1..100)
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun summarize(
        frameTimestampsNanos: List<Long>,
        maxRssBytes: Long,
        maxTemperatureC: Double,
        scenario: PerformanceScenario
    ): PerformanceSummary {
        val intervalsMs = frameTimestampsNanos.zipWithNext { before, after ->
            (after - before).coerceAtLeast(0L) / 1_000_000.0
        }.filter { it > 0.0 && it.isFinite() }
        val p50 = nearestRank(intervalsMs, 50)
        val sortedFps = intervalsMs.map { 1000.0 / it }.sorted()
        val medianFps = when {
            sortedFps.isEmpty() -> 0.0
            sortedFps.size % 2 == 1 -> sortedFps[sortedFps.size / 2]
            else -> {
                val upper = sortedFps.size / 2
                (sortedFps[upper - 1] + sortedFps[upper]) / 2.0
            }
        }
        return PerformanceSummary(
            frameCount = intervalsMs.size,
            p50Ms = p50,
            p95Ms = nearestRank(intervalsMs, 95),
            p99Ms = nearestRank(intervalsMs, 99),
            medianFps = medianFps,
            maxRssBytes = maxRssBytes,
            maxTemperatureC = maxTemperatureC,
            scenario = scenario
        )
    }
}

class PerformanceCaptureController {
    private data class ActiveCapture(
        val scenario: PerformanceScenario,
        val timestampsNanos: MutableList<Long> = mutableListOf(),
        var maxRssBytes: Long = 0,
        var maxTemperatureC: Double = Double.NEGATIVE_INFINITY
    )

    private var active: ActiveCapture? = null

    @Synchronized
    fun start(scenario: PerformanceScenario) {
        active = ActiveCapture(scenario)
    }

    @Synchronized
    fun record(titleId: String, frameTimestampNanos: Long, rssBytes: Long, temperatureC: Double): Boolean {
        val capture = active ?: return false
        if (capture.scenario.titleId != titleId) return false
        capture.timestampsNanos += frameTimestampNanos
        capture.maxRssBytes = maxOf(capture.maxRssBytes, rssBytes)
        if (temperatureC.isFinite()) {
            capture.maxTemperatureC = maxOf(capture.maxTemperatureC, temperatureC)
        }
        return true
    }

    @Synchronized
    fun finish(titleId: String): PerformanceSummary? {
        val capture = active ?: return null
        if (capture.scenario.titleId != titleId) return null
        active = null
        return PerformancePercentiles.summarize(
            capture.timestampsNanos,
            capture.maxRssBytes,
            capture.maxTemperatureC.takeIf { it.isFinite() } ?: 0.0,
            capture.scenario
        )
    }

    @Synchronized
    fun cancelSession() {
        active = null
    }

    @Synchronized
    fun isCapturing(titleId: String): Boolean = active?.scenario?.titleId == titleId
}

class LatestGenerationPublisher<T> {
    private val lock = Any()
    private var generation = 0L

    fun begin(): Long = synchronized(lock) { ++generation }

    fun invalidate() {
        synchronized(lock) { ++generation }
    }

    fun publish(token: Long, value: T, consumer: (T) -> Unit): Boolean = synchronized(lock) {
        if (generation != token) return@synchronized false
        consumer(value)
        true
    }
}
