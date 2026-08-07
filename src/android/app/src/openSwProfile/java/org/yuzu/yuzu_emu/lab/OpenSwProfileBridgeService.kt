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
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.features.cheats.CheatStateKey
import org.yuzu.yuzu_emu.features.performance.PerformanceSampler
import org.yuzu.yuzu_emu.features.performance.RenderRuntimeSnapshot
import org.yuzu.yuzu_emu.features.performance.OpenSwSessionSnapshot
import org.yuzu.yuzu_emu.features.performance.OpenSwSessionState
import org.yuzu.yuzu_emu.features.settings.model.IntSetting
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.utils.NativeConfig
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
                .put("resolution_setup", IntSetting.RENDERER_RESOLUTION.getInt(true))
                .put("scaling_filter", IntSetting.RENDERER_SCALING_FILTER.getInt(true))
                .put("fsr_sharpening", IntSetting.FSR_SHARPENING_SLIDER.getInt(true))
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
            if (!LabCommandPolicy.validWorkerSelection(workers)) {
                return false
            }
            return setGlobalIntSettings(IntSetting.ANDROID_PIPELINE_WORKERS to workers)
        }

        override fun setGraphicsConfig(
            resolution: Int,
            scalingFilter: Int,
            sharpening: Int
        ): Boolean {
            if (!LabCommandPolicy.validResolutionSetup(resolution) ||
                !LabCommandPolicy.validScalingFilter(scalingFilter) ||
                !LabCommandPolicy.validSharpening(sharpening)
            ) {
                return false
            }
            return setGlobalIntSettings(
                IntSetting.RENDERER_RESOLUTION to resolution,
                IntSetting.RENDERER_SCALING_FILTER to scalingFilter,
                IntSetting.FSR_SHARPENING_SLIDER to sharpening
            )
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

        override fun finishCapture(): String {
            val report = PerformanceSampler.finishCapture(applicationContext)
                ?: latestPendingCaptureReport()
                ?: return ""
            return exportCaptureReport(report)
        }

        override fun getCheats(): String {
            val context = NativeLibrary.getCheatContext() ?: return ""
            val entries = JSONArray()
            NativeLibrary.getLoadedCheats().forEach { cheat ->
                entries.put(
                    JSONObject()
                        .put("name", cheat.name)
                        .put("enabled", cheat.enabled)
                        .put("is_master", cheat.isMaster)
                        .put("fingerprint", cheat.fingerprint)
                        .put("source", cheat.source)
                )
            }
            return JSONObject()
                .put("title_id", context.titleId)
                .put("build_id", context.buildId)
                .put("entries", entries)
                .toString()
        }

        override fun setCheatEnabled(name: String, enabled: Boolean): Boolean {
            if (!NativeLibrary.isRunning() || name.isBlank()) return false
            val context = NativeLibrary.getCheatContext() ?: return false
            val matches = NativeLibrary.getLoadedCheats().filter { it.name == name }
            if (matches.size != 1) return false
            val cheat = matches.single()
            if (cheat.isMaster && !enabled) return false
            if (!NativeLibrary.setCheatEnabled(cheat.sessionId, enabled)) return false
            return getSharedPreferences("opensw_cheat_states", MODE_PRIVATE)
                .edit()
                .putBoolean(CheatStateKey.of(context, cheat), enabled)
                .commit()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        replayController.shutdown()
        super.onDestroy()
    }

    private fun latestPendingCaptureReport(): File? =
        File(filesDir, "reports")
            .listFiles { file ->
                file.isFile &&
                    file.name.startsWith("opensw-performance-") &&
                    file.extension == "json"
            }
            ?.maxByOrNull(File::lastModified)

    private fun exportCaptureReport(report: File): String {
        val root = checkNotNull(getExternalFilesDir(null)) {
            "Profile external files directory is unavailable"
        }
        val directory = File(root, LAB_CAPTURE_EXPORT_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) {
            "Could not create the lab capture export directory"
        }
        val exported = File(directory, report.name)
        val sourceSha256 = sha256(report)
        report.copyTo(exported, overwrite = true)
        check(exported.length() == report.length() && sha256(exported) == sourceSha256) {
            exported.delete()
            "Lab capture export verification failed"
        }
        report.delete()
        return JSONObject()
            .put("schema", LAB_CAPTURE_EXPORT_SCHEMA)
            .put("path", exported.absolutePath)
            .put("byte_size", exported.length())
            .put("sha256", sourceSha256)
            .toString()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
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

    private fun setGlobalIntSettings(vararg settings: Pair<IntSetting, Int>): Boolean {
        if (NativeLibrary.isRunning() || NativeConfig.isPerGameConfigLoaded()) return false
        return runCatching {
            settings.forEach { (setting, value) ->
                setting.global = true
                setting.setInt(value)
            }
            NativeConfig.saveGlobalConfig()
            true
        }.getOrDefault(false)
    }

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
        private const val LAB_CAPTURE_EXPORT_SCHEMA = "opensw-lab-capture-export-v1"
        private const val LAB_CAPTURE_EXPORT_DIRECTORY = "opensw-lab-captures"
        private const val STATE_POLL_MS = 25L
        private const val MAIN_THREAD_TIMEOUT_MS = 2_000L
        private const val MAX_TIMEOUT_MS = 120_000L
        private const val MAX_REPLAY_JSON_BYTES = 2 * 1024 * 1024
    }
}
