// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.lab

import com.remipelloux.opensw.lab.protocol.InputReplay
import com.remipelloux.opensw.lab.protocol.ReplayEvent
import com.remipelloux.opensw.lab.protocol.ReplayEventKind
import org.json.JSONObject
import org.yuzu.yuzu_emu.features.performance.OpenSwSessionSnapshot

fun OpenSwSessionSnapshot.toJson(): JSONObject = JSONObject()
    .put("schema", "opensw-session-status-v1")
    .put("generation", generation)
    .put("state", state.name)
    .put("title_id", titleId.toTitleId())
    .put("surface_attached", surfaceAttached)

object InputReplayJson {
    fun parse(payload: String): InputReplay {
        val json = JSONObject(payload)
        val eventsJson = json.getJSONArray("events")
        val events = ArrayList<ReplayEvent>(eventsJson.length())
        for (index in 0 until eventsJson.length()) {
            val event = eventsJson.getJSONObject(index)
            events += ReplayEvent(
                timestampNs = event.getLong("timestamp_ns"),
                kind = ReplayEventKind.valueOf(event.getString("kind")),
                control = event.getInt("control"),
                value = event.getDouble("value").toFloat()
            )
        }
        return InputReplay(
            schema = json.getString("schema"),
            titleId = json.getString("title_id"),
            gameVersion = json.getString("game_version"),
            controllerId = json.getString("controller_id"),
            controllerPort = json.optInt("controller_port", 0),
            durationNs = json.getLong("duration_ns"),
            events = events,
            sha256 = json.getString("sha256")
        )
    }
}

internal fun Long.toTitleId(): String = java.lang.Long.toUnsignedString(this, 16)
    .uppercase()
    .padStart(16, '0')
