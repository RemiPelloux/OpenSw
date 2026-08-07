// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package com.remipelloux.opensw.lab.protocol

const val PROTOCOL_SCHEMA = "opensw-lab-v1"
const val REPLAY_SCHEMA = "opensw-input-replay-v1"
const val RUNTIME_IDENTITY_SCHEMA = "opensw-runtime-identity-v1"
const val OFFICIAL_EDEN_PACKAGE = "dev.eden.eden_emulator.nightly"

private val titleIdPattern = Regex("[0-9a-fA-F]{16}")
private val sha256Pattern = Regex("[0-9a-fA-F]{64}")
private val controllerPattern = Regex("[0-9a-fA-F]{32}")

enum class ReplayEventKind { BUTTON, AXIS }

data class ReplayEvent(
    val timestampNs: Long,
    val kind: ReplayEventKind,
    val control: Int,
    val value: Float
)

data class InputReplay(
    val schema: String,
    val titleId: String,
    val gameVersion: String,
    val controllerId: String,
    val durationNs: Long,
    val events: List<ReplayEvent>,
    val sha256: String
)

object ReplayValidator {
    const val MAX_DURATION_NS = 120_000_000_000L
    const val MAX_EVENTS = 10_000
    private val allowedButtons = 0..288
    private val allowedAxes = 0..47

    fun validate(replay: InputReplay): List<String> {
        val errors = mutableListOf<String>()
        if (replay.schema != REPLAY_SCHEMA) errors += "schema"
        if (!titleIdPattern.matches(replay.titleId)) errors += "title_id"
        if (replay.gameVersion.isBlank()) errors += "game_version"
        if (!controllerPattern.matches(replay.controllerId)) errors += "controller_id"
        if (replay.durationNs !in 1..MAX_DURATION_NS) errors += "duration"
        if (replay.events.size > MAX_EVENTS) errors += "event_count"
        if (!sha256Pattern.matches(replay.sha256)) errors += "sha256"
        var previousTimestamp = -1L
        replay.events.forEachIndexed { index, event ->
            if (event.timestampNs < previousTimestamp || event.timestampNs > replay.durationNs) {
                errors += "event[$index].timestamp"
            }
            previousTimestamp = event.timestampNs
            if (!event.value.isFinite() || event.value !in -1f..1f) {
                errors += "event[$index].value"
            }
            val controls = if (event.kind == ReplayEventKind.BUTTON) allowedButtons else allowedAxes
            if (event.control !in controls) errors += "event[$index].control"
        }
        return errors
    }
}

object LabCommandPolicy {
    fun canLaunch(packageName: String): Boolean = packageName != OFFICIAL_EDEN_PACKAGE

    fun validWorkerSelection(workers: Int): Boolean = workers == 0 || workers in 2..8

    fun validTitleId(titleId: String): Boolean = titleIdPattern.matches(titleId)
}
