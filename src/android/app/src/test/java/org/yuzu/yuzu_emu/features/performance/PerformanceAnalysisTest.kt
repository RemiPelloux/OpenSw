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
        assertEquals(95.0, percentile95((1..100).map(Int::toDouble)), 0.0)
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
