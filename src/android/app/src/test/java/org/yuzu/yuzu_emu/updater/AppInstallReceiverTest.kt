// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.updater

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInstallReceiverTest {
    @Test
    fun `keeps legacy callback constructor`() {
        assertNotNull(AppInstallReceiver(onComplete = {}, onFailure = {}))
    }

    @Test
    fun `accepts added and replaced events for expected package`() {
        assertTrue(
            AppInstallReceiver.isExpectedPackageEvent(
                Intent.ACTION_PACKAGE_ADDED,
                EXPECTED_PACKAGE,
                EXPECTED_PACKAGE
            )
        )
        assertTrue(
            AppInstallReceiver.isExpectedPackageEvent(
                Intent.ACTION_PACKAGE_REPLACED,
                EXPECTED_PACKAGE,
                EXPECTED_PACKAGE
            )
        )
    }

    @Test
    fun `ignores unrelated packages and actions`() {
        assertFalse(
            AppInstallReceiver.isExpectedPackageEvent(
                Intent.ACTION_PACKAGE_ADDED,
                "com.example.other",
                EXPECTED_PACKAGE
            )
        )
        assertFalse(
            AppInstallReceiver.isExpectedPackageEvent(
                Intent.ACTION_PACKAGE_REMOVED,
                EXPECTED_PACKAGE,
                EXPECTED_PACKAGE
            )
        )
    }

    private companion object {
        const val EXPECTED_PACKAGE = "org.yuzu.yuzu_emu"
    }
}
