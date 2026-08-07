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
    val sorted = samples.filter(Double::isFinite).sorted()
    if (sorted.isEmpty()) return 0.0
    val rank = percentile.coerceIn(0.0, 1.0)
    val index = (ceil(sorted.size * rank).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
}

internal fun finiteMetricOrNull(value: Double): Double? = value.takeIf(Double::isFinite)

internal fun finiteMetricOrNull(value: Float): Float? = value.takeIf(Float::isFinite)

internal data class PerformanceCaptureSummary(
    val sampleCount: Int,
    val maxRssMb: Long,
    val maxTemperatureC: Float,
    val pipeline: PipelineProfileSummary?
)

internal data class PipelineProfileSummary(
    val maxPipelineQueueDepth: Long,
    val maxPresentQueueDepth: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val compilations: Long,
    val smallDrawWaits: Long,
    val pipelineWaits: Long,
    val pipelineWaitNs: Long,
    val translationNs: Long,
    val spirvNs: Long,
    val shaderModuleNs: Long,
    val vulkanPipelineNs: Long,
    val freeFrameWaitNs: Long,
    val schedulerWaitNs: Long,
    val swapchainAcquireNs: Long,
    val presentNs: Long,
    val captureSmallDrawWaitNs: Long
)

internal fun summarizeCapture(
    samples: Collection<PerformanceSnapshot>,
    titleId: String
): PerformanceCaptureSummary {
    return PerformanceCaptureSummary(
        sampleCount = samples.size,
        maxRssMb = samples.maxOfOrNull(PerformanceSnapshot::appRssMb) ?: 0L,
        maxTemperatureC = samples.map(PerformanceSnapshot::batteryTemperatureC)
            .filter(Float::isFinite)
            .maxOrNull() ?: 0f,
        pipeline = summarizePipelineProfiles(
            samples.mapNotNull(PerformanceSnapshot::pipelineProfile),
            titleId
        )
    )
}

internal fun summarizePipelineProfiles(
    profiles: Collection<PipelineProfileSnapshot>,
    titleId: String
): PipelineProfileSummary? {
    val matching = profiles.filter { it.matches(titleId) }
    val first = matching.firstOrNull() ?: return null
    val last = matching.last()
    fun delta(start: Long, end: Long): Long? = if (end >= start) end - start else null
    return PipelineProfileSummary(
        maxPipelineQueueDepth = last.captureMaxQueueDepth,
        maxPresentQueueDepth = last.captureMaxPresentQueueDepth,
        cacheHits = delta(first.cacheHits, last.cacheHits) ?: return null,
        cacheMisses = delta(first.cacheMisses, last.cacheMisses) ?: return null,
        compilations = delta(first.compilations, last.compilations) ?: return null,
        smallDrawWaits = last.captureSmallDrawWaits,
        pipelineWaits = delta(first.pipelineWaits, last.pipelineWaits) ?: return null,
        pipelineWaitNs = delta(first.pipelineWaitNs, last.pipelineWaitNs) ?: return null,
        translationNs = delta(first.translationNs, last.translationNs) ?: return null,
        spirvNs = delta(first.spirvNs, last.spirvNs) ?: return null,
        shaderModuleNs = delta(first.shaderModuleNs, last.shaderModuleNs) ?: return null,
        vulkanPipelineNs = delta(first.vulkanPipelineNs, last.vulkanPipelineNs) ?: return null,
        freeFrameWaitNs = delta(first.freeFrameWaitNs, last.freeFrameWaitNs) ?: return null,
        schedulerWaitNs = delta(first.schedulerWaitNs, last.schedulerWaitNs) ?: return null,
        swapchainAcquireNs = delta(
            first.swapchainAcquireNs,
            last.swapchainAcquireNs
        ) ?: return null,
        presentNs = delta(first.presentNs, last.presentNs) ?: return null,
        captureSmallDrawWaitNs = last.captureSmallDrawWaitNs
    )
}

internal enum class CaptureState {
    ACTIVE,
    INVALIDATED,
    FINISHED
}

internal data class CaptureConfiguration(
    val titleId: String,
    val processId: Int,
    val packageName: String,
    val apkVersion: String,
    val mode: String,
    val pipelineWorkersRequested: Int,
    val pipelineWorkersEffective: Int,
    val pipelineWorkersReason: String,
    val presentationRate: Int,
    val asyncPresentation: Boolean
)

internal data class PerformanceCaptureSession(
    val captureId: String,
    val generation: Long,
    val startedAtMs: Long,
    val startedAtMonotonicMs: Long,
    val configuration: CaptureConfiguration,
    val samples: MutableList<PerformanceSnapshot> = mutableListOf(),
    var state: CaptureState = CaptureState.ACTIVE,
    var invalidationReason: String? = null,
    var finishedAtMs: Long? = null,
    var finishedAtMonotonicMs: Long? = null
) {
    fun record(
        activeGeneration: Long,
        currentConfiguration: CaptureConfiguration,
        snapshot: PerformanceSnapshot
    ): Boolean {
        if (state != CaptureState.ACTIVE || generation != activeGeneration) return false
        if (currentConfiguration != configuration) {
            invalidate("configuration_changed")
            return false
        }
        val profile = snapshot.pipelineProfile
        if (profile != null && !profile.matches(configuration.titleId)) {
            invalidate("title_id_changed")
            return false
        }
        samples += snapshot
        return true
    }

    fun invalidate(reason: String) {
        if (state != CaptureState.ACTIVE) return
        state = CaptureState.INVALIDATED
        invalidationReason = reason
    }

    fun finish(activeGeneration: Long, wallTimeMs: Long, monotonicTimeMs: Long): Boolean {
        if (generation != activeGeneration || state == CaptureState.FINISHED) return false
        if (state == CaptureState.ACTIVE) state = CaptureState.FINISHED
        finishedAtMs = wallTimeMs
        finishedAtMonotonicMs = monotonicTimeMs
        return true
    }
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
