// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package com.remipelloux.opensw.lab

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.remipelloux.opensw.lab.protocol.IOpenSwProfileBridge

class MainActivity : AppCompatActivity() {
    private var bridge: IOpenSwProfileBridge? = null
    private lateinit var status: TextView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bridge = IOpenSwProfileBridge.Stub.asInterface(service)
            refreshStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bridge = null
            status.text = getString(R.string.profile_disconnected)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            setPadding(24, 24, 24, 24)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                status,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            listOf(0, 2, 4, 6, 8).forEach { workers ->
                addView(Button(context).apply {
                    text = if (workers == 0) getString(R.string.workers_auto) else "$workers workers"
                    setOnClickListener {
                        bridge?.setPipelineWorkers(workers)
                        refreshStatus()
                    }
                })
            }
            addView(Button(context).apply {
                setText(R.string.stop_opensw)
                setOnClickListener {
                    bridge?.stopEmulation()
                    refreshStatus()
                }
            })
        }
        setContentView(layout)
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent().setComponent(
            ComponentName(
                "com.remipelloux.opensw.profile",
                "org.yuzu.yuzu_emu.lab.OpenSwProfileBridgeService"
            )
        )
        if (!bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            status.text = getString(R.string.profile_disconnected)
        }
    }

    override fun onStop() {
        bridge?.let {
            unbindService(connection)
            bridge = null
        }
        super.onStop()
    }

    private fun refreshStatus() {
        status.text = runCatching { bridge?.runtimeIdentity }
            .getOrNull()
            .orEmpty()
            .ifBlank { getString(R.string.profile_disconnected) }
    }
}
