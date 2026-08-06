// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AynThorDetectorTest {
    @Test
    fun detectsThorWithoutDependingOnCapitalizationOrSpacing() {
        assertTrue(AynThorDetector.matches("AYN Technologies", "Thor Max", "THOR"))
    }

    @Test
    fun rejectsOtherAynAndGenericAndroidDevices() {
        assertFalse(AynThorDetector.matches("AYN", "Odin 2", "odin2"))
        assertFalse(AynThorDetector.matches("Google", "Thor", "thor"))
    }
}
