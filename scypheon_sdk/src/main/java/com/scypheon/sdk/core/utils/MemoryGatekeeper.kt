package com.scypheon.sdk.core.utils

import android.app.ActivityManager
import android.content.Context
import timber.log.Timber

/**
 * MemoryGatekeeper manages the safety logic for loading large neural models
 * on hardware-constrained Android devices.
 */
object MemoryGatekeeper {

    data class MemoryReport(
        val availableMB: Long,
        val projectedLoadMB: Long,
        val isHealthy: Boolean,
        val stressLevel: Int, // 0: OK, 1: WARNING, 2: CRITICAL
        val isVetoRequired: Boolean
    )

    private const val BYTES_PER_TOKEN = 1024L // Abstract estimate; refined at runtime

    /**
     * TIERED DYNAMIC HEADROOM (Solaris 4.1 Spec)
     * Android LMKD avoids killing background apps if free RAM stays above these thresholds.
     * Note: Increased headroom for 12GB+ devices to account for Samsung One UI background
     * services that consume ~2GB of RAM at idle.
     */
    fun computeHeadroomMB(totalRAMMB: Long): Long = when {
        totalRAMMB < 4096 -> 700L     // 4GB Devices: Tight headroom (+100MB)
        totalRAMMB < 6144 -> 1000L    // 6GB Devices (+200MB)
        totalRAMMB < 8192 -> 1536L    // 8GB Devices (+512MB)
        totalRAMMB < 12288 -> 2048L   // 8-12GB Devices (+512MB)
        else -> 3072L                 // 12GB+ Devices: Samsung One UI uses ~2GB at idle (+1GB safety)
    }

    /**
     * Calculates the maximum safe KV cache token count based on real-time memory pressure.
     * MDRS 4.1 Spec: No hard caps. Fully dynamic based on VRAM availability.
     */
    fun calculateSafeKvCache(context: Context, modelSizeBytes: Long): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMB = memInfo.totalMem shr 20
        val availableMB = memInfo.availMem shr 20
        val modelSizeMB = modelSizeBytes shr 20
        val headroomMB = computeHeadroomMB(totalMB)

        // [MDRS 4.2] Precision Heuristic: Q4_0 K-cache + Q8_0 V-cache
        // Formula: n_layer * n_embd * 1.625 bytes per token
        // - K (Q4_0): 4.5 bits/weight = 0.5625 bytes
        // - V (Q8_0): 8.5 bits/weight = 1.0625 bytes
        // Total: 1.625 bytes per 'weight' element in the KV cache.
        
        val estimatedBytesPerToken = when {
            modelSizeMB < 1500 -> 16 * 2048 * 1.625    // ~1B (Llama-3): ~52KB/token
            modelSizeMB < 3500 -> 28 * 3200 * 1.625    // ~3B (StableLM): ~142KB/token
            modelSizeMB < 6500 -> 32 * 4096 * 1.625    // ~7B/8B (Llama-2/3): ~208KB/token
            else -> 48 * 5120 * 1.625                  // ~11B+ (Command-R): ~390KB/token
        }.toLong()

        // Reserve for llama.cpp compute graph buffer allocation during prefill.
        // Increased for high-RAM devices to allow larger n_batch (512+).
        val computeBufferReserveMB = if (totalMB >= 12000) 512L else 350L

        // [v1.2.7-SAR] zRAM Efficiency Boost: Most Android devices use 50-70% zRAM compression.
        // We can safely overcommit memory by ~40% for the KV cache which is mostly zero-heavy.
        val zRamBonus = 1.4
        val netAvailableMB = ((availableMB - headroomMB - modelSizeMB - computeBufferReserveMB) * zRamBonus).coerceAtLeast(0.0).toLong()
        
        // Convert to tokens dynamically (MDRS 4.2: High Precision)
        val maxTokens = (netAvailableMB * 1024 * 1024 / estimatedBytesPerToken).toInt()
        
        // [SAR 1.0.4] Power-of-Two Quantization (1024, 2048, 4096...)
        // Increased floor to 1024 to support modern system prompts (Gemma-4/Llama-3).
        var quantizedTokens = 1024
        while (quantizedTokens * 2 <= maxTokens && quantizedTokens < 32768) {
            quantizedTokens *= 2
        }
        
        // If device is extremely low RAM (< 4GB), we allow 512 as an absolute floor.
        // [v1.2.9] Backend now supports up to 32k if RAM allows.
        val floor = if (totalMB < 4096) 512 else 1024
        val finalTokens = quantizedTokens.coerceAtLeast(floor).coerceAtMost(32768)
        
        Timber.i("🛰️ [MDRS 4.2] Context Logic: Avail=${availableMB}MB (zBonus=${netAvailableMB}MB) | Model=${modelSizeMB}MB | FinalTokens=$finalTokens")
        
        return finalTokens
    }

    fun performPreflightCheck(context: Context, modelSizeBytes: Long, kvCacheRequestedMB: Int = 2048, isCpuMode: Boolean = false): MemoryReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val availableMB = memInfo.availMem / (1024 * 1024)
        val modelSizeMB = modelSizeBytes / (1024 * 1024)
        val safeTokens = calculateSafeKvCache(context, modelSizeBytes)
        val projectedLoadMB = modelSizeMB + (safeTokens * BYTES_PER_TOKEN / 1024 / 1024)

        val isHealthy = availableMB - projectedLoadMB > computeHeadroomMB(memInfo.totalMem shr 20)
        val stressLevel = if (isHealthy) 0 else if (availableMB > modelSizeMB + 256) 1 else 2
        val isVetoRequired = availableMB < (modelSizeMB + 128)

        Timber.i("📊 MDRS Proactive: Available=${availableMB}MB, SafeTokens=$safeTokens, Projected=${projectedLoadMB}MB, Veto=$isVetoRequired")
        
        return MemoryReport(availableMB, projectedLoadMB, isHealthy, stressLevel, isVetoRequired)
    }

    /**
     * [v1.0.5-SAR] Strict Pre-load Enforcement.
     * Verifies if the device has enough available RAM to load the model while
     * maintaining a safety buffer for the OS and UI.
     */
    fun canLoadModel(context: Context, modelSizeBytes: Long): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        
        val availableGB = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val modelSizeGB = modelSizeBytes / (1024.0 * 1024.0 * 1024.0)
        
        // Architect Recommendation: Reserve 2GB for OS + UI + Background
        val safetyBufferGB = 2.0
        val requiredGB = modelSizeGB + safetyBufferGB
        
        val canLoad = availableGB > requiredGB
        
        if (!canLoad) {
            Timber.w(" [GATEKEEPER] LOAD DENIED: Available=${String.format("%.2f", availableGB)}GB | Required=${String.format("%.2f", requiredGB)}GB (Model=${String.format("%.2f", modelSizeGB)}GB + 2GB Buffer)")
        } else {
            Timber.i(" [GATEKEEPER] LOAD APPROVED: Available=${String.format("%.2f", availableGB)}GB | Model=${String.format("%.2f", modelSizeGB)}GB")
        }
        
        return canLoad
    }
}
