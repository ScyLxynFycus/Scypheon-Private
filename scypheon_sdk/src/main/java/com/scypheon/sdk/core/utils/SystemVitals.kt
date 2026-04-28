package com.scypheon.sdk.core.utils

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import timber.log.Timber

/**
 * SystemVitals provides a real-time audit of device health metrics.
 * It replaces static blacklists with dynamic, evidence-based triage.
 */
object SystemVitals {

    enum class HealthStatus {
        OPTIMAL,  // GPU recommended
        STRESSED, // OpenCL or CPU recommended
        CRITICAL  // CPU only
    }

    data class AuditReport(
        val status: HealthStatus,
        val availableRamMB: Long,
        val thermalStatus: Int,
        val isPowerSaveMode: Boolean,
        val reason: String
    )

    /**
     * Performs a comprehensive diagnostic scan.
     */
    fun performAudit(context: Context, modelSizeMB: Long): AuditReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        
        val availableMB = memInfo.availMem / (1024 * 1024)
        val thermal = pm.currentThermalStatus
        val powerSave = pm.isPowerSaveMode
        
        // 🛡️ SFE Safety Buffers:
        // CPU Load: Model Size + 25% overhead (KV Cache, process overhead)
        // GPU Load: Model Size + 50% overhead (Vulkan VRAM overhead)
        val cpuRequired = (modelSizeMB * 1.25).toLong()
        val gpuRequired = (modelSizeMB * 1.50).toLong()
        
        return when {
            availableMB < cpuRequired -> {
                AuditReport(HealthStatus.CRITICAL, availableMB, thermal, powerSave, "CRITICAL: RAM ($availableMB MB) below CPU safety floor ($cpuRequired MB)")
            }
            availableMB < gpuRequired || thermal >= PowerManager.THERMAL_STATUS_MODERATE || powerSave -> {
                val reason = if (availableMB < gpuRequired) "Low RAM for GPU" 
                            else if (powerSave) "Battery Saver Mode"
                            else "Thermal Throttling (Level $thermal)"
                AuditReport(HealthStatus.STRESSED, availableMB, thermal, powerSave, "STRESSED: $reason")
            }
            else -> {
                AuditReport(HealthStatus.OPTIMAL, availableMB, thermal, powerSave, "OPTIMAL: All systems green")
            }
        }
    }

    /**
     * Determines if the device should attempt GPU acceleration.
     */
    fun shouldAttemptGPU(report: AuditReport): Boolean {
        return report.status != HealthStatus.CRITICAL
    }
}
