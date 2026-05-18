package com.scypheon.sdk.core.engine

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.scypheon.sdk.core.annotations.SafetyCritical
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@SafetyCritical
@Singleton
class InferenceGovernor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class GovernorState {
        object Allow : GovernorState()
        data class Throttle(val reason: String, val maxTokens: Int) : GovernorState()
        data class Block(val reason: String) : GovernorState()
    }

    private val _state = MutableStateFlow<GovernorState>(GovernorState.Allow)
    val state = _state.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun evaluate(): GovernorState {
        val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        } ?: 100f

        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        // 1. Thermal Throttling (API 29+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val thermalStatus = powerManager.currentThermalStatus
            when {
                thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> {
                    _state.value = GovernorState.Block("Device Overheating (Critical)")
                    return _state.value
                }
                thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> {
                    _state.value = GovernorState.Throttle("Thermal Pressure (Moderate)", 32)
                    return _state.value
                }
            }
        }

        // 2. Power Save Mode
        if (powerManager.isPowerSaveMode) {
             _state.value = GovernorState.Throttle("Power Save Mode", 128)
             return _state.value
        }

        // 3. Battery Guard (Ignore if charging)
        if (!isCharging && batteryLevel < 15f) {
            _state.value = GovernorState.Throttle("Battery Critical ($batteryLevel%)", 64)
            return _state.value
        }

        _state.value = GovernorState.Allow
        return _state.value
    }
}

