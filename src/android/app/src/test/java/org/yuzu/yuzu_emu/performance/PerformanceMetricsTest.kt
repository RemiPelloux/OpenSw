// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceMetricsTest {
    private val scenario = PerformanceScenario(
        "arceus-route-a",
        "01001F5010DFA000",
        "1.1.1",
        "slot-1",
        "jubilife-fieldlands",
        "fixed-north",
        "1920x1080",
        "handheld",
        "firmware",
        "driver"
    )

    @Test
    fun nearestRankUsesObservedValue() {
        assertEquals(
            5.0,
            PerformancePercentiles.nearestRank(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 95),
            0.0
        )
        assertEquals(0.0, PerformancePercentiles.nearestRank(emptyList(), 99), 0.0)
    }

    @Test
    fun summaryUsesOnlyFramesInsideTimestampWindow() {
        val summary = PerformancePercentiles.summarize(
            listOf(0L, 10_000_000L, 30_000_000L, 60_000_000L),
            42L,
            51.5,
            scenario
        )
        assertEquals(3, summary.frameCount)
        assertEquals(20.0, summary.p50Ms, 0.0)
        assertEquals(30.0, summary.p95Ms, 0.0)
        assertEquals(50.0, summary.medianFps, 0.0)
    }

    @Test
    fun medianFpsAveragesTheTwoMiddleValues() {
        val summary = PerformancePercentiles.summarize(
            listOf(0L, 10_000_000L, 30_000_000L),
            0L,
            0.0,
            scenario
        )
        assertEquals(75.0, summary.medianFps, 0.0)
    }

    @Test
    fun captureRejectsMixedTitlesAndCancelsWithSession() {
        val capture = PerformanceCaptureController()
        capture.start(scenario)
        assertFalse(capture.record("wrong", 1L, 1L, 1.0))
        assertTrue(capture.record(scenario.titleId, 1L, 1L, 1.0))
        capture.cancelSession()
        assertNull(capture.finish(scenario.titleId))
    }

    @Test
    fun oldSamplerCannotPublish() {
        val publisher = LatestGenerationPublisher<Int>()
        val old = publisher.begin()
        val current = publisher.begin()
        var published = 0
        assertFalse(publisher.publish(old, 1) { published = it })
        assertTrue(publisher.publish(current, 2) { published = it })
        assertEquals(2, published)
    }
}
