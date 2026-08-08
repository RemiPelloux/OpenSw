// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import android.content.ClipData
import android.content.Intent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.features.settings.model.IntSetting
import org.yuzu.yuzu_emu.utils.OpenSwPerformanceModeManager
import org.yuzu.yuzu_emu.utils.PerformanceMode

class PerformancePanelController(
    private val fragment: Fragment,
    private val panel: View,
    private val gameTitle: String,
    private val titleId: String,
    private val compact: Boolean = false
) {
    private val title = panel.findViewById<TextView>(R.id.performance_game_title)
    private val context = panel.findViewById<TextView>(R.id.performance_context)
    private val status = panel.findViewById<TextView>(R.id.performance_status)
    private val config = panel.findViewById<TextView>(R.id.performance_config)
    private val warning = panel.findViewById<TextView>(R.id.performance_warning)
    private val fps = panel.findViewById<TextView>(R.id.performance_fps)
    private val frameTime = panel.findViewById<TextView>(R.id.performance_frametime)
    private val speed = panel.findViewById<TextView>(R.id.performance_speed)
    private val shaders = panel.findViewById<TextView>(R.id.performance_shaders)
    private val memory = panel.findViewById<TextView>(R.id.performance_memory)
    private val power = panel.findViewById<TextView>(R.id.performance_power)
    private val graph = panel.findViewById<FrameTimeGraphView>(R.id.performance_graph)
    private val modeToggle =
        panel.findViewById<MaterialButtonToggleGroup>(R.id.performance_mode_toggle)
    private val directGroup = panel.findViewById<View>(R.id.performance_direct_group)
    private val pipelineGroup = panel.findViewById<View>(R.id.performance_pipeline_group)
    private val pipelineEmpty = panel.findViewById<View>(R.id.performance_pipeline_empty)
    private val pipelineCache = panel.findViewById<TextView>(R.id.performance_pipeline_cache)
    private val pipelineWait = panel.findViewById<TextView>(R.id.performance_pipeline_wait)
    private val pipelinePhases = panel.findViewById<TextView>(R.id.performance_pipeline_phases)
    private val presentation = panel.findViewById<TextView>(R.id.performance_presentation)
    private val capture = panel.findViewById<MaterialButton>(R.id.performance_capture)
    private val share = panel.findViewById<MaterialButton>(R.id.performance_share)
    private val samplerConsumer = Any()
    private var isVisible = false
    private var detailsVisible = false
    private var latestPipelineProfile: PipelineProfileSnapshot? = null

    init {
        title.text = gameTitle
        if (compact) {
            panel.findViewById<View>(R.id.performance_brand_rail).visibility = View.GONE
            panel.findViewById<View>(R.id.performance_heading).visibility = View.GONE
            title.visibility = View.GONE
        }
        capture.setOnClickListener { toggleCapture() }
        share.setOnClickListener { shareLatestReport() }
        if (BuildConfig.OPENSW_PROFILE) {
            modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                detailsVisible = checkedId == R.id.performance_mode_details
                updateSelectedView()
            }
        } else {
            modeToggle.visibility = View.GONE
        }
        updateSelectedView()
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
        val modeLabel = fragment.getString(mode.labelResource())
        context.text = if (compact) {
            modeLabel
        } else {
            fragment.getString(
                R.string.performance_context_format,
                titleId,
                modeLabel
            )
        }
        config.text = currentConfiguration()
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
        fps.text = fragment.getString(R.string.performance_fps_value_format, snapshot.fps)
        frameTime.text = fragment.getString(
            R.string.performance_frametime_value_format,
            snapshot.frameTimeP95Ms
        )
        graph.addSample(snapshot.frameTimeMs)
        speed.text = fragment.getString(
            R.string.performance_speed_format,
            snapshot.emulationSpeed * 100.0
        )
        shaders.text = if (snapshot.shadersBuilding == 0) {
            fragment.getString(R.string.performance_shaders_idle)
        } else {
            fragment.resources.getQuantityString(
                R.plurals.performance_shaders_building,
                snapshot.shadersBuilding,
                snapshot.shadersBuilding
            )
        }
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
        renderHealth(performanceHealth(snapshot))
        latestPipelineProfile = snapshot.pipelineProfile?.takeIf { it.matches(titleId) }
        renderPipelineProfile(latestPipelineProfile)
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

    private fun renderHealth(health: PerformanceHealth) {
        val (label, color) = when (health) {
            PerformanceHealth.WAITING ->
                R.string.performance_health_waiting to R.color.opensw_outline
            PerformanceHealth.STABLE ->
                R.string.performance_health_stable to R.color.opensw_mint
            PerformanceHealth.UNEVEN ->
                R.string.performance_health_uneven to R.color.opensw_yellow
            PerformanceHealth.SLOW ->
                R.string.performance_health_slow to R.color.opensw_yellow
            PerformanceHealth.THERMAL ->
                R.string.performance_health_thermal to R.color.opensw_yellow
        }
        status.setText(label)
        status.setTextColor(ContextCompat.getColor(fragment.requireContext(), color))
    }

    private fun renderPipelineProfile(profile: PipelineProfileSnapshot?) {
        if (!BuildConfig.OPENSW_PROFILE || !detailsVisible) {
            pipelineGroup.visibility = View.GONE
            return
        }
        pipelineGroup.visibility = View.VISIBLE
        pipelineEmpty.visibility = if (profile == null) View.VISIBLE else View.GONE
        listOf(pipelineCache, pipelineWait, pipelinePhases, presentation).forEach { metric ->
            metric.visibility = if (profile == null) View.GONE else View.VISIBLE
        }
        if (profile == null) return
        val cacheAccesses = profile.cacheHits + profile.cacheMisses
        val hitRate = if (cacheAccesses == 0L) 0.0 else profile.cacheHits * 100.0 / cacheAccesses
        pipelineCache.text = fragment.getString(
            R.string.performance_pipeline_cache_format,
            hitRate,
            profile.compilations,
            profile.maxQueueDepth
        )
        pipelineWait.text = fragment.getString(
            R.string.performance_pipeline_wait_format,
            profile.pipelineWaits,
            profile.pipelineWaitNs / 1_000_000.0,
            profile.smallDrawWaits
        )
        pipelinePhases.text = fragment.getString(
            R.string.performance_pipeline_phases_format,
            profile.translationNs / 1_000_000.0,
            profile.spirvNs / 1_000_000.0,
            profile.shaderModuleNs / 1_000_000.0,
            profile.vulkanPipelineNs / 1_000_000.0
        )
        presentation.text = fragment.getString(
            R.string.performance_presentation_format,
            profile.maxPresentQueueDepth,
            profile.freeFrameWaitNs / 1_000_000.0,
            profile.schedulerWaitNs / 1_000_000.0,
            profile.swapchainAcquireNs / 1_000_000.0,
            profile.presentNs / 1_000_000.0
        )
    }

    private fun updateSelectedView() {
        directGroup.visibility = if (detailsVisible) View.GONE else View.VISIBLE
        renderPipelineProfile(latestPipelineProfile)
    }

    private fun currentConfiguration(): String {
        val requested = IntSetting.ANDROID_PIPELINE_WORKERS.getInt(false)
        val runtime = RenderRuntimeSnapshot.from(NativeLibrary.getRenderRuntimeSnapshot())
        val requestedLabel = if (requested == 0) {
            fragment.getString(R.string.performance_workers_auto)
        } else {
            fragment.resources.getQuantityString(
                R.plurals.performance_workers,
                requested,
                requested
            )
        }
        val workerLabel = runtime?.let {
            fragment.getString(
                R.string.performance_workers_effective_format,
                requestedLabel,
                it.effectiveWorkers,
                it.workerReason.name.lowercase(Locale.ROOT).replace('_', ' ')
            )
        } ?: requestedLabel
        val displayTarget = IntSetting.ANDROID_PRESENTATION_FRAME_RATE.getInt(false)
        val displayLabel = if (displayTarget == 0) {
            fragment.getString(R.string.performance_display_auto)
        } else {
            fragment.getString(R.string.performance_display_target_format, displayTarget)
        }
        val presentation = if (BooleanSetting.RENDERER_ASYNC_PRESENTATION.getBoolean(false)) {
            fragment.getString(R.string.performance_presentation_async)
        } else {
            fragment.getString(R.string.performance_presentation_sync)
        }
        val threadPolicy = runtime?.threadPolicy?.name?.lowercase(Locale.ROOT)
            ?: fragment.getString(R.string.performance_thread_policy_system)
        return fragment.getString(
            R.string.performance_config_format,
            workerLabel,
            displayLabel,
            presentation,
            threadPolicy
        )
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
        intent.clipData = ClipData.newRawUri(latest.name, uri)
        val chooser = Intent.createChooser(
            intent,
            fragment.getString(R.string.performance_share_chooser)
        ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fragment.startActivity(chooser)
    }

    private fun PerformanceMode.labelResource(): Int = when (this) {
        PerformanceMode.STANDARD -> R.string.opensw_mode_standard
        PerformanceMode.THOR_BALANCED -> R.string.opensw_mode_thor_balanced
        PerformanceMode.THOR_60_STABLE -> R.string.opensw_mode_thor_60_stable
        PerformanceMode.THOR_MAX -> R.string.opensw_mode_thor_max
    }
}
