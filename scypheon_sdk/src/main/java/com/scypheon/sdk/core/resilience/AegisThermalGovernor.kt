package com.scypheon.sdk.core.resilience

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class ThermalLevel {
    NORMAL,     // < 45C
    WARNING,    // 45C - 47C
    SEVERE,     // 48C - 49C (Red Warning)
    CRITICAL    // >= 50C (Auto-Shutdown Inference)
}

/**
 * AegisThermalGovernor: Protects device hardware from thermal runaway during AI inference.
 * 
 * [v1.5.0-SAR] GC-Hardened: Caches IntentFilter and uses adaptive polling intervals
 * to eliminate the ~160KB/2s GC churn caused by per-loop sticky broadcast registration.
 *
 * Thresholds:
 * - 45C: Warning (Yellow)
 * - 48C: Severe (Red)
 * - 50C: Critical (Kill Engine)
 */
@Singleton
class AegisThermalGovernor @Inject constructor() {

    private val _thermalStatus = MutableStateFlow(ThermalLevel.NORMAL)
    val thermalStatus = _thermalStatus.asStateFlow()

    private val _currentTemperature = MutableStateFlow(0f)
    val currentTemperature = _currentTemperature.asStateFlow()

    private var job: Job? = null

    // [v1.5.0-SAR] Cached IntentFilter — allocated once, reused forever.
    // Previously, a new IntentFilter + Intent was created every 2s, causing 
    // "Explicit concurrent copying GC freed ~160KB" spam in logcat.
    private val batteryIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    fun startMonitoring(context: Context, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val temp = getBatteryTemperature(context)
                _currentTemperature.emit(temp)
                
                val level = when {
                    temp >= 50f -> ThermalLevel.CRITICAL
                    temp >= 48f -> ThermalLevel.SEVERE
                    temp >= 45f -> ThermalLevel.WARNING
                    else -> ThermalLevel.NORMAL
                }

                if (level != _thermalStatus.value) {
                    _thermalStatus.emit(level)
                    Timber.w("🛡️ [AEGIS] Thermal Level Shift: $level (${temp}C)")
                }
                
                // [v1.5.0-SAR] Adaptive polling: no need to hammer the battery service
                // every 2s when the device is cool. Ramp up when hot.
                val pollIntervalMs = when (level) {
                    ThermalLevel.CRITICAL -> 1000L  // Hot — check every 1s
                    ThermalLevel.SEVERE -> 2000L    // Warm — check every 2s
                    ThermalLevel.WARNING -> 3000L   // Mild — check every 3s
                    ThermalLevel.NORMAL -> 5000L    // Cool — check every 5s
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stopMonitoring() {
        job?.cancel()
        job = null
    }

    private fun getBatteryTemperature(context: Context): Float {
        // [v1.5.0-SAR] Reuses cached batteryIntentFilter to avoid per-call allocation.
        // registerReceiver(null, filter) with a sticky broadcast is the standard
        // non-allocating way to read battery state — the Intent returned is the
        // system's cached sticky broadcast, not a new object.
        val intent = context.registerReceiver(null, batteryIntentFilter)
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp.toFloat() / 10f // Battery temp is in tenths of a degree Celsius
    }
}
