// SPDX-FileCopyrightText: Copyright 2025 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AppInstallReceiver private constructor(
    private val expectedPackageName: String?,
    private var onComplete: (() -> Unit)?,
    private var onFailure: ((Exception) -> Unit)?,
    private val unregisterOnReceive: Boolean
) : BroadcastReceiver() {
    constructor(
        onComplete: () -> Unit,
        onFailure: (Exception) -> Unit
    ) : this(null, onComplete, onFailure, true)

    internal constructor(
        expectedPackageName: String,
        onComplete: () -> Unit
    ) : this(expectedPackageName, onComplete, null, false)

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        if (expectedPackageName != null &&
            !isExpectedPackageEvent(intent.action, packageName, expectedPackageName)
        ) {
            return
        }

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i("AppInstallReceiver", "Package installed or updated: $packageName")
                onComplete?.invoke()
            }
            else -> onFailure?.invoke(Exception("Installation failed for package: $packageName"))
        }
        if (unregisterOnReceive) {
            context.unregisterReceiver(this)
        }
    }

    fun clearCallback() {
        onComplete = null
        onFailure = null
    }

    companion object {
        internal fun isExpectedPackageEvent(
            action: String?,
            packageName: String?,
            expectedPackageName: String
        ): Boolean =
            packageName == expectedPackageName &&
                (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED)
    }
}
