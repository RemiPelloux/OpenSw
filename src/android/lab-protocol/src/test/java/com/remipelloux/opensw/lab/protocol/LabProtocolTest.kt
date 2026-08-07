package com.remipelloux.opensw.lab.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabProtocolTest {
    @Test
    fun replayValidationRejectsUnsortedAndNonFiniteEvents() {
        val replay = InputReplay(
            schema = REPLAY_SCHEMA,
            titleId = "01001F5010DFA000",
            gameVersion = "1.1.1",
            controllerId = "0".repeat(32),
            durationNs = 1_000,
            events = listOf(
                ReplayEvent(500, ReplayEventKind.AXIS, 0, 0.5f),
                ReplayEvent(400, ReplayEventKind.AXIS, 0, Float.NaN)
            ),
            sha256 = "a".repeat(64)
        )
        val errors = ReplayValidator.validate(replay)
        assertTrue(errors.contains("event[1].timestamp"))
        assertTrue(errors.contains("event[1].value"))
    }

    @Test
    fun edenAndInvalidWorkersAreDenied() {
        assertFalse(LabCommandPolicy.canLaunch(OFFICIAL_EDEN_PACKAGE))
        assertTrue(LabCommandPolicy.canLaunch("com.remipelloux.opensw.profile"))
        assertTrue(LabCommandPolicy.validWorkerSelection(0))
        assertTrue(LabCommandPolicy.validWorkerSelection(7))
        assertFalse(LabCommandPolicy.validWorkerSelection(1))
        assertFalse(LabCommandPolicy.validWorkerSelection(9))
    }
}
