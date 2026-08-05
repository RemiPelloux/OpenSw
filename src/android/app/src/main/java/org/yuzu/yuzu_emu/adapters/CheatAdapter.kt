// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.databinding.ListItemCheatBinding

class CheatAdapter(
    private val onToggle: (NativeLibrary.CheatEntry, Boolean) -> Boolean
) : ListAdapter<NativeLibrary.CheatEntry, CheatAdapter.CheatViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheatViewHolder =
        CheatViewHolder(
            ListItemCheatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: CheatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CheatViewHolder(
        private val binding: ListItemCheatBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cheat: NativeLibrary.CheatEntry) {
            val context = binding.root.context
            binding.cheatName.text = cheat.name
            binding.cheatSourceState.text = context.getString(
                if (cheat.enabled) R.string.cheat_source_enabled else R.string.cheat_source_disabled,
                cheat.source
            )
            binding.cheatLock.visibility = if (cheat.isMaster) View.VISIBLE else View.GONE
            binding.cheatSwitch.setOnCheckedChangeListener(null)
            binding.cheatSwitch.isChecked = cheat.enabled
            binding.cheatSwitch.isEnabled = !cheat.isMaster
            binding.cheatSwitch.setOnCheckedChangeListener { button, checked ->
                if (!onToggle(cheat, checked)) {
                    button.isChecked = cheat.enabled
                }
            }
            binding.cheatRow.setOnClickListener {
                if (!cheat.isMaster) {
                    binding.cheatSwitch.performClick()
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<NativeLibrary.CheatEntry>() {
        override fun areItemsTheSame(
            oldItem: NativeLibrary.CheatEntry,
            newItem: NativeLibrary.CheatEntry
        ): Boolean = oldItem.sessionId == newItem.sessionId

        override fun areContentsTheSame(
            oldItem: NativeLibrary.CheatEntry,
            newItem: NativeLibrary.CheatEntry
        ): Boolean = oldItem == newItem
    }
}
