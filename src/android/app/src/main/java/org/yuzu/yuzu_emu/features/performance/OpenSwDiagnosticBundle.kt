// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.utils.OpenSwPerformanceModeManager

object OpenSwDiagnosticBundle {
    fun create(context: Context): File {
        val output = File(context.cacheDir, "OpenSw-diagnostics-${System.currentTimeMillis()}.zip")
        val metadata = JSONObject()
            .put("format", "opensw-diagnostics-v1")
            .put("package", context.packageName)
            .put("version", BuildConfig.VERSION_NAME)
            .put("cpu_preset", BuildConfig.OPENSW_CPU_PRESET)
            .put("lto_mode", BuildConfig.OPENSW_LTO_MODE)
            .put("performance_mode", OpenSwPerformanceModeManager.getMode(context))
            .put("device_manufacturer", Build.MANUFACTURER)
            .put("device_model", Build.MODEL)
            .put("device_product", Build.PRODUCT)
            .put(
                "soc_model",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""
            )
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("android_release", Build.VERSION.RELEASE)

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("device.json"))
            zip.write(metadata.toString(2).toByteArray())
            zip.closeEntry()

            val reports = File(context.filesDir, "reports")
            reports.listFiles()
                ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?.sortedByDescending(File::lastModified)
                ?.take(5)
                ?.forEach { report ->
                    zip.putNextEntry(ZipEntry("performance/${report.name}"))
                    report.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return output
    }
}
