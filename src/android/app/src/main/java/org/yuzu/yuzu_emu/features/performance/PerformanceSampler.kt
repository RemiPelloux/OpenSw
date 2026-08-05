// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.features.performance

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.core.content.ContextCompat
import java.io.File
import java.util.IdentityHashMap
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.utils.Log
import org.json.JSONArray
import org.json.JSONObject

data class PerformanceSnapshot(
    val timestampMs: Long = 0L,
    val fps: Double = 0.0,
    val systemFps: Double = 0.0,
    val frameTimeMs: Double = 0.0,
    val frameTimeP95Ms: Double = 0.0,
    val emulationSpeed: Double = 0.0,
    val appRssMb: Long = 0L,
    val systemRamMb: Long = 0L,
    val shadersBuilding: Int = 0,
    val batteryTemperatureC: Float = 0f,
    val batteryCurrentA: Double = 0.0,
    val batteryCapacity: Int = 0,
    val charging: Boolean = false,
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    val thermalWarning: Boolean = false,
    val performanceWarning: Boolean = false
) {
    fun compactLabel(): String = String.format(
        Locale.ROOT,
        "%.1f FPS | %.1f ms p95 | %d MB",
        fps,
        frameTimeP95Ms,
        appRssMb
    )
}

data class PerformanceMetricRequirements(
    val frameStats: Boolean = false,
    val emulationSpeed: Boolean = false,
    val appRss: Boolean = false,
    val systemRam: Boolean = false,
    val shaders: Boolean = false,
    val battery: Boolean = false,
    val thermal: Boolean = false
) {
    operator fun plus(other: PerformanceMetricRequirements) = PerformanceMetricRequirements(
        frameStats = frameStats || other.frameStats,
        emulationSpeed = emulationSpeed || other.emulationSpeed,
        appRss = appRss || other.appRss,
        systemRam = systemRam || other.systemRam,
        shaders = shaders || other.shaders,
        battery = battery || other.battery,
        thermal = thermal || other.thermal
    )

    val needsSlowSample: Boolean
        get() = appRss || systemRam || battery || thermal

    val needsFastSample: Boolean
        get() = frameStats || emulationSpeed || shaders

    companion object {
        val NONE = PerformanceMetricRequirements()
        val ALL = PerformanceMetricRequirements(
            frameStats = true,
            emulationSpeed = true,
            appRss = true,
            systemRam = true,
            shaders = true,
            battery = true,
            thermal = true
        )
    }
}

object PerformanceSampler {
    private const val FAST_SAMPLE_MS = 250L
    private const val SLOW_SAMPLE_MS = 1_000L
    private const val WARNING_DURATION_MS = 10_000L
    private const val PERFORMANCE_WARNING_DURATION_MS = 30_000L
    private const val FRAME_WINDOW_SIZE = 120

    private val state = MutableStateFlow(PerformanceSnapshot())
    val snapshots: StateFlow<PerformanceSnapshot> = state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var samplingJob: Job? = null
    private var appContext: Context? = null
    @Volatile
    private var batteryState = BatteryState()
    private var batteryReceiverRegistered = false
    private val consumers = IdentityHashMap<Any, PerformanceMetricRequirements>()
    private val captureConsumer = Any()
    private val thermalCondition = SustainedCondition(WARNING_DURATION_MS)
    private val performanceCondition = SustainedCondition(PERFORMANCE_WARNING_DURATION_MS)
    private val frameTimesMs = ArrayDeque<Double>(FRAME_WINDOW_SIZE)
    private var capture: PerformanceCapture? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            batteryState = batteryState.copy(
                temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            )
        }
    }

    @Synchronized
    fun acquire(
        context: Context,
        consumer: Any,
        requirements: PerformanceMetricRequirements = PerformanceMetricRequirements.ALL
    ) {
        consumers[consumer] = requirements
        if (capture != null) {
            consumers[captureConsumer] = PerformanceMetricRequirements.ALL
        }
        if (samplingJob?.isActive == true) {
            updateBatteryReceiver()
            return
        }
        val applicationContext = context.applicationContext
        appContext = applicationContext
        updateBatteryReceiver()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        samplingJob = scope!!.launch { sampleLoop(applicationContext) }
    }

    @Synchronized
    fun release(consumer: Any) {
        consumers.remove(consumer)
        if (consumers.isNotEmpty()) {
            updateBatteryReceiver()
            return
        }
        stopSampling()
    }

    @Synchronized
    fun stopAll() {
        consumers.clear()
        stopSampling()
    }

    private fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
        scope?.cancel()
        scope = null
        appContext?.let(::unregisterBatteryReceiver)
        appContext = null
        frameTimesMs.clear()
        thermalCondition.reset()
        performanceCondition.reset()
        state.value = PerformanceSnapshot()
    }

    private suspend fun sampleLoop(context: Context) {
        var lastSlowSampleMs = 0L
        var slow = SlowMetrics()
        while (currentCoroutineContext().isActive) {
            val now = SystemClock.elapsedRealtime()
            val requirements = combinedRequirements()
            if (requirements.needsSlowSample && now - lastSlowSampleMs >= SLOW_SAMPLE_MS) {
                slow = readSlowMetrics(context, requirements)
                lastSlowSampleMs = now
            }

            val isRunning = NativeLibrary.isRunning()
            val perf = if (
                isRunning && (requirements.frameStats || requirements.emulationSpeed)
            ) {
                NativeLibrary.getPerfStats()
            } else {
                DoubleArray(4)
            }
            val frameTimeMs = perf.getOrElse(2) { 0.0 } * 1_000.0
            if (frameTimeMs > 0.0 && frameTimeMs.isFinite()) {
                if (frameTimesMs.size == FRAME_WINDOW_SIZE) frameTimesMs.removeFirst()
                frameTimesMs.addLast(frameTimeMs)
            }
            val speed = perf.getOrElse(3) { 0.0 }
            val thermalWarning = if (requirements.thermal) {
                updateThermalWarning(now, slow.thermalStatus)
            } else {
                thermalCondition.reset()
                false
            }
            val performanceWarning = if (requirements.emulationSpeed) {
                updatePerformanceWarning(now, speed)
            } else {
                performanceCondition.reset()
                false
            }

            state.value = PerformanceSnapshot(
                timestampMs = System.currentTimeMillis(),
                systemFps = perf.getOrElse(0) { 0.0 },
                fps = perf.getOrElse(1) { 0.0 },
                frameTimeMs = frameTimeMs,
                frameTimeP95Ms = percentile95(frameTimesMs),
                emulationSpeed = speed,
                appRssMb = slow.appRssMb,
                systemRamMb = slow.systemRamMb,
                shadersBuilding = if (isRunning && requirements.shaders) {
                    NativeLibrary.getShadersBuilding()
                } else {
                    0
                },
                batteryTemperatureC = slow.batteryTemperatureC,
                batteryCurrentA = slow.batteryCurrentA,
                batteryCapacity = slow.batteryCapacity,
                charging = slow.charging,
                thermalStatus = slow.thermalStatus,
                thermalWarning = thermalWarning,
                performanceWarning = performanceWarning
            )
            synchronized(this@PerformanceSampler) {
                capture?.samples?.add(state.value)
            }
            delay(if (requirements.needsFastSample) FAST_SAMPLE_MS else SLOW_SAMPLE_MS)
        }
    }

    @Synchronized
    fun isCapturing(): Boolean = capture != null

    @Synchronized
    fun startCapture(context: Context, titleId: String, mode: String) {
        if (capture != null) return
        acquire(context, captureConsumer)
        capture = PerformanceCapture(
            titleId = titleId.uppercase().filter(Char::isLetterOrDigit),
            mode = mode,
            startedAtMs = System.currentTimeMillis()
        )
    }

    @Synchronized
    fun finishCapture(context: Context): File? {
        val finished = capture ?: return null
        capture = null
        release(captureConsumer)
        if (finished.samples.isEmpty()) return null

        val directory = File(context.filesDir, "reports")
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val file = File(directory, "opensw-performance-${finished.startedAtMs}.json")
        val samples = JSONArray()
        finished.samples.forEach { sample ->
            samples.put(
                JSONObject()
                    .put("timestamp_ms", sample.timestampMs)
                    .put("fps", sample.fps)
                    .put("frametime_ms", sample.frameTimeMs)
                    .put("frametime_p95_ms", sample.frameTimeP95Ms)
                    .put("emulation_speed", sample.emulationSpeed)
                    .put("rss_mb", sample.appRssMb)
                    .put("thermal_status", sample.thermalStatus)
                    .put("battery_temperature_c", sample.batteryTemperatureC)
            )
        }
        val report = JSONObject()
            .put("format", "opensw-performance-v1")
            .put("title_id", finished.titleId)
            .put("mode", finished.mode)
            .put("started_at_ms", finished.startedAtMs)
            .put("finished_at_ms", System.currentTimeMillis())
            .put("samples", samples)
        file.writeText(report.toString(2))
        return file
    }

    private suspend fun readSlowMetrics(
        context: Context,
        requirements: PerformanceMetricRequirements
    ): SlowMetrics =
        withContext(Dispatchers.IO) {
            val memoryInfo = if (requirements.systemRam) {
                ActivityManager.MemoryInfo().also { info ->
                    val manager =
                        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    manager.getMemoryInfo(info)
                }
            } else {
                null
            }
            val batteryManager = if (requirements.battery) {
                context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            } else {
                null
            }
            val currentMicroAmps = batteryManager?.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
            ) ?: 0
            val capacity = batteryManager?.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            ) ?: 0
            val battery = batteryState
            SlowMetrics(
                appRssMb = if (requirements.appRss) readRssMb() else 0L,
                systemRamMb = memoryInfo?.let {
                    (it.totalMem - it.availMem) / 1_048_576L
                } ?: 0L,
                batteryTemperatureC = if (requirements.battery) battery.temperatureC else 0f,
                batteryCurrentA = currentMicroAmps / 1_000_000.0,
                batteryCapacity = capacity.coerceIn(0, 100),
                charging = requirements.battery && battery.charging,
                thermalStatus = if (
                    requirements.thermal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ) {
                    val manager =
                        context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    manager.currentThermalStatus
                } else {
                    PowerManager.THERMAL_STATUS_NONE
                }
            )
        }

    private fun readRssMb(): Long = runCatching {
        val residentPages = File("/proc/self/statm").bufferedReader().use { reader ->
            val line = reader.readLine()
            val residentStart = line.indexOf(' ') + 1
            val residentEnd = line.indexOf(' ', residentStart).let { index ->
                if (index == -1) line.length else index
            }
            line.substring(residentStart, residentEnd).toLong()
        }
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
        residentPages * pageSize / 1_048_576L
    }.onFailure {
        Log.warning("[PerformanceSampler] Failed to read process RSS: ${it.message}")
    }.getOrDefault(0L)

    private fun updateThermalWarning(now: Long, thermalStatus: Int): Boolean {
        return thermalCondition.update(now, thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE)
    }

    private fun updatePerformanceWarning(now: Long, speed: Double): Boolean {
        return performanceCondition.update(now, speed in 0.01..0.949)
    }

    @Synchronized
    private fun combinedRequirements(): PerformanceMetricRequirements = consumers.values.fold(
        PerformanceMetricRequirements.NONE,
        PerformanceMetricRequirements::plus
    )

    private fun updateBatteryReceiver() {
        val context = appContext ?: return
        val needsBattery = consumers.values.any { it.battery }
        if (needsBattery) {
            registerBatteryReceiver(context)
        } else {
            unregisterBatteryReceiver(context)
        }
    }

    private fun registerBatteryReceiver(context: Context) {
        if (batteryReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        batteryReceiverRegistered = true
    }

    private fun unregisterBatteryReceiver(context: Context) {
        if (!batteryReceiverRegistered) return
        runCatching { context.unregisterReceiver(batteryReceiver) }
        batteryReceiverRegistered = false
    }

    private data class BatteryState(
        val temperatureC: Float = 0f,
        val charging: Boolean = false
    )

    private data class SlowMetrics(
        val appRssMb: Long = 0L,
        val systemRamMb: Long = 0L,
        val batteryTemperatureC: Float = 0f,
        val batteryCurrentA: Double = 0.0,
        val batteryCapacity: Int = 0,
        val charging: Boolean = false,
        val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    )

    private data class PerformanceCapture(
        val titleId: String,
        val mode: String,
        val startedAtMs: Long,
        val samples: MutableList<PerformanceSnapshot> = mutableListOf()
    )
}
