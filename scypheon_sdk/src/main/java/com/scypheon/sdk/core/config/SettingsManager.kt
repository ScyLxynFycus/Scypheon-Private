package com.scypheon.sdk.core.config

import android.content.SharedPreferences
import com.scypheon.sdk.core.agent.ooda.*
import javax.inject.Inject
import javax.inject.Singleton

data class OrientationConfig(
    val lowBatteryThreshold: Int,
    val thermalCriticalStatus: ThermalStatus,
    val deepReasoningComplexityThreshold: ComplexityThreshold
)

@Singleton
class SettingsManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    fun getOrientationConfig(): OrientationConfig = OrientationConfig(
        lowBatteryThreshold = prefs.getInt("battery_threshold", 15),
        thermalCriticalStatus = ThermalStatus.valueOf(prefs.getString("thermal_status", "CRITICAL") ?: "CRITICAL"),
        deepReasoningComplexityThreshold = ComplexityThreshold.valueOf(prefs.getString("complexity_threshold", "HIGH") ?: "HIGH")
    )

    fun updateConfig(key: String, value: Any) {
        prefs.edit().apply {
            when (value) {
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }.apply()
    }
}
