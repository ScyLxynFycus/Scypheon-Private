package com.scypheon.sdk.core.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.ThermalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemMonitorImpl @Inject constructor(
    private val context: Context
) : com.scypheon.sdk.core.agent.SystemMonitor {
    private val batteryManager by lazy { context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager }
    private val connectivityManager by lazy { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    override suspend fun captureSnapshot(): DeviceEnvironment = withContext(Dispatchers.IO) {
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val networkType = resolveNetworkType()
        val thermalStatus = resolveThermalStatus()

        DeviceEnvironment(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            thermalStatus = thermalStatus,
            networkType = networkType
        )
    }

    private fun resolveNetworkType(): String {
        val network = connectivityManager.activeNetwork ?: return "NONE"
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return "NONE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    private fun resolveThermalStatus(): ThermalStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_SEVERE, PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.WARM
                else -> ThermalStatus.NORMAL
            }
        } else {
            ThermalStatus.NORMAL
        }
    }
}
