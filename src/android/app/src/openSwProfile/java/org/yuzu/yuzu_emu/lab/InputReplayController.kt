// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.lab

import com.remipelloux.opensw.lab.protocol.InputReplay
import com.remipelloux.opensw.lab.protocol.ReplayEventKind
import com.remipelloux.opensw.lab.protocol.ReplayValidator
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.features.input.NativeInput
import org.yuzu.yuzu_emu.features.performance.OpenSwSessionSnapshot
import org.yuzu.yuzu_emu.features.performance.OpenSwSessionState

enum class ReplayState { IDLE, RUNNING, COMPLETED, CANCELLED, FAILED }

data class ReplaySnapshot(val sha256: String, val state: ReplayState)

class InputReplayController {
    private val lock = Any()
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "OpenSwInputReplay").apply { isDaemon = true }
    }
    private val scheduled = mutableListOf<ScheduledFuture<*>>()
    private var replay: InputReplay? = null
    private var sessionGeneration = 0L
    private var state = ReplayState.IDLE
    private var token = 0L

    fun start(candidate: InputReplay): Boolean = synchronized(lock) {
        if (state == ReplayState.RUNNING || ReplayValidator.validate(candidate).isNotEmpty()) {
            return false
        }
        val session = currentSession() ?: return false
        if (session.state != OpenSwSessionState.RUNNING ||
            !candidate.titleId.equals(session.titleId.toTitleId(), ignoreCase = true)
        ) {
            return false
        }

        cancelScheduledLocked(resetInput = true)
        replay = candidate
        sessionGeneration = session.generation
        state = ReplayState.RUNNING
        val currentToken = ++token
        candidate.events.forEach { event ->
            scheduled += executor.schedule(
                { applyEvent(currentToken, candidate, event.kind, event.control, event.value) },
                event.timestampNs,
                TimeUnit.NANOSECONDS
            )
        }
        scheduled += executor.schedule(
            { complete(currentToken, candidate) },
            candidate.durationNs,
            TimeUnit.NANOSECONDS
        )
        true
    }

    fun cancel(): Boolean = synchronized(lock) {
        val wasActive = state == ReplayState.RUNNING
        cancelScheduledLocked(resetInput = true)
        state = if (wasActive) ReplayState.CANCELLED else ReplayState.IDLE
        wasActive
    }

    fun snapshot(): ReplaySnapshot = synchronized(lock) {
        if (state == ReplayState.RUNNING && !sessionMatchesLocked()) {
            cancelScheduledLocked(resetInput = true)
            state = ReplayState.FAILED
        }
        ReplaySnapshot(replay?.sha256.orEmpty(), state)
    }

    fun shutdown() {
        synchronized(lock) { cancelScheduledLocked(resetInput = true) }
        executor.shutdownNow()
    }

    private fun applyEvent(
        expectedToken: Long,
        expectedReplay: InputReplay,
        kind: ReplayEventKind,
        control: Int,
        value: Float
    ) = synchronized(lock) {
        if (token != expectedToken || replay !== expectedReplay || state != ReplayState.RUNNING ||
            !sessionMatchesLocked()
        ) {
            if (state == ReplayState.RUNNING) state = ReplayState.FAILED
            return
        }
        if (kind == ReplayEventKind.BUTTON) {
            val buttonState = if (value == 1f) {
                NativeInput.ButtonState.PRESSED
            } else {
                NativeInput.ButtonState.RELEASED
            }
            NativeInput.onGamePadButtonEvent(
                expectedReplay.controllerId,
                expectedReplay.controllerPort,
                control,
                buttonState
            )
        } else {
            NativeInput.onGamePadAxisEvent(
                expectedReplay.controllerId,
                expectedReplay.controllerPort,
                control,
                value
            )
        }
    }

    private fun complete(expectedToken: Long, expectedReplay: InputReplay) = synchronized(lock) {
        if (token != expectedToken ||
            replay !== expectedReplay ||
            state != ReplayState.RUNNING
        ) {
            return
        }
        if (!sessionMatchesLocked()) {
            state = ReplayState.FAILED
            resetInputLocked(expectedReplay)
            return
        }
        resetInputLocked(expectedReplay)
        state = ReplayState.COMPLETED
        scheduled.clear()
    }

    private fun sessionMatchesLocked(): Boolean {
        val session = currentSession() ?: return false
        return session.generation == sessionGeneration &&
            session.state == OpenSwSessionState.RUNNING &&
            replay?.titleId.equals(session.titleId.toTitleId(), ignoreCase = true)
    }

    private fun currentSession(): OpenSwSessionSnapshot? =
        OpenSwSessionSnapshot.from(NativeLibrary.getSessionSnapshot())

    private fun cancelScheduledLocked(resetInput: Boolean) {
        scheduled.forEach { it.cancel(false) }
        scheduled.clear()
        if (resetInput) replay?.let(::resetInputLocked)
        ++token
    }

    private fun resetInputLocked(inputReplay: InputReplay) {
        inputReplay.events.asSequence()
            .filter { it.kind == ReplayEventKind.BUTTON }
            .map { it.control }
            .distinct()
            .forEach { control ->
                NativeInput.onGamePadButtonEvent(
                    inputReplay.controllerId,
                    inputReplay.controllerPort,
                    control,
                    NativeInput.ButtonState.RELEASED
                )
            }
        inputReplay.events.asSequence()
            .filter { it.kind == ReplayEventKind.AXIS }
            .map { it.control }
            .distinct()
            .forEach { control ->
                NativeInput.onGamePadAxisEvent(
                    inputReplay.controllerId,
                    inputReplay.controllerPort,
                    control,
                    0f
                )
            }
    }
}
