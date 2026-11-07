package com.example.watchdog

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.example.engine.PocketEngineBridge
import com.example.model.EngineConfig
import com.example.model.GgmlQuantType
import com.example.model.HardwareProfile
import com.example.model.RamTier
import java.io.File

/**
 * Pillar 4: RAM Watchdog & Adaptive Hardware Profiler
 *
 * - Queries `ActivityManager.MemoryInfo` to detect total device RAM and real-time `availMem`.
 * - Inspects `/sys/devices/system/cpu/cpuX/cpufreq/cpuinfo_max_freq` to detect `big_cores_count`.
 * - Applies the 4GB RAM Profile (`Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf`, ctx=4096, K=q8_0, V=q4_0, ubatch=256)
 *   or 6GB+ RAM Profile (`Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf`, ctx=8192, K=q8_0, V=q8_0, ubatch=512).
 * - Subscribes to `ComponentCallbacks2.onTrimMemory(level)` and flushes non-pinned KV cache tokens
 *   on `TRIM_MEMORY_RUNNING_CRITICAL` before Android's `lmkd` terminates the daemon.
 */
class RamWatchdogProfiler(
    private val context: Context,
    private val engineBridge: PocketEngineBridge,
    private val onCriticalOomTrim: (Int, String) -> Unit = { _, _ -> }
) : ComponentCallbacks2 {

    private var registered = false

    fun registerWatchdog() {
        if (!registered) {
            context.applicationContext.registerComponentCallbacks(this)
            registered = true
        }
    }

    fun unregisterWatchdog() {
        if (registered) {
            context.applicationContext.unregisterComponentCallbacks(this)
            registered = false
        }
    }

    /**
     * Queries live RAM telemetry and CPU topology to construct an adaptive [HardwareProfile].
     */
    fun profileDeviceHardware(): HardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRamMb = if (memInfo.totalMem > 0L) {
            memInfo.totalMem / (1024L * 1024L)
        } else {
            4096L
        }
        val availRamMb = if (memInfo.availMem > 0L) {
            memInfo.availMem / (1024L * 1024L)
        } else {
            1850L
        }
        val thresholdMb = memInfo.threshold / (1024L * 1024L)

        val totalCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val bigCores = detectBigPerformanceCores(totalCores)

        val is4GbDevice = totalRamMb <= 4608L
        val tier = when {
            is4GbDevice -> RamTier.TIER_4GB_CONSTRAINED
            totalRamMb <= 6800L -> RamTier.TIER_6GB_STANDARD
            else -> RamTier.TIER_8GB_PLUS
        }

        val recommendedModel = if (is4GbDevice) {
            "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf"
        } else {
            "Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf"
        }

        val recommendedCtx = if (is4GbDevice) 4096 else 8192
        val recommendedTypeK = GgmlQuantType.Q8_0
        val recommendedTypeV = if (is4GbDevice) GgmlQuantType.Q4_0 else GgmlQuantType.Q8_0
        val recommendedUbatch = if (is4GbDevice) 256 else 512

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        return HardwareProfile(
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            lowMemoryWarning = memInfo.lowMemory,
            thresholdMb = thresholdMb,
            totalCpuCores = totalCores,
            bigCoresCount = bigCores,
            socSummary = "$socModel ($abi)",
            tier = tier,
            recommendedModelId = recommendedModel,
            recommendedContextSize = recommendedCtx,
            recommendedTypeK = recommendedTypeK,
            recommendedTypeV = recommendedTypeV,
            recommendedUbatch = recommendedUbatch
        )
    }

    /**
     * Builds the optimal initial [EngineConfig] for the detected device RAM profile.
     */
    fun buildRecommendedEngineConfig(profile: HardwareProfile, activeModelPath: String): EngineConfig {
        return EngineConfig(
            activeModelId = profile.recommendedModelId,
            activeModelPath = activeModelPath,
            useMmap = true,
            useMlock = false,
            flashAttn = true,
            contextSize = profile.recommendedContextSize,
            ubatchSize = profile.recommendedUbatch,
            threads = profile.bigCoresCount,
            gpuLayers = 0,
            typeK = profile.recommendedTypeK,
            typeV = profile.recommendedTypeV,
            enablePld = true,
            pldNgramLen = 3,
            pldMaxDraftTokens = 6,
            pinSystemPrefix = true,
            enableHarnessDiet = true,
            enableGbnfToolGrammar = true,
            enableHybridHighThinking = true
        )
    }

    /**
     * Inspects `/sys/devices/system/cpu/cpuX/cpufreq/cpuinfo_max_freq` to count big/prime ARM cores
     * so `llama.cpp` threads aren't scheduled onto slow efficiency cores.
     */
    private fun detectBigPerformanceCores(totalCores: Int): Int {
        return try {
            val freqs = mutableListOf<Long>()
            for (i in 0 until totalCores) {
                val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (freqFile.exists() && freqFile.canRead()) {
                    val freq = freqFile.readText().trim().toLongOrNull()
                    if (freq != null && freq > 0L) freqs.add(freq)
                }
            }
            if (freqs.size >= 2) {
                val maxFreq = freqs.maxOrNull() ?: 0L
                val minFreq = freqs.minOrNull() ?: 0L
                if (maxFreq > minFreq) {
                    val threshold = minFreq + ((maxFreq - minFreq) * 0.45).toLong()
                    val bigCount = freqs.count { it >= threshold }
                    return bigCount.coerceIn(2, totalCores)
                }
            }
            (totalCores / 2).coerceIn(2, 6)
        } catch (_: Exception) {
            (totalCores / 2).coerceIn(2, 6)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            val levelName = when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL"
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE"
                else -> "TRIM_LEVEL_$level"
            }
            val evicted = engineBridge.emergencyFlushNonPinnedKvCache(levelName)
            onCriticalOomTrim(evicted, levelName)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        val evicted = engineBridge.emergencyFlushNonPinnedKvCache("ON_LOW_MEMORY")
        onCriticalOomTrim(evicted, "ON_LOW_MEMORY")
    }
}
