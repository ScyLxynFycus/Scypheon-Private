package com.scypheon.sdk.core.environment

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SensorMesh: Provides unified environment awareness for all agents.
 * Pillar 4: Agentic Proactivity - Hardware & Environment Context.
 */
@Singleton
class SensorMesh @Inject constructor(
    private val context: Context
) {
    data class EnvironmentSnap(
        val batteryLevel: Int,
        val isCharging: Boolean,
        val lightLevel: Float?, // Needs to be hooked to LightSensor
        val networkType: String,
        val thermalStatus: String
    )

    fun getEnvironmentSnapshot(): EnvironmentSnap {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL

        // For now, placeholder for sensor-specific logic
        return EnvironmentSnap(
            batteryLevel = level,
            isCharging = isCharging,
            lightLevel = null, 
            networkType = "WIFI/4G",
            thermalStatus = "NORMAL"
        )
    }

    fun getContextString(): String {
        val snap = getEnvironmentSnapshot()
        return "[ENVIRONMENT: Battery ${snap.batteryLevel}%, Charging=${snap.isCharging}, Thermal=${snap.thermalStatus}]"
    }
}
