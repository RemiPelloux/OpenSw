// SPDX-FileCopyrightText: Copyright 2025 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.updater

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class APKInstaller(context: Context) {
    private val context = context.applicationContext
    private var receiver: AppInstallReceiver? = null
    private var receiverRegistered = false

    fun install(apkFile: File, onComplete: () -> Unit, onFailure: (Exception) -> Unit) {
        cancel()

        val installReceiver = AppInstallReceiver(context.packageName) {
            cleanupReceiver()
            onComplete()
        }
        receiver = installReceiver

        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                apkFile
            )

            ContextCompat.registerReceiver(
                context,
                installReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                },
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            cleanupReceiver()
            onFailure(e)
        }
    }

    fun cancel() {
        cleanupReceiver()
    }

    private fun cleanupReceiver() {
        val activeReceiver = receiver ?: return
        receiver = null
        activeReceiver.clearCallback()

        if (receiverRegistered) {
            receiverRegistered = false
            try {
                context.unregisterReceiver(activeReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Install receiver was already unregistered", e)
            }
        }
    }

    private companion object {
        const val TAG = "APKInstaller"
    }
}
