// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package com.remipelloux.opensw.lab.protocol

import java.security.MessageDigest

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
    val controllerPort: Int,
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
        if (replay.controllerPort !in 0..9) errors += "controller_port"
        if (replay.durationNs !in 1..MAX_DURATION_NS) errors += "duration"
        if (replay.events.size > MAX_EVENTS) errors += "event_count"
        if (!sha256Pattern.matches(replay.sha256) ||
            !replay.sha256.equals(ReplayHasher.sha256(replay), ignoreCase = true)
        ) {
            errors += "sha256"
        }
        var previousTimestamp = -1L
        replay.events.forEachIndexed { index, event ->
            if (event.timestampNs < previousTimestamp || event.timestampNs > replay.durationNs) {
                errors += "event[$index].timestamp"
            }
            previousTimestamp = event.timestampNs
            if (!event.value.isFinite() || event.value !in -1f..1f) {
                errors += "event[$index].value"
            }
            if (event.kind == ReplayEventKind.BUTTON && event.value != 0f && event.value != 1f) {
                errors += "event[$index].button_value"
            }
            val controls = if (event.kind == ReplayEventKind.BUTTON) allowedButtons else allowedAxes
            if (event.control !in controls) errors += "event[$index].control"
        }
        return errors
    }
}

object ReplayHasher {
    fun sha256(replay: InputReplay): String {
        val canonical = buildString {
            append(replay.schema).append('\n')
            append(replay.titleId.uppercase()).append('\n')
            append(replay.gameVersion).append('\n')
            append(replay.controllerId.lowercase()).append('\n')
            append(replay.controllerPort).append('\n')
            append(replay.durationNs).append('\n')
            replay.events.forEach { event ->
                append(event.timestampNs).append('|')
                append(event.kind.name).append('|')
                append(event.control).append('|')
                append(event.value.toRawBits()).append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

object LabCommandPolicy {
    fun validWorkerSelection(workers: Int): Boolean = workers == 0 || workers in 2..8

    fun validResolutionSetup(resolution: Int): Boolean = resolution in 0..12

    fun validScalingFilter(scalingFilter: Int): Boolean = scalingFilter in 0..14

    fun validSharpening(sharpening: Int): Boolean = sharpening in 0..100

    fun validTitleId(titleId: String): Boolean = titleIdPattern.matches(titleId)

    fun validGameUri(uri: String): Boolean = uri.startsWith("content://") && uri.length <= 4096
}
