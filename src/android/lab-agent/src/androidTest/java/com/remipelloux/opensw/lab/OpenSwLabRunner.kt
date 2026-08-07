// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package com.remipelloux.opensw.lab

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Base64
import androidx.test.runner.AndroidJUnitRunner
import com.remipelloux.opensw.lab.protocol.IOpenSwProfileBridge
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import org.json.JSONObject

class OpenSwLabRunner : AndroidJUnitRunner() {
    private lateinit var runnerArguments: Bundle

    override fun onCreate(arguments: Bundle) {
        runnerArguments = Bundle(arguments)
        super.onCreate(arguments)
    }

    override fun onStart() {
        val command = runnerArguments.getString("command")
        if (command.isNullOrBlank()) {
            super.onStart()
            return
        }
        Thread({ executeCommand(command) }, "OpenSwLabCommand").start()
    }

    private fun executeCommand(command: String) {
        val result = runCatching {
            BridgeClient(targetContext).use { client ->
                val bridge = client.connect()
                val timeout = runnerArguments.getString("timeout_ms")?.toLongOrNull() ?: 30_000L
                when (command) {
                    "runtime-identity" -> JSONObject()
                        .put("ok", true)
                        .put("value", JSONObject(bridge.runtimeIdentity))
                    "session-status" -> JSONObject()
                        .put("ok", true)
                        .put("value", JSONObject(bridge.sessionStatus))
                    "launch" -> booleanResult(
                        bridge.launchGame(
                            requireArgument("game_uri"),
                            requireArgument("title_id"),
                            timeout
                        )
                    )
                    "pause" -> booleanResult(bridge.pauseEmulation(timeout))
                    "resume" -> booleanResult(bridge.resumeEmulation(timeout))
                    "stop" -> booleanResult(bridge.stopEmulation(timeout))
                    "set-workers" -> booleanResult(
                        bridge.setPipelineWorkers(requireArgument("workers").toInt())
                    )
                    "clear-cache" -> booleanResult(
                        bridge.clearShaderCache(requireArgument("title_id"))
                    )
                    "start-replay" -> {
                        val replay = runnerArguments.getString("replay_file")?.let { name ->
                            require(!name.contains('/') && !name.contains('\\')) {
                                "Replay filename must not contain a path"
                            }
                            val root = checkNotNull(targetContext.getExternalFilesDir(null))
                            val file = File(root, name)
                            require(file.isFile && file.length() <= MAX_REPLAY_BYTES) {
                                "Replay file is missing or too large"
                            }
                            file.readText(Charsets.UTF_8)
                        } ?: String(
                            Base64.decode(requireArgument("replay_base64"), Base64.DEFAULT),
                            Charsets.UTF_8
                        )
                        booleanResult(bridge.startReplay(replay))
                    }
                    "cancel-replay" -> booleanResult(bridge.cancelReplay())
                    else -> error("Unknown lab command: $command")
                }
            }
        }.getOrElse { error ->
            JSONObject().put("ok", false).put("error", error.message ?: error.javaClass.simpleName)
        }
        val encoded = Base64.encodeToString(result.toString().toByteArray(), Base64.NO_WRAP)
        val bundle = Bundle().apply { putString("stream", "$RESULT_MARKER$encoded\n") }
        finish(if (result.optBoolean("ok")) Activity.RESULT_OK else Activity.RESULT_CANCELED, bundle)
    }

    private fun requireArgument(name: String): String =
        runnerArguments.getString(name)?.takeIf { it.isNotBlank() }
            ?: error("Missing instrumentation argument: $name")

    private fun booleanResult(success: Boolean): JSONObject = JSONObject().put("ok", success)

    companion object {
        const val RESULT_MARKER = "OPEN_SW_LAB_RESULT="
        const val MAX_REPLAY_BYTES = 2L * 1024L * 1024L
    }
}

internal class BridgeClient(private val context: Context) : AutoCloseable {
    private val connected = CountDownLatch(1)
    private var bound = false
    private var bridge: IOpenSwProfileBridge? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bridge = IOpenSwProfileBridge.Stub.asInterface(service)
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bridge = null
        }
    }

    fun connect(): IOpenSwProfileBridge {
        val intent = Intent().setComponent(
            ComponentName(
                "com.remipelloux.opensw.profile",
                "org.yuzu.yuzu_emu.lab.OpenSwProfileBridgeService"
            )
        )
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        check(bound) { "Profile bridge is unavailable" }
        check(connected.await(10, TimeUnit.SECONDS)) { "Timed out binding Profile bridge" }
        return checkNotNull(bridge) { "Profile bridge disconnected" }
    }

    override fun close() {
        if (bound) context.unbindService(connection)
        bound = false
        bridge = null
    }
}
