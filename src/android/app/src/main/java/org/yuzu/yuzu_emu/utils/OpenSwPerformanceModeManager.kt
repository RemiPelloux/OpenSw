// SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.features.settings.model.IntSetting

enum class PerformanceMode(val value: Int, val threadPerformanceMode: Int) {
    STANDARD(0, 2),
    OPTI_60(2, 2),
    MAX(3, 2);

    companion object {
        fun from(value: Int): PerformanceMode =
            entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

internal interface OpenSwProfileStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun remove(keys: Collection<String>)
}

internal interface OpenSwSettingsBackend {
    fun getBoolean(key: String, needsGlobal: Boolean = true): Boolean
    fun getInt(key: String, needsGlobal: Boolean = true): Int
    fun usingGlobal(key: String): Boolean
    fun setGlobal(key: String, global: Boolean)
    fun setBoolean(key: String, value: Boolean)
    fun setInt(key: String, value: Int)
    fun save()
}

internal class OpenSwPerformanceProfile(
    private val store: OpenSwProfileStore,
    private val backend: OpenSwSettingsBackend
) {
    fun mode(): Int {
        val storedMode = store.getInt(KEY_MODE, MODE_STANDARD)
        val selected = PerformanceMode.from(storedMode)
        if (
            selected.value != storedMode ||
            store.getInt(KEY_SCHEMA_VERSION, 0) < PROFILE_SCHEMA_VERSION
        ) {
            apply(selected.value)
        }
        return selected.value
    }

    fun apply(mode: Int) {
        val selected = PerformanceMode.from(mode)
        require(selected.value == mode)
        captureBackup()
        restoreBackup()
        targetBooleanValues(selected).forEach { (key, value) ->
            backend.setGlobal(key, true)
            backend.setBoolean(key, value)
        }
        targetIntValues(selected).forEach { (key, value) ->
            backend.setGlobal(key, true)
            backend.setInt(key, value)
        }
        backend.save()
        store.putInt(KEY_MODE, selected.value)
        store.putInt(KEY_SCHEMA_VERSION, PROFILE_SCHEMA_VERSION)
    }

    fun targetBooleanValues(mode: PerformanceMode): Map<String, Boolean> =
        MANAGED_BOOLEAN_KEYS.associateWith { key ->
            booleanOverrides(mode)[key] ?: originalBoolean(key)
        }

    fun targetIntValues(mode: PerformanceMode): Map<String, Int> =
        MANAGED_INT_KEYS.associateWith { key ->
            intOverrides(mode)[key] ?: originalInt(key)
        }

    private fun originalBoolean(key: String): Boolean =
        if (store.contains(backupKey(key))) {
            store.getBoolean(backupKey(key), false)
        } else {
            backend.getBoolean(key, true)
        }

    private fun originalInt(key: String): Int =
        if (store.contains(backupKey(key))) {
            store.getInt(backupKey(key), 0)
        } else {
            backend.getInt(key, true)
        }

    private fun captureBackup() {
        if (store.getBoolean(KEY_BACKUP_PRESENT, false)) return
        MANAGED_BOOLEAN_KEYS.forEach { key ->
            store.putBoolean(backupKey(key), backend.getBoolean(key, true))
            store.putBoolean(backupGlobalKey(key), backend.usingGlobal(key))
        }
        MANAGED_INT_KEYS.forEach { key ->
            store.putInt(backupKey(key), backend.getInt(key, true))
            store.putBoolean(backupGlobalKey(key), backend.usingGlobal(key))
        }
        store.putBoolean(KEY_BACKUP_PRESENT, true)
    }

    private fun restoreBackup() {
        if (!store.getBoolean(KEY_BACKUP_PRESENT, false)) return
        MANAGED_BOOLEAN_KEYS.forEach { key ->
            if (store.contains(backupKey(key))) {
                backend.setGlobal(key, true)
                backend.setBoolean(key, store.getBoolean(backupKey(key), false))
                backend.setGlobal(key, store.getBoolean(backupGlobalKey(key), true))
            }
        }
        MANAGED_INT_KEYS.forEach { key ->
            if (store.contains(backupKey(key))) {
                backend.setGlobal(key, true)
                backend.setInt(key, store.getInt(backupKey(key), 0))
                backend.setGlobal(key, store.getBoolean(backupGlobalKey(key), true))
            }
        }
    }

    private fun backupKey(key: String) = "$KEY_BACKUP_PREFIX$key"
    private fun backupGlobalKey(key: String) = "$KEY_BACKUP_GLOBAL_PREFIX$key"

    companion object {
        const val MODE_STANDARD = 0
        const val MODE_60_OPTI = 2
        const val MODE_MAX = 3
        const val KEY_MODE = "opensw_performance_mode"

        internal const val KEY_SCHEMA_VERSION = "opensw_performance_profile_schema"
        internal const val PROFILE_SCHEMA_VERSION = 1
        private const val KEY_BACKUP_PRESENT = "opensw_performance_backup_present"
        private const val KEY_BACKUP_PREFIX = "opensw_performance_backup_"
        private const val KEY_BACKUP_GLOBAL_PREFIX = "opensw_performance_backup_global_"

        internal val COMMON_BOOLEAN_VALUES = mapOf(
            BooleanSetting.RENDERER_ASYNC_PRESENTATION.key to true,
            BooleanSetting.USE_OPTIMIZED_VERTEX_BUFFERS.key to true,
            BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key to true,
            BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key to true
        )
        internal val STANDARD_INT_VALUES = mapOf(
            IntSetting.ANDROID_PIPELINE_WORKERS.key to 4
        )
        internal val OPTI_60_INT_VALUES = mapOf(
            IntSetting.ANDROID_PIPELINE_WORKERS.key to 6
        )
        internal val MAX_INT_VALUES = mapOf(
            IntSetting.ANDROID_PIPELINE_WORKERS.key to 8
        )
        internal val MANAGED_BOOLEAN_KEYS = COMMON_BOOLEAN_VALUES.keys
        internal val MANAGED_INT_KEYS = STANDARD_INT_VALUES.keys

        private fun booleanOverrides(mode: PerformanceMode): Map<String, Boolean> = when (mode) {
            PerformanceMode.STANDARD,
            PerformanceMode.OPTI_60,
            PerformanceMode.MAX -> COMMON_BOOLEAN_VALUES
        }

        private fun intOverrides(mode: PerformanceMode): Map<String, Int> = when (mode) {
            PerformanceMode.STANDARD -> STANDARD_INT_VALUES
            PerformanceMode.OPTI_60 -> OPTI_60_INT_VALUES
            PerformanceMode.MAX -> MAX_INT_VALUES
        }
    }
}

object OpenSwPerformanceModeManager {
    const val INHERIT = -1
    const val KEY_MODE = OpenSwPerformanceProfile.KEY_MODE

    private const val GAME_MODE_PREFIX = "opensw_performance_mode_game_"
    private var sessionBackup: SessionBackup? = null

    fun getMode(context: Context): Int = profile(context).mode()

    fun getGameMode(context: Context, titleId: String): Int {
        val store = store(context)
        val key = gameModeKey(titleId)
        return if (store.contains(key)) {
            val storedMode = store.getInt(key, INHERIT)
            val selected = PerformanceMode.from(storedMode)
            if (selected.value != storedMode) {
                store.putInt(key, selected.value)
            }
            selected.value
        } else {
            INHERIT
        }
    }

    fun getResolvedMode(context: Context, titleId: String): PerformanceMode {
        val gameMode = getGameMode(context, titleId)
        return if (gameMode == INHERIT) {
            PerformanceMode.from(getMode(context))
        } else {
            PerformanceMode.from(gameMode)
        }
    }

    fun apply(context: Context, mode: Int) = profile(context).apply(mode)

    fun setGameMode(context: Context, titleId: String, mode: Int) {
        require(mode == INHERIT || PerformanceMode.from(mode).value == mode)
        val store = store(context)
        val key = gameModeKey(titleId)
        if (mode == INHERIT) {
            store.remove(listOf(key))
        } else {
            store.putInt(key, mode)
        }
    }

    @Synchronized
    fun applyForSession(context: Context, titleId: String): PerformanceMode {
        sessionBackup?.let { return it.mode }
        val gameMode = getGameMode(context, titleId)
        val resolved = getResolvedMode(context, titleId)
        if (gameMode == INHERIT) return resolved

        val backend = NativeSettingsBackend
        val profile = profile(context)
        val booleans = OpenSwPerformanceProfile.MANAGED_BOOLEAN_KEYS.associateWith { key ->
            val wasGlobal = backend.usingGlobal(key)
            SessionBoolean(wasGlobal, backend.getBoolean(key, wasGlobal))
        }
        val ints = OpenSwPerformanceProfile.MANAGED_INT_KEYS.associateWith { key ->
            val wasGlobal = backend.usingGlobal(key)
            SessionInt(wasGlobal, backend.getInt(key, wasGlobal))
        }

        profile.targetBooleanValues(resolved).forEach { (key, value) ->
            backend.setGlobal(key, false)
            backend.setBoolean(key, value)
        }
        profile.targetIntValues(resolved).forEach { (key, value) ->
            backend.setGlobal(key, false)
            backend.setInt(key, value)
        }
        sessionBackup = SessionBackup(titleId, resolved, booleans, ints)
        return resolved
    }

    @Synchronized
    fun restoreAfterSession(titleId: String? = null) {
        val backup = sessionBackup ?: return
        if (titleId != null && normalizeTitleId(titleId) != normalizeTitleId(backup.titleId)) return
        backup.booleans.forEach { (key, state) ->
            NativeSettingsBackend.setGlobal(key, false)
            NativeSettingsBackend.setBoolean(key, state.value)
            NativeSettingsBackend.setGlobal(key, state.wasGlobal)
        }
        backup.ints.forEach { (key, state) ->
            NativeSettingsBackend.setGlobal(key, false)
            NativeSettingsBackend.setInt(key, state.value)
            NativeSettingsBackend.setGlobal(key, state.wasGlobal)
        }
        sessionBackup = null
    }

    private fun profile(context: Context) = OpenSwPerformanceProfile(
        store(context),
        NativeSettingsBackend
    )

    private fun store(context: Context): OpenSwProfileStore = SharedPreferencesStore(
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    )

    private fun gameModeKey(titleId: String) = GAME_MODE_PREFIX + normalizeTitleId(titleId)

    private fun normalizeTitleId(titleId: String) =
        titleId.uppercase().filter(Char::isLetterOrDigit)

    private data class SessionBoolean(val wasGlobal: Boolean, val value: Boolean)
    private data class SessionInt(val wasGlobal: Boolean, val value: Int)
    private data class SessionBackup(
        val titleId: String,
        val mode: PerformanceMode,
        val booleans: Map<String, SessionBoolean>,
        val ints: Map<String, SessionInt>
    )
}

private class SharedPreferencesStore(
    private val preferences: SharedPreferences
) : OpenSwProfileStore {
    override fun contains(key: String) = preferences.contains(key)
    override fun getBoolean(key: String, default: Boolean) = preferences.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = preferences.getInt(key, default)
    override fun putBoolean(key: String, value: Boolean) =
        preferences.edit { putBoolean(key, value) }
    override fun putInt(key: String, value: Int) = preferences.edit { putInt(key, value) }
    override fun remove(keys: Collection<String>) = preferences.edit { keys.forEach(::remove) }
}

private object NativeSettingsBackend : OpenSwSettingsBackend {
    override fun getBoolean(key: String, needsGlobal: Boolean) =
        NativeConfig.getBoolean(key, needsGlobal)

    override fun getInt(key: String, needsGlobal: Boolean) = NativeConfig.getInt(key, needsGlobal)
    override fun usingGlobal(key: String) = NativeConfig.usingGlobal(key)
    override fun setGlobal(key: String, global: Boolean) = NativeConfig.setGlobal(key, global)
    override fun setBoolean(key: String, value: Boolean) = NativeConfig.setBoolean(key, value)
    override fun setInt(key: String, value: Int) = NativeConfig.setInt(key, value)
    override fun save() = NativeConfig.saveGlobalConfig()
}
