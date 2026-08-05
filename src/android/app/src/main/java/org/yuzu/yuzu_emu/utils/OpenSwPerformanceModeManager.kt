// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.features.settings.model.IntSetting

internal interface OpenSwProfileStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun remove(keys: Collection<String>)
}

internal interface OpenSwSettingsBackend {
    fun getBoolean(key: String): Boolean
    fun getInt(key: String): Int
    fun setBoolean(key: String, value: Boolean)
    fun setInt(key: String, value: Int)
    fun save()
}

internal class OpenSwPerformanceProfile(
    private val store: OpenSwProfileStore,
    private val backend: OpenSwSettingsBackend
) {
    fun mode(): Int = store.getInt(KEY_MODE, MODE_STANDARD)

    fun apply(mode: Int) {
        require(mode in MODE_STANDARD..MODE_EXPERIMENTAL)
        if (mode == MODE_STANDARD) {
            restoreBackup()
            store.putInt(KEY_MODE, MODE_STANDARD)
            backend.save()
            clearBackup()
            return
        }

        captureBackup()
        restoreBackup()
        THOR_BOOLEAN_VALUES.forEach { (key, value) -> backend.setBoolean(key, value) }
        if (mode == MODE_EXPERIMENTAL) {
            EXPERIMENTAL_BOOLEAN_VALUES.forEach { (key, value) ->
                backend.setBoolean(key, value)
            }
            EXPERIMENTAL_INT_VALUES.forEach { (key, value) -> backend.setInt(key, value) }
        }
        backend.save()
        store.putInt(KEY_MODE, mode)
    }

    private fun captureBackup() {
        if (store.getBoolean(KEY_BACKUP_PRESENT, false)) return
        MANAGED_BOOLEAN_KEYS.forEach { key ->
            store.putBoolean(backupKey(key), backend.getBoolean(key))
        }
        MANAGED_INT_KEYS.forEach { key ->
            store.putInt(backupKey(key), backend.getInt(key))
        }
        store.putBoolean(KEY_BACKUP_PRESENT, true)
    }

    private fun restoreBackup() {
        if (!store.getBoolean(KEY_BACKUP_PRESENT, false)) return
        MANAGED_BOOLEAN_KEYS.forEach { key ->
            if (store.contains(backupKey(key))) {
                backend.setBoolean(key, store.getBoolean(backupKey(key), false))
            }
        }
        MANAGED_INT_KEYS.forEach { key ->
            if (store.contains(backupKey(key))) {
                backend.setInt(key, store.getInt(backupKey(key), 0))
            }
        }
    }

    private fun clearBackup() {
        store.remove(
            listOf(KEY_BACKUP_PRESENT) +
                MANAGED_BOOLEAN_KEYS.map(::backupKey) +
                MANAGED_INT_KEYS.map(::backupKey)
        )
    }

    private fun backupKey(key: String) = "$KEY_BACKUP_PREFIX$key"

    companion object {
        const val MODE_STANDARD = 0
        const val MODE_THOR = 1
        const val MODE_EXPERIMENTAL = 2
        const val KEY_MODE = "opensw_performance_mode"

        private const val KEY_BACKUP_PRESENT = "opensw_performance_backup_present"
        private const val KEY_BACKUP_PREFIX = "opensw_performance_backup_"

        internal val THOR_BOOLEAN_VALUES = mapOf(
            BooleanSetting.RENDERER_ASYNC_PRESENTATION.key to true,
            BooleanSetting.USE_OPTIMIZED_VERTEX_BUFFERS.key to true
        )
        internal val EXPERIMENTAL_BOOLEAN_VALUES = mapOf(
            BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key to true,
            BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key to true
        )
        internal val EXPERIMENTAL_INT_VALUES = mapOf(
            IntSetting.ANDROID_PIPELINE_WORKERS.key to 6
        )
        private val MANAGED_BOOLEAN_KEYS =
            (THOR_BOOLEAN_VALUES.keys + EXPERIMENTAL_BOOLEAN_VALUES.keys).toSet()
        private val MANAGED_INT_KEYS = EXPERIMENTAL_INT_VALUES.keys
    }
}

object OpenSwPerformanceModeManager {
    const val KEY_MODE = OpenSwPerformanceProfile.KEY_MODE

    fun getMode(context: Context): Int = profile(context).mode()

    fun apply(context: Context, mode: Int) = profile(context).apply(mode)

    private fun profile(context: Context) = OpenSwPerformanceProfile(
        SharedPreferencesStore(PreferenceManager.getDefaultSharedPreferences(context)),
        NativeSettingsBackend
    )
}

private class SharedPreferencesStore(
    private val preferences: SharedPreferences
) : OpenSwProfileStore {
    override fun contains(key: String) = preferences.contains(key)
    override fun getBoolean(key: String, default: Boolean) = preferences.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = preferences.getInt(key, default)
    override fun putBoolean(key: String, value: Boolean) = preferences.edit {
        putBoolean(key, value)
    }
    override fun putInt(key: String, value: Int) = preferences.edit { putInt(key, value) }
    override fun remove(keys: Collection<String>) = preferences.edit {
        keys.forEach(::remove)
    }
}

private object NativeSettingsBackend : OpenSwSettingsBackend {
    override fun getBoolean(key: String) = NativeConfig.getBoolean(key, true)
    override fun getInt(key: String) = NativeConfig.getInt(key, true)

    override fun setBoolean(key: String, value: Boolean) {
        NativeConfig.setGlobal(key, true)
        NativeConfig.setBoolean(key, value)
    }

    override fun setInt(key: String, value: Int) {
        NativeConfig.setGlobal(key, true)
        NativeConfig.setInt(key, value)
    }

    override fun save() = NativeConfig.saveGlobalConfig()
}
