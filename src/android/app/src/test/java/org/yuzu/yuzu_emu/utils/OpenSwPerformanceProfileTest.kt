// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.features.settings.model.IntSetting

class OpenSwPerformanceProfileTest {
    @Test
    fun performanceModesSelectReversibleThreadPolicies() {
        assertEquals(0, PerformanceMode.STANDARD.threadPerformanceMode)
        assertEquals(1, PerformanceMode.THOR_BALANCED.threadPerformanceMode)
        assertEquals(2, PerformanceMode.THOR_60_STABLE.threadPerformanceMode)
        assertEquals(2, PerformanceMode.THOR_MAX.threadPerformanceMode)
    }

    @Test
    fun balancedModeAppliesOnlyValidatedOverrides() {
        val store = FakeStore()
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        profile.apply(OpenSwPerformanceProfile.MODE_THOR_BALANCED)

        assertEquals(OpenSwPerformanceProfile.MODE_THOR_BALANCED, profile.mode())
        assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNC_PRESENTATION.key))
        assertTrue(backend.booleans.getValue(BooleanSetting.USE_OPTIMIZED_VERTEX_BUFFERS.key))
        assertFalse(
            backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key)
        )
        assertEquals(4, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))
    }

    @Test
    fun stableAndMaxModesUseDistinctWorkerCounts() {
        val store = FakeStore()
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        profile.apply(OpenSwPerformanceProfile.MODE_THOR_60_STABLE)
        assertTrue(
            backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key)
        )
        assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key))
        assertEquals(6, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))

        profile.apply(OpenSwPerformanceProfile.MODE_THOR_MAX)
        assertEquals(8, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))
    }

    @Test
    fun standardRestoresValuesCapturedBeforeThorMode() {
        val store = FakeStore()
        val backend = FakeBackend().apply {
            booleans[BooleanSetting.RENDERER_ASYNC_PRESENTATION.key] = false
            booleans[BooleanSetting.USE_OPTIMIZED_VERTEX_BUFFERS.key] = false
            booleans[BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key] = false
            booleans[BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key] = true
            ints[IntSetting.ANDROID_PIPELINE_WORKERS.key] = 3
        }
        val profile = OpenSwPerformanceProfile(store, backend)
        backend.setGlobal(BooleanSetting.RENDERER_ASYNC_PRESENTATION.key, false)

        profile.apply(OpenSwPerformanceProfile.MODE_THOR_MAX)
        profile.apply(OpenSwPerformanceProfile.MODE_STANDARD)

        assertFalse(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNC_PRESENTATION.key))
        assertFalse(backend.booleans.getValue(BooleanSetting.USE_OPTIMIZED_VERTEX_BUFFERS.key))
        assertFalse(
            backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key)
        )
        assertTrue(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key))
        assertEquals(3, backend.ints.getValue(IntSetting.ANDROID_PIPELINE_WORKERS.key))
        assertFalse(backend.usingGlobal(BooleanSetting.RENDERER_ASYNC_PRESENTATION.key))
        assertEquals(OpenSwPerformanceProfile.MODE_STANDARD, profile.mode())
    }

    @Test
    fun switchingFromStableToBalancedDropsAggressiveOverrides() {
        val store = FakeStore()
        val backend = FakeBackend()
        val profile = OpenSwPerformanceProfile(store, backend)

        profile.apply(OpenSwPerformanceProfile.MODE_THOR_60_STABLE)
        profile.apply(OpenSwPerformanceProfile.MODE_THOR_BALANCED)

        assertFalse(
            backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_GPU_EMULATION.key)
        )
        assertFalse(backend.booleans.getValue(BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.key))
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
