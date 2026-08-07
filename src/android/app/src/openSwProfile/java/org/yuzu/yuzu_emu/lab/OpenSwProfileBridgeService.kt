// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.lab

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.remipelloux.opensw.lab.protocol.IOpenSwProfileBridge
import com.remipelloux.opensw.lab.protocol.LabCommandPolicy
import com.remipelloux.opensw.lab.protocol.RUNTIME_IDENTITY_SCHEMA
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.features.performance.PerformanceSampler
import org.yuzu.yuzu_emu.features.performance.RenderRuntimeSnapshot
import org.yuzu.yuzu_emu.features.settings.model.IntSetting
import org.yuzu.yuzu_emu.utils.DirectoryInitialization

class OpenSwProfileBridgeService : Service() {
    private val sessionGeneration = AtomicLong(1)

    private val binder = object : IOpenSwProfileBridge.Stub() {
        override fun getRuntimeIdentity(): String {
            val runtime = RenderRuntimeSnapshot.from(NativeLibrary.getRenderRuntimeSnapshot())
            val identity = JSONObject()
                .put("schema", RUNTIME_IDENTITY_SCHEMA)
                .put("pid", Process.myPid())
                .put("package_version", BuildConfig.VERSION_NAME)
                .put("session_generation", sessionGeneration.get())
                .put("title_id", runtime?.titleId?.toTitleId() ?: "")
                .put("requested_workers", runtime?.requestedWorkers ?: 0)
                .put("effective_workers", runtime?.effectiveWorkers ?: 0)
                .put("worker_reason", runtime?.workerReason?.name ?: "UNAVAILABLE")
                .put("async_gpu", runtime?.asyncGpu ?: false)
                .put("async_shaders", runtime?.asyncShaders ?: false)
                .put("async_presentation", runtime?.asyncPresentation ?: false)
                .put("descriptor_buffer_available", runtime?.descriptorBufferAvailable ?: false)
                .put("presentation_target", runtime?.presentationTarget ?: 0)
                .put("replay_sha256", "")
                .put("replay_state", "IDLE")
                .put("monotonic_timestamp_ms", SystemClock.elapsedRealtime())
                .toString()
            runCatching { writeRuntimeIdentity(identity) }
            return identity
        }

        override fun setPipelineWorkers(workers: Int): Boolean {
            if (!LabCommandPolicy.validWorkerSelection(workers) || NativeLibrary.isRunning()) {
                return false
            }
            return runCatching {
                IntSetting.ANDROID_PIPELINE_WORKERS.setInt(workers)
                true
            }.getOrDefault(false)
        }

        override fun clearShaderCache(titleId: String): Boolean {
            if (!LabCommandPolicy.validTitleId(titleId) || NativeLibrary.isRunning()) return false
            val normalized = titleId.lowercase(Locale.ROOT)
            val shaderRoot = File(DirectoryInitialization.userDirectory, "cache/shader")
            val target = File(shaderRoot, normalized)
            if (target.parentFile?.canonicalFile != shaderRoot.canonicalFile) return false
            return !target.exists() || target.deleteRecursively()
        }

        override fun stopEmulation(): Boolean {
            if (!NativeLibrary.isRunning()) return true
            NativeLibrary.stopEmulation()
            sessionGeneration.incrementAndGet()
            return true
        }

        override fun startCapture(titleId: String, mode: String): Boolean {
            if (!LabCommandPolicy.validTitleId(titleId) || !NativeLibrary.isRunning()) return false
            if (PerformanceSampler.isCapturing()) return false
            PerformanceSampler.startCapture(applicationContext, titleId, mode)
            return PerformanceSampler.isCapturing()
        }

        override fun finishCapture(): String {
            return PerformanceSampler.finishCapture(applicationContext)?.absolutePath.orEmpty()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun writeRuntimeIdentity(identity: String) {
        val directory = File(filesDir, "lab")
        if (!directory.isDirectory && !directory.mkdirs()) return
        val destination = File(directory, "runtime-identity-v1.json")
        val temporary = File(directory, "runtime-identity-v1.json.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(identity.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(destination)) temporary.delete()
    }

    private fun Long.toTitleId(): String = java.lang.Long.toUnsignedString(this, 16)
        .uppercase(Locale.ROOT)
        .padStart(16, '0')
}
