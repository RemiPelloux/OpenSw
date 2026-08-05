// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import android.content.Intent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.utils.OpenSwPerformanceModeManager
import org.yuzu.yuzu_emu.utils.PerformanceMode

class PerformancePanelController(
    private val fragment: Fragment,
    private val panel: View,
    private val gameTitle: String,
    private val titleId: String
) {
    private val title = panel.findViewById<TextView>(R.id.performance_game_title)
    private val context = panel.findViewById<TextView>(R.id.performance_context)
    private val warning = panel.findViewById<TextView>(R.id.performance_warning)
    private val fps = panel.findViewById<TextView>(R.id.performance_fps)
    private val frameTime = panel.findViewById<TextView>(R.id.performance_frametime)
    private val speed = panel.findViewById<TextView>(R.id.performance_speed)
    private val memory = panel.findViewById<TextView>(R.id.performance_memory)
    private val power = panel.findViewById<TextView>(R.id.performance_power)
    private val capture = panel.findViewById<MaterialButton>(R.id.performance_capture)
    private val share = panel.findViewById<MaterialButton>(R.id.performance_share)
    private val samplerConsumer = Any()
    private var isVisible = false

    init {
        title.text = gameTitle
        capture.setOnClickListener { toggleCapture() }
        share.setOnClickListener { shareLatestReport() }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PerformanceSampler.snapshots.collect(::render)
            }
        }
    }

    fun show() {
        if (!isVisible) {
            PerformanceSampler.acquire(fragment.requireContext(), samplerConsumer)
            isVisible = true
        }
        panel.visibility = View.VISIBLE
        val mode = OpenSwPerformanceModeManager.getResolvedMode(fragment.requireContext(), titleId)
        context.text = fragment.getString(
            R.string.performance_context_format,
            titleId,
            fragment.getString(mode.labelResource())
        )
        capture.setText(
            if (PerformanceSampler.isCapturing()) {
                R.string.performance_capture_stop
            } else {
                R.string.performance_capture_start
            }
        )
        render(PerformanceSampler.snapshots.value)
    }

    fun hide() {
        panel.visibility = View.GONE
        if (isVisible) {
            PerformanceSampler.release(samplerConsumer)
            isVisible = false
        }
    }

    private fun render(snapshot: PerformanceSnapshot) {
        fps.text = fragment.getString(R.string.performance_fps_format, snapshot.fps)
        frameTime.text = fragment.getString(
            R.string.performance_frametime_format,
            snapshot.frameTimeMs,
            snapshot.frameTimeP95Ms
        )
        speed.text = fragment.getString(
            R.string.performance_speed_format,
            snapshot.emulationSpeed * 100.0
        )
        memory.text = fragment.getString(
            R.string.performance_memory_format,
            snapshot.appRssMb,
            snapshot.systemRamMb
        )
        power.text = fragment.getString(
            R.string.performance_power_format,
            snapshot.batteryTemperatureC,
            abs(snapshot.batteryCurrentA),
            snapshot.batteryCapacity
        )
        when {
            snapshot.thermalWarning -> {
                warning.setText(R.string.performance_thermal_warning)
                warning.visibility = View.VISIBLE
            }
            snapshot.performanceWarning -> {
                warning.setText(R.string.performance_speed_warning)
                warning.visibility = View.VISIBLE
            }
            else -> warning.visibility = View.GONE
        }
    }

    private fun toggleCapture() {
        if (!PerformanceSampler.isCapturing()) {
            val mode = OpenSwPerformanceModeManager.getResolvedMode(
                fragment.requireContext(),
                titleId
            )
            PerformanceSampler.startCapture(fragment.requireContext(), titleId, mode.name)
            capture.setText(R.string.performance_capture_stop)
            return
        }

        capture.isEnabled = false
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                PerformanceSampler.finishCapture(fragment.requireContext())
            }
            capture.isEnabled = true
            capture.setText(R.string.performance_capture_start)
            Toast.makeText(
                fragment.requireContext(),
                if (file == null) {
                    R.string.performance_capture_empty
                } else {
                    R.string.performance_capture_saved
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareLatestReport() {
        val reports = File(fragment.requireContext().filesDir, "reports")
        val latest = reports.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.maxByOrNull(File::lastModified)
        if (latest == null) {
            Toast.makeText(
                fragment.requireContext(),
                R.string.performance_report_missing,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            fragment.requireContext(),
            "${fragment.requireContext().packageName}.provider",
            latest
        )
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fragment.startActivity(
            Intent.createChooser(intent, fragment.getString(R.string.performance_share_chooser))
        )
    }

    private fun PerformanceMode.labelResource(): Int = when (this) {
        PerformanceMode.STANDARD -> R.string.opensw_mode_standard
        PerformanceMode.THOR_BALANCED -> R.string.opensw_mode_thor_balanced
        PerformanceMode.THOR_60_STABLE -> R.string.opensw_mode_thor_60_stable
        PerformanceMode.THOR_MAX -> R.string.opensw_mode_thor_max
    }
}
