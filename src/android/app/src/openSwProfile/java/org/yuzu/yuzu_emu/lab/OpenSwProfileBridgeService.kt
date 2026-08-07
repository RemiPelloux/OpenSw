// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.lab

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.remipelloux.opensw.lab.protocol.IOpenSwProfileBridge
import com.remipelloux.opensw.lab.protocol.LabCommandPolicy
import com.remipelloux.opensw.lab.protocol.RUNTIME_IDENTITY_SCHEMA
import com.remipelloux.opensw.lab.protocol.ReplayValidator
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.features.performance.PerformanceSampler
import org.yuzu.yuzu_emu.features.performance.RenderRuntimeSnapshot
import org.yuzu.yuzu_emu.features.performance.OpenSwSessionSnapshot
import org.yuzu.yuzu_emu.features.performance.OpenSwSessionState
import org.yuzu.yuzu_emu.features.settings.model.IntSetting
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.ui.main.MainActivity

class OpenSwProfileBridgeService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val replayController = InputReplayController()

    private val binder = object : IOpenSwProfileBridge.Stub() {
        override fun getRuntimeIdentity(): String {
            val runtime = RenderRuntimeSnapshot.from(NativeLibrary.getRenderRuntimeSnapshot())
            val session = sessionSnapshot()
            val replay = replayController.snapshot()
            val identity = JSONObject()
                .put("schema", RUNTIME_IDENTITY_SCHEMA)
                .put("pid", Process.myPid())
                .put("package_version", BuildConfig.VERSION_NAME)
                .put("session_generation", session?.generation ?: 0)
                .put("session_state", session?.state?.name ?: "UNAVAILABLE")
                .put("surface_attached", session?.surfaceAttached ?: false)
                .put("title_id", session?.titleId?.toTitleId() ?: "")
                .put("requested_workers", runtime?.requestedWorkers ?: 0)
                .put("effective_workers", runtime?.effectiveWorkers ?: 0)
                .put("worker_reason", runtime?.workerReason?.name ?: "UNAVAILABLE")
                .put("async_gpu", runtime?.asyncGpu ?: false)
                .put("async_shaders", runtime?.asyncShaders ?: false)
                .put("async_presentation", runtime?.asyncPresentation ?: false)
                .put("descriptor_buffer_available", runtime?.descriptorBufferAvailable ?: false)
                .put("presentation_target", runtime?.presentationTarget ?: 0)
                .put("replay_sha256", replay.sha256)
                .put("replay_state", replay.state.name)
                .put("monotonic_timestamp_ms", SystemClock.elapsedRealtime())
                .toString()
            runCatching { writeRuntimeIdentity(identity) }
            return identity
        }

        override fun getSessionStatus(): String =
            sessionSnapshot()?.toJson()?.toString()
                ?: JSONObject()
                    .put("schema", "opensw-session-status-v1")
                    .put("state", "UNAVAILABLE")
                    .toString()

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

        override fun launchGame(
            gameUri: String,
            expectedTitleId: String,
            timeoutMs: Long
        ): Boolean {
            if (!LabCommandPolicy.validGameUri(gameUri) ||
                !LabCommandPolicy.validTitleId(expectedTitleId) ||
                !validTimeout(timeoutMs)
            ) {
                return false
            }
            val current = sessionSnapshot() ?: return false
            if (current.state != OpenSwSessionState.STOPPED || current.surfaceAttached) return false
            val libraryIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val gameIntent = Intent(applicationContext, EmulationActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(Uri.parse(gameUri), "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return runCatching {
                startActivities(arrayOf(libraryIntent, gameIntent))
                awaitState(timeoutMs) { session ->
                    session.state == OpenSwSessionState.RUNNING &&
                        session.surfaceAttached &&
                        expectedTitleId.equals(session.titleId.toTitleId(), ignoreCase = true)
                }
            }.getOrDefault(false)
        }

        override fun pauseEmulation(timeoutMs: Long): Boolean {
            if (!validTimeout(timeoutMs)) return false
            if (sessionSnapshot()?.state == OpenSwSessionState.PAUSED) return true
            if (!requestActivityAction { it.requestLabPause() }) return false
            return awaitState(timeoutMs) { it.state == OpenSwSessionState.PAUSED }
        }

        override fun resumeEmulation(timeoutMs: Long): Boolean {
            if (!validTimeout(timeoutMs)) return false
            if (sessionSnapshot()?.state == OpenSwSessionState.RUNNING) return true
            if (!requestActivityAction { it.requestLabResume() }) return false
            return awaitState(timeoutMs) { it.state == OpenSwSessionState.RUNNING }
        }

        override fun stopEmulation(timeoutMs: Long): Boolean {
            if (!validTimeout(timeoutMs)) return false
            val current = sessionSnapshot() ?: return false
            if (current.state == OpenSwSessionState.STOPPED && !current.surfaceAttached) return true
            replayController.cancel()
            if (!requestActivityAction { it.requestLabStop() }) return false
            return awaitState(timeoutMs) {
                it.state == OpenSwSessionState.STOPPED && !it.surfaceAttached
            }
        }

        override fun startReplay(replayJson: String): Boolean {
            if (replayJson.length > MAX_REPLAY_JSON_BYTES) return false
            val replay = runCatching {
                InputReplayJson.parse(replayJson)
            }.getOrNull() ?: return false
            if (ReplayValidator.validate(replay).isNotEmpty()) return false
            return replayController.start(replay)
        }

        override fun cancelReplay(): Boolean = replayController.cancel()

        override fun startCapture(titleId: String, mode: String): Boolean {
            if (!LabCommandPolicy.validTitleId(titleId) || !NativeLibrary.isRunning()) return false
            if (PerformanceSampler.isCapturing()) return false
            PerformanceSampler.startCapture(applicationContext, titleId, mode)
            return PerformanceSampler.isCapturing()
        }

        override fun finishCapture(): String =
            PerformanceSampler.finishCapture(applicationContext)?.readText().orEmpty()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        replayController.shutdown()
        super.onDestroy()
    }

    private fun sessionSnapshot(): OpenSwSessionSnapshot? =
        OpenSwSessionSnapshot.from(NativeLibrary.getSessionSnapshot())

    private fun requestActivityAction(action: (EmulationActivity) -> Boolean): Boolean {
        val latch = CountDownLatch(1)
        var accepted = false
        mainHandler.post {
            accepted = NativeLibrary.sEmulationActivity.get()?.let(action) == true
            latch.countDown()
        }
        return latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS) && accepted
    }

    private fun awaitState(
        timeoutMs: Long,
        predicate: (OpenSwSessionSnapshot) -> Boolean
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() <= deadline) {
            val snapshot = sessionSnapshot()
            if (snapshot != null && predicate(snapshot)) return true
            SystemClock.sleep(STATE_POLL_MS)
        }
        return false
    }

    private fun validTimeout(timeoutMs: Long): Boolean = timeoutMs in 100..MAX_TIMEOUT_MS

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

    companion object {
        private const val STATE_POLL_MS = 25L
        private const val MAIN_THREAD_TIMEOUT_MS = 2_000L
        private const val MAX_TIMEOUT_MS = 120_000L
        private const val MAX_REPLAY_JSON_BYTES = 2 * 1024 * 1024
    }
}
