package com.scypheon.sdk.core.environment

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * SensorMesh: Provides unified environment awareness for all agents.
 * Pillar 4: Agentic Proactivity - Hardware & Environment Context.
 */
@Singleton
class SensorMesh @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class EnvironmentSnap(
        val batteryLevel: Int,
        val isCharging: Boolean,
        val lightLevel: Float?, // Needs to be hooked to LightSensor
        val networkType: String,
        val thermalStatus: String
    )

    fun getEnvironmentSnapshot(): EnvironmentSnap {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(network)
        val networkType = when {
            caps == null -> "NONE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY -> "CRITICAL"
                PowerManager.THERMAL_STATUS_SEVERE, PowerManager.THERMAL_STATUS_MODERATE -> "WARM"
                else -> "NORMAL"
            }
        } else {
            "NORMAL"
        }

        return EnvironmentSnap(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            lightLevel = null,
            networkType = networkType,
            thermalStatus = thermalStatus
        )
    }

    private fun getNetworkType(): String {
        return getEnvironmentSnapshot().networkType
    }

    private fun getThermalStatus(): String {
        return getEnvironmentSnapshot().thermalStatus
    }

    fun getContextString(): String {
        val snap = getEnvironmentSnapshot()
        return "[ENVIRONMENT: Battery ${snap.batteryLevel}%, Charging=${snap.isCharging}, Thermal=${snap.thermalStatus}]"
    }
}
