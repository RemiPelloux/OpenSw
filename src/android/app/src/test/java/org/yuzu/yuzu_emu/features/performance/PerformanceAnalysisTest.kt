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
        assertEquals(20.0, percentile95(listOf(10.0, 20.0)), 0.0)
        assertEquals(95.0, percentile95((1..100).map(Int::toDouble)), 0.0)
    }

    @Test
    fun healthUsesTheObservedFrameBudget() {
        assertEquals(
            PerformanceHealth.STABLE,
            performanceHealth(PerformanceSnapshot(fps = 30.0, frameTimeP95Ms = 37.0, emulationSpeed = 1.0))
        )
        assertEquals(
            PerformanceHealth.STABLE,
            performanceHealth(PerformanceSnapshot(fps = 60.0, frameTimeP95Ms = 19.0, emulationSpeed = 1.0))
        )
        assertEquals(
            PerformanceHealth.UNEVEN,
            performanceHealth(PerformanceSnapshot(fps = 60.0, frameTimeP95Ms = 24.0, emulationSpeed = 1.0))
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
    }

    @Test
    fun captureSummaryUsesOnlyFinitePresentedFrames() {
        val summary = summarizeCapture(
            listOf(
                PerformanceSnapshot(fps = 60.0, frameTimeMs = 10.0, appRssMb = 100, batteryTemperatureC = 40f),
                PerformanceSnapshot(fps = 50.0, frameTimeMs = 20.0, appRssMb = 150, batteryTemperatureC = 42f),
                PerformanceSnapshot(fps = 40.0, frameTimeMs = 30.0, appRssMb = 120, batteryTemperatureC = 41f),
                PerformanceSnapshot(fps = 0.0, frameTimeMs = Double.NaN)
            )
        )

        assertEquals(20.0, summary.frameTimeP50Ms, 0.0)
        assertEquals(30.0, summary.frameTimeP95Ms, 0.0)
        assertEquals(30.0, summary.frameTimeP99Ms, 0.0)
        assertEquals(50.0, summary.medianFps, 0.0)
        assertEquals(3, summary.sampleCount)
        assertEquals(150L, summary.maxRssMb)
        assertEquals(42f, summary.maxTemperatureC)
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
