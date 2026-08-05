// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.cheats

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.adapters.CheatAdapter
import java.util.Locale
import kotlin.math.min

class CheatPanelController(
    private val fragment: Fragment,
    private val drawer: View,
    private val quickSettings: View,
    private val cheatsPanel: View,
    private val gameTitle: String
) {
    private val preferences = fragment.requireContext().getSharedPreferences(
        "opensw_cheat_states",
        Context.MODE_PRIVATE
    )
    private val title = cheatsPanel.findViewById<TextView>(R.id.cheat_game_title)
    private val contextLabel = cheatsPanel.findViewById<TextView>(R.id.cheat_context)
    private val state = cheatsPanel.findViewById<TextView>(R.id.cheat_state)
    private val list = cheatsPanel.findViewById<RecyclerView>(R.id.cheat_list)
    private val search = cheatsPanel.findViewById<TextInputEditText>(R.id.cheat_search)
    private val filter = cheatsPanel.findViewById<ChipGroup>(R.id.cheat_filter)
    private val adapter = CheatAdapter(::toggle)

    private var context: NativeLibrary.CheatContext? = null
    private var cheats: List<NativeLibrary.CheatEntry> = emptyList()
    private var activeOnly = false

    init {
        title.text = gameTitle
        list.adapter = adapter
        search.doAfterTextChanged { applyFilter() }
        filter.setOnCheckedStateChangeListener { _, checkedIds ->
            activeOnly = checkedIds.firstOrNull() == R.id.cheat_filter_active
            applyFilter()
        }
        resizeDrawer()
    }

    fun showQuickSettings() {
        cheatsPanel.visibility = View.GONE
        quickSettings.visibility = View.VISIBLE
    }

    fun showCheats() {
        quickSettings.visibility = View.GONE
        cheatsPanel.visibility = View.VISIBLE
        refresh(applySavedState = true)
    }

    fun onEmulationStarted() {
        refresh(applySavedState = true)
    }

    private fun refresh(applySavedState: Boolean) {
        context = NativeLibrary.getCheatContext()
        val currentContext = context
        if (currentContext == null) {
            showState(R.string.cheat_session_ended)
            return
        }

        var loaded = NativeLibrary.getLoadedCheats().toList()
        if (applySavedState) {
            loaded.filterNot { it.isMaster }.forEach { cheat ->
                val key = CheatStateKey.of(currentContext, cheat)
                if (preferences.contains(key)) {
                    val saved = preferences.getBoolean(key, false)
                    if (saved != cheat.enabled) {
                        NativeLibrary.setCheatEnabled(cheat.sessionId, saved)
                    }
                }
            }
            loaded = NativeLibrary.getLoadedCheats().toList()
        }

        cheats = loaded.sortedWith(compareByDescending<NativeLibrary.CheatEntry> { it.isMaster }
            .thenBy { it.name.lowercase(Locale.ROOT) })
        val activeCount = cheats.count { it.enabled }
        contextLabel.text = fragment.getString(
            R.string.cheat_context_format,
            currentContext.titleId,
            currentContext.buildId,
            fragment.resources.getQuantityString(
                R.plurals.cheat_active_count,
                activeCount,
                activeCount
            )
        )
        if (cheats.isEmpty()) {
            showState(R.string.cheat_no_file)
        } else {
            state.visibility = View.GONE
            list.visibility = View.VISIBLE
            applyFilter()
        }
    }

    private fun toggle(cheat: NativeLibrary.CheatEntry, enabled: Boolean): Boolean {
        val currentContext = context ?: return false
        if (!NativeLibrary.setCheatEnabled(cheat.sessionId, enabled)) {
            Toast.makeText(fragment.requireContext(), R.string.cheat_toggle_error, Toast.LENGTH_LONG)
                .show()
            return false
        }

        preferences.edit().putBoolean(CheatStateKey.of(currentContext, cheat), enabled).apply()
        if (!enabled) {
            Toast.makeText(
                fragment.requireContext(),
                R.string.cheat_restart_warning,
                Toast.LENGTH_LONG
            ).show()
        }
        refresh(applySavedState = false)
        return true
    }

    private fun applyFilter() {
        val query = search.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        adapter.submitList(cheats.filter { cheat ->
            (!activeOnly || cheat.enabled) &&
                (query.isEmpty() || cheat.name.lowercase(Locale.ROOT).contains(query))
        })
    }

    private fun showState(message: Int) {
        state.setText(message)
        state.visibility = View.VISIBLE
        list.visibility = View.GONE
    }

    private fun resizeDrawer() {
        drawer.post {
            val maxWidth = (420 * fragment.resources.displayMetrics.density).toInt()
            val viewportWidth = fragment.resources.displayMetrics.widthPixels
            drawer.layoutParams = drawer.layoutParams.apply {
                width = min((viewportWidth * 0.92f).toInt(), maxWidth)
            }
        }
    }
}

internal object CheatStateKey {
    fun of(
        context: NativeLibrary.CheatContext,
        cheat: NativeLibrary.CheatEntry
    ): String = "${context.titleId}:${context.buildId}:${cheat.fingerprint}"
}
