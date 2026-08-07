package com.remipelloux.opensw.lab.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabProtocolTest {
    @Test
    fun replayValidationRejectsUnsortedAndNonFiniteEvents() {
        val unsignedReplay = InputReplay(
            schema = REPLAY_SCHEMA,
            titleId = "01001F5010DFA000",
            gameVersion = "1.1.1",
            controllerId = "0".repeat(32),
            controllerPort = 0,
            durationNs = 1_000,
            events = listOf(
                ReplayEvent(500, ReplayEventKind.AXIS, 0, 0.5f),
                ReplayEvent(400, ReplayEventKind.AXIS, 0, Float.NaN)
            ),
            sha256 = ""
        )
        val replay = unsignedReplay.copy(sha256 = ReplayHasher.sha256(unsignedReplay))
        val errors = ReplayValidator.validate(replay)
        assertTrue(errors.contains("event[1].timestamp"))
        assertTrue(errors.contains("event[1].value"))
    }

    @Test
    fun invalidWorkersAndUrisAreDenied() {
        assertTrue(LabCommandPolicy.validWorkerSelection(0))
        assertTrue(LabCommandPolicy.validWorkerSelection(7))
        assertFalse(LabCommandPolicy.validWorkerSelection(1))
        assertFalse(LabCommandPolicy.validWorkerSelection(9))
        assertTrue(LabCommandPolicy.validGameUri("content://games/arceus"))
        assertFalse(LabCommandPolicy.validGameUri("file:///sdcard/game.xci"))
    }

    @Test
    fun graphicsConfigurationAcceptsOnlyKnownRanges() {
        assertTrue(LabCommandPolicy.validResolutionSetup(0))
        assertTrue(LabCommandPolicy.validResolutionSetup(12))
        assertFalse(LabCommandPolicy.validResolutionSetup(-1))
        assertFalse(LabCommandPolicy.validResolutionSetup(13))
        assertTrue(LabCommandPolicy.validScalingFilter(0))
        assertTrue(LabCommandPolicy.validScalingFilter(14))
        assertFalse(LabCommandPolicy.validScalingFilter(15))
        assertTrue(LabCommandPolicy.validSharpening(0))
        assertTrue(LabCommandPolicy.validSharpening(100))
        assertFalse(LabCommandPolicy.validSharpening(101))
    }


    @Test
    fun replayValidationAcceptsCanonicalHash() {
        val unsignedReplay = InputReplay(
            schema = REPLAY_SCHEMA,
            titleId = "01001F5010DFA000",
            gameVersion = "1.1.1",
            controllerId = "00112233445566778899aabbccddeeff",
            controllerPort = 0,
            durationNs = 1_000,
            events = listOf(ReplayEvent(0, ReplayEventKind.BUTTON, 96, 1f)),
            sha256 = ""
        )
        val replay = unsignedReplay.copy(sha256 = ReplayHasher.sha256(unsignedReplay))

        assertTrue(ReplayValidator.validate(replay).isEmpty())
    }

    @Test
    fun replayHashMatchesHostCanonicalFormat() {
        val replay = InputReplay(
            schema = REPLAY_SCHEMA,
            titleId = "01001f5010dfa000",
            gameVersion = "1.1.1",
            controllerId = "A".repeat(32),
            controllerPort = 0,
            durationNs = 1_000,
            events = listOf(ReplayEvent(0, ReplayEventKind.BUTTON, 96, 1f)),
            sha256 = ""
        )

        assertTrue(
            ReplayHasher.sha256(replay) ==
                "9a09a980fa6153263eb841ff5f5acfcf36eefc12525ceebed23e43b2b1675f6f"
        )
    }
}
