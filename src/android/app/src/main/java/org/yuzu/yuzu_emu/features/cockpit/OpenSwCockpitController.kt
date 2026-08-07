// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cockpit

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.features.cheats.CheatPanelController
import org.yuzu.yuzu_emu.features.performance.PerformancePanelController
import org.yuzu.yuzu_emu.utils.Log

class OpenSwCockpitController(
    private val fragment: Fragment,
    private val gameTitle: String,
    private val titleId: String,
    private val isPaused: () -> Boolean,
    private val onPauseToggle: () -> Unit,
    private val onOverlayToggle: () -> Unit,
    private val onQuickSettings: () -> Unit,
    private val onStopEmulation: () -> Unit
) : DisplayManager.DisplayListener {
    private val displayManager =
        fragment.requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler = Handler(Looper.getMainLooper())
    private var presentation: CockpitPresentation? = null
    private var cheatPanel: CheatPanelController? = null
    private var performancePanel: PerformancePanelController? = null

    fun start() {
        displayManager.registerDisplayListener(this, handler)
        attachToPreferredDisplay()
    }

    fun stop() {
        displayManager.unregisterDisplayListener(this)
        dismissCockpit()
    }

    fun onEmulationStarted() {
        cheatPanel?.onEmulationStarted()
    }

    fun onPauseStateChanged() {
        presentation
            ?.findViewById<View>(android.R.id.content)
            ?.let(::updatePauseButton)
    }

    override fun onDisplayAdded(displayId: Int) = attachToPreferredDisplay()

    override fun onDisplayRemoved(displayId: Int) {
        if (presentation?.display?.displayId == displayId) {
            dismissCockpit()
        }
        attachToPreferredDisplay()
    }

    override fun onDisplayChanged(displayId: Int) {
        if (presentation?.display?.displayId == displayId &&
            presentation?.display?.state != Display.STATE_ON
        ) {
            onDisplayRemoved(displayId)
        }
    }

    private fun attachToPreferredDisplay() {
        if (!fragment.isAdded || fragment.requireActivity().isFinishing) return
        val target = preferredDisplay() ?: return
        if (presentation?.display?.displayId == target.displayId) return

        dismissCockpit()
        val next = CockpitPresentation(fragment.requireContext(), target, ::configureCockpit)
        runCatching { next.show() }
            .onSuccess {
                presentation = next
                Log.info("[OpenSw] Cockpit attached to display ${target.displayId}")
            }
            .onFailure {
                dismissCockpit()
                Log.warning(
                    "[OpenSw] Unable to attach cockpit to display " +
                        "${target.displayId}: ${it.message}"
                )
            }
    }

    private fun preferredDisplay(): Display? {
        val presentationIds = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .mapTo(mutableSetOf()) { display -> display.displayId }
        val displays = displayManager.displays
        val selectedId = selectCockpitDisplayId(
            displays.map { display ->
                CockpitDisplayCandidate(
                    displayId = display.displayId,
                    isDefault = display.displayId == Display.DEFAULT_DISPLAY,
                    isPresentation = display.displayId in presentationIds,
                    isOn = display.state == Display.STATE_ON,
                    isValid = display.isValid
                )
            }
        )
        return displays.firstOrNull { display ->
            display.displayId == selectedId
        }
    }

    private fun dismissCockpit() {
        performancePanel?.hide()
        performancePanel = null
        presentation?.dismiss()
        presentation = null
        cheatPanel = null
    }

    private fun configureCockpit(root: View) {
        root.findViewById<TextView>(R.id.cockpit_title).text = gameTitle
        root.findViewById<TextView>(R.id.cockpit_context).text = titleId
        val performanceView = root.findViewById<View>(R.id.cockpit_performance_content)
        val cheatsView = root.findViewById<View>(R.id.cockpit_cheats_content)
        val sessionView = root.findViewById<View>(R.id.cockpit_session_content)
        val performance = PerformancePanelController(
            fragment = fragment,
            panel = performanceView,
            gameTitle = gameTitle,
            titleId = titleId,
            compact = true
        )
        performancePanel = performance
        val cheats = CheatPanelController(
            fragment = fragment,
            drawer = root,
            quickSettings = sessionView,
            cheatsPanel = cheatsView,
            gameTitle = gameTitle,
            resizeToDrawer = false,
            compact = true
        )
        cheatPanel = cheats

        root.findViewById<MaterialButtonToggleGroup>(R.id.cockpit_tabs)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                when (checkedId) {
                    R.id.cockpit_tab_performance -> {
                        cheats.hide()
                        sessionView.visibility = View.GONE
                        performance.show()
                    }
                    R.id.cockpit_tab_cheats -> {
                        performance.hide()
                        sessionView.visibility = View.GONE
                        cheats.showCheats()
                    }
                    R.id.cockpit_tab_session -> {
                        performance.hide()
                        cheats.hide()
                        sessionView.visibility = View.VISIBLE
                        updatePauseButton(root)
                    }
                }
            }

        root.findViewById<MaterialButton>(R.id.cockpit_pause).setOnClickListener {
            onPauseToggle()
            updatePauseButton(root)
        }
        root.findViewById<MaterialButton>(R.id.cockpit_overlay).setOnClickListener {
            onOverlayToggle()
        }
        root.findViewById<MaterialButton>(R.id.cockpit_quick_settings).setOnClickListener {
            onQuickSettings()
        }
        root.findViewById<MaterialButton>(R.id.cockpit_stop).setOnClickListener {
            onStopEmulation()
        }
        updatePauseButton(root)
        performance.show()
    }

    private fun updatePauseButton(root: View) {
        val button = root.findViewById<MaterialButton>(R.id.cockpit_pause)
        val state = root.findViewById<TextView>(R.id.cockpit_state)
        if (isPaused()) {
            button.setText(R.string.emulation_unpause)
            button.setIconResource(R.drawable.ic_play)
            state.setText(R.string.cockpit_paused)
            state.setTextColor(root.context.getColor(R.color.opensw_yellow))
        } else {
            button.setText(R.string.emulation_pause)
            button.setIconResource(R.drawable.ic_pause)
            state.setText(R.string.cockpit_running)
            state.setTextColor(root.context.getColor(R.color.opensw_mint))
        }
    }

    private class CockpitPresentation(
        context: Context,
        display: Display,
        private val onReady: (View) -> Unit
    ) : Presentation(context, display, R.style.Theme_Yuzu_Main) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.layout_opensw_cockpit)
            onReady(findViewById(android.R.id.content))
        }
    }
}
