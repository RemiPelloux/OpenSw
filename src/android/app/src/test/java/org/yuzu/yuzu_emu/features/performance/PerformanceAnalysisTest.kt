// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAnalysisTest {
    @Test
    fun sustainedConditionRequiresFullWindowAndResets() {
        val condition = SustainedCondition(10_000)

        assertFalse(condition.update(1_000, active = true))
        assertFalse(condition.update(10_999, active = true))
        assertTrue(condition.update(11_000, active = true))
        assertFalse(condition.update(12_000, active = false))
        assertFalse(condition.update(20_000, active = true))
    }

    @Test
    fun percentileUsesSortedNearestRankWindow() {
        assertEquals(0.0, percentile95(emptyList()), 0.0)
        assertEquals(0.0, percentile95(listOf(Double.NaN, Double.POSITIVE_INFINITY)), 0.0)
        assertEquals(20.0, percentile95(listOf(10.0, 20.0)), 0.0)
        assertEquals(
            20.0,
            percentile95(listOf(Double.NaN, 10.0, Double.NEGATIVE_INFINITY, 20.0)),
            0.0
        )
        assertEquals(95.0, percentile95((1..100).map(Int::toDouble)), 0.0)
    }

    @Test
    fun diagnosticMetricsConvertNonFiniteValuesToNull() {
        assertEquals(null, finiteMetricOrNull(Double.NaN))
        assertEquals(null, finiteMetricOrNull(Double.POSITIVE_INFINITY))
        assertEquals(null, finiteMetricOrNull(Float.NEGATIVE_INFINITY))
        assertEquals(16.7, finiteMetricOrNull(16.7)!!, 0.0)
        assertEquals(42f, finiteMetricOrNull(42f)!!, 0f)
    }

    @Test
    fun healthUsesTheObservedFrameBudget() {
        assertEquals(
            PerformanceHealth.STABLE,
            performanceHealth(
                PerformanceSnapshot(
                    fps = 30.0,
                    frameTimeP95Ms = 37.0,
                    emulationSpeed = 1.0
                )
            )
        )
        assertEquals(
            PerformanceHealth.STABLE,
            performanceHealth(
                PerformanceSnapshot(
                    fps = 60.0,
                    frameTimeP95Ms = 19.0,
                    emulationSpeed = 1.0
                )
            )
        )
        assertEquals(
            PerformanceHealth.UNEVEN,
            performanceHealth(
                PerformanceSnapshot(
                    fps = 60.0,
                    frameTimeP95Ms = 24.0,
                    emulationSpeed = 1.0
                )
            )
        )
    }

    @Test
    fun healthPrioritizesActionableWarnings() {
        assertEquals(PerformanceHealth.WAITING, performanceHealth(PerformanceSnapshot()))
        assertEquals(
            PerformanceHealth.SLOW,
            performanceHealth(PerformanceSnapshot(performanceWarning = true))
        )
        assertEquals(
            PerformanceHealth.THERMAL,
            performanceHealth(
                PerformanceSnapshot(performanceWarning = true, thermalWarning = true)
            )
        )
    }

    @Test
    fun profileSnapshotRequiresCompleteActiveCounters() {
        assertEquals(null, PipelineProfileSnapshot.from(longArrayOf()))
        assertEquals(null, PipelineProfileSnapshot.from(LongArray(12)))

        val values = LongArray(12) { (it + 1).toLong() }
        val snapshot = PipelineProfileSnapshot.from(values)!!
        assertEquals(1L, snapshot.titleId)
        assertEquals(2L, snapshot.cacheHits)
        assertEquals(7L, snapshot.pipelineWaits)
        assertEquals(12L, snapshot.vulkanPipelineNs)
        assertTrue(snapshot.matches("0000000000000001"))
        assertFalse(snapshot.matches("0000000000000002"))

        val extended = PipelineProfileSnapshot.from(LongArray(17) { (it + 1).toLong() })!!
        assertEquals(13L, extended.maxPresentQueueDepth)
        assertEquals(14L, extended.freeFrameWaitNs)
        assertEquals(17L, extended.presentNs)

        val instrumentation = PipelineProfileSnapshot.from(LongArray(46) { (it + 1).toLong() })!!
        assertEquals(22L, instrumentation.pipelineSelfHits)
        assertEquals(24L, instrumentation.pipelineTransitionProbes)
        assertEquals(27L, instrumentation.descriptorBufferDraws)
        assertEquals(33L, instrumentation.descriptorRingStalls)
        assertEquals(35L, instrumentation.vertexBufferBindCalls)
        assertEquals(39L, instrumentation.textureUploadBytes)
        assertEquals(46L, instrumentation.descriptorOffsetSkips)

        val renderPassInstrumentation =
            PipelineProfileSnapshot.from(LongArray(63) { (it + 1).toLong() })!!
        assertEquals(47L, renderPassInstrumentation.renderPassBegins)
        assertEquals(
            48L,
            renderPassInstrumentation.renderPassEndsAndroidDrawHardFlush
        )
        assertEquals(54L, renderPassInstrumentation.commandBufferSubmissions)
        assertEquals(56L, renderPassInstrumentation.reorderedBufferUploadBytes)
        assertEquals(59L, renderPassInstrumentation.postCopyBarrierCalls)
        assertEquals(63L, renderPassInstrumentation.androidDrawHardFlushes)
    }

    @Test
    fun diagnosticSummaryDoesNotExposeSampledFramePercentiles() {
        val summary = summarizeCapture(
            listOf(
                PerformanceSnapshot(
                    fps = 60.0,
                    frameTimeMs = 10.0,
                    appRssMb = 100,
                    batteryTemperatureC = 40f
                ),
                PerformanceSnapshot(
                    fps = 50.0,
                    frameTimeMs = 20.0,
                    appRssMb = 150,
                    batteryTemperatureC = 42f
                ),
                PerformanceSnapshot(
                    fps = 40.0,
                    frameTimeMs = 30.0,
                    appRssMb = 120,
                    batteryTemperatureC = 41f
                ),
                PerformanceSnapshot(fps = 0.0, frameTimeMs = Double.NaN)
            ),
            "0000000000000001"
        )

        assertEquals(4, summary.sampleCount)
        assertEquals(150L, summary.maxRssMb)
        assertEquals(42f, summary.maxTemperatureC)
        assertEquals(null, summary.pipeline)
    }

    @Test
    fun profileSummaryUsesDeltasAndCaptureWindowCounters() {
        fun profile(base: Long, pipelineQueue: Long, presentQueue: Long) = PipelineProfileSnapshot(
            titleId = 1L,
            cacheHits = base,
            cacheMisses = base + 1,
            compilations = base + 2,
            maxQueueDepth = pipelineQueue,
            smallDrawWaits = base + 3,
            pipelineWaits = base + 4,
            pipelineWaitNs = base + 5,
            translationNs = base + 6,
            spirvNs = base + 7,
            shaderModuleNs = base + 8,
            vulkanPipelineNs = base + 9,
            maxPresentQueueDepth = presentQueue,
            freeFrameWaitNs = base + 10,
            schedulerWaitNs = base + 11,
            swapchainAcquireNs = base + 12,
            presentNs = base + 13,
            captureMaxQueueDepth = pipelineQueue,
            captureMaxPresentQueueDepth = presentQueue,
            captureSmallDrawWaits = base + 3,
            captureSmallDrawWaitNs = base + 14,
            renderPassBegins = base + 15,
            renderPassEndsAndroidDrawHardFlush = base + 16,
            renderPassEndsUploadSynchronization = base + 17,
            renderPassEndsFeedbackLoop = base + 18,
            renderPassEndsExplicitOutside = base + 19,
            renderPassEndsSubmission = base + 20,
            renderPassEndsSwitch = base + 21,
            commandBufferSubmissions = base + 22,
            reorderedBufferUploads = base + 23,
            reorderedBufferUploadBytes = base + 24,
            inlineBufferUploads = base + 25,
            inlineBufferUploadBytes = base + 26,
            postCopyBarrierCalls = base + 27,
            feedbackLoopBarrierCalls = base + 28,
            androidDrawFlushDeferred = base + 29,
            androidDrawSoftFlushes = base + 30,
            androidDrawHardFlushes = base + 31
        )

        val summary = summarizePipelineProfiles(
            listOf(profile(10, 2, 4), profile(25, 7, 5)),
            "0000000000000001"
        )!!
        assertEquals(7L, summary.maxPipelineQueueDepth)
        assertEquals(5L, summary.maxPresentQueueDepth)
        assertEquals(15L, summary.cacheHits)
        assertEquals(15L, summary.presentNs)
        assertEquals(15L, summary.renderPassBegins)
        assertEquals(15L, summary.renderPassEndsUploadSynchronization)
        assertEquals(15L, summary.commandBufferSubmissions)
        assertEquals(15L, summary.reorderedBufferUploadBytes)
        assertEquals(15L, summary.androidDrawFlushDeferred)
        assertEquals(15L, summary.androidDrawHardFlushes)

        assertEquals(
            null,
            summarizePipelineProfiles(
                listOf(profile(25, 2, 2), profile(10, 3, 3)),
                "0000000000000001"
            )
        )
    }

    @Test
    fun captureSessionRejectsOldGenerationAndInvalidatesConfigurationChange() {
        val configuration = CaptureConfiguration(
            titleId = "0000000000000001",
            processId = 42,
            packageName = "com.remipelloux.opensw.profile",
            apkVersion = "test",
            mode = "STANDARD",
            pipelineWorkersRequested = 4,
            pipelineWorkersEffective = 4,
            pipelineWorkersReason = "EXPLICIT",
            presentationRate = 60,
            asyncPresentation = true
        )
        val session = PerformanceCaptureSession(
            captureId = "capture-1",
            generation = 7,
            startedAtMs = 100,
            startedAtMonotonicMs = 50,
            configuration = configuration
        )

        assertFalse(session.record(6, configuration, PerformanceSnapshot()))
        assertEquals(CaptureState.ACTIVE, session.state)
        assertFalse(
            session.record(
                7,
                configuration.copy(pipelineWorkersRequested = 6, pipelineWorkersEffective = 6),
                PerformanceSnapshot()
            )
        )
        assertEquals(CaptureState.INVALIDATED, session.state)
        assertEquals("configuration_changed", session.invalidationReason)
        assertTrue(session.finish(7, 200, 150))
        assertEquals(CaptureState.INVALIDATED, session.state)
        assertFalse(session.finish(8, 300, 250))
    }

    @Test
    fun metricRequirementsMergeWithoutEnablingHiddenSensors() {
        val hud = PerformanceMetricRequirements(frameStats = true, shaders = true)
        val memory = PerformanceMetricRequirements(appRss = true)
        val combined = hud + memory

        assertTrue(combined.frameStats)
        assertTrue(combined.shaders)
        assertTrue(combined.appRss)
        assertFalse(combined.systemRam)
        assertFalse(combined.battery)
        assertFalse(combined.thermal)
        assertTrue(combined.needsFastSample)
        assertTrue(combined.needsSlowSample)
    }
}
