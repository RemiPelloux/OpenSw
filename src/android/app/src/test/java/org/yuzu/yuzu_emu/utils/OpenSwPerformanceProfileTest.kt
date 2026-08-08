// SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.features.settings.model.IntSetting

class OpenSwPerformanceProfileTest {
    @Test
    fun allPerformanceModesUseHybridThreadPolicy() {
        assertEquals(2, PerformanceMode.STANDARD.threadPerformanceMode)
        assertEquals(2, PerformanceMode.OPTI_60.threadPerformanceMode)
        assertEquals(2, PerformanceMode.MAX.threadPerformanceMode)
    }

    @Test
    fun everyModeAppliesSharedRenderingOptimizations() {
        val store = FakeStore()
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        listOf(
            OpenSwPerformanceProfile.MODE_STANDARD,
            OpenSwPerformanceProfile.MODE_60_OPTI,
            OpenSwPerformanceProfile.MODE_MAX
        ).forEach { mode ->
            profile.apply(mode)
            assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNC_PRESENTATION.key))
            assertTrue(backend.booleans.getValue(BooleanSetting.USE_OPTIMIZED_VERTEX_BUFFERS.key))
            assertTrue(
                backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key)
            )
            assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key))
        }
    }

    @Test
    fun modesUseDistinctWorkerCounts() {
        val store = FakeStore()
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        profile.apply(OpenSwPerformanceProfile.MODE_STANDARD)
        assertEquals(4, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))

        profile.apply(OpenSwPerformanceProfile.MODE_60_OPTI)
        assertEquals(6, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))

        profile.apply(OpenSwPerformanceProfile.MODE_MAX)
        assertEquals(8, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))
    }

    @Test
    fun legacyBalancedModeMigratesToStandard() {
        val store = FakeStore()
        store.putInt(OpenSwPerformanceProfile.KEY_MODE, 1)
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        assertEquals(OpenSwPerformanceProfile.MODE_STANDARD, profile.mode())
        assertEquals(
            OpenSwPerformanceProfile.MODE_STANDARD,
            store.getInt(OpenSwPerformanceProfile.KEY_MODE, -1)
        )
        assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key))
        assertEquals(4, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))
    }

    @Test
    fun existingStandardModeAppliesNewProfileSchema() {
        val store = FakeStore()
        store.putInt(OpenSwPerformanceProfile.KEY_MODE, OpenSwPerformanceProfile.MODE_STANDARD)
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        assertEquals(OpenSwPerformanceProfile.MODE_STANDARD, profile.mode())
        assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNC_PRESENTATION.key))
        assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key))
        assertEquals(
            OpenSwPerformanceProfile.PROFILE_SCHEMA_VERSION,
            store.getInt(OpenSwPerformanceProfile.KEY_SCHEMA_VERSION, 0)
        )
    }

    @Test
    fun switchingToStandardKeepsSharedOptimizations() {
        val store = FakeStore()
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        profile.apply(OpenSwPerformanceProfile.MODE_MAX)
        profile.apply(OpenSwPerformanceProfile.MODE_STANDARD)

        assertTrue(
            backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key)
        )
        assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key))
        assertEquals(4, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))
    }

    private class FakeStore : OpenSwProfileStore {
        private val values = mutableMapOf<String, Any>()
        override fun contains(key: String) = values.containsKey(key)
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }
        override fun putInt(key: String, value: Int) {
            values[key] = value
        }
        override fun remove(keys: Collection<String>) {
            keys.forEach(values::remove)
        }
    }

    private class FakeBackend : OpenSwSettingsBackend {
        val booleans = mutableMapOf(
            BooleanSetting.RENDERER_ASYNC_PRESENTATION.key to false,
            BooleanSetting.USE_OPTIMIZED_VERTEX_BUFFERS.key to false,
            BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key to false,
            BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key to false
        )
        val ints = mutableMapOf(IntSetting.ANDROID_PIPELINE_WORKERS.key to 4)
        private val global = mutableMapOf<String, Boolean>().withDefault { true }

        override fun getBoolean(key: String, needsGlobal: Boolean) = booleans.getValue(key)
        override fun getInt(key: String, needsGlobal: Boolean) = ints.getValue(key)
        override fun usingGlobal(key: String) = global.getValue(key)
        override fun setGlobal(key: String, global: Boolean) {
            this.global[key] = global
        }
        override fun setBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }
        override fun setInt(key: String, value: Int) {
            ints[key] = value
        }
        override fun save() = Unit
    }
}
