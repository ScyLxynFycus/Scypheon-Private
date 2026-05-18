package com.scypheon.app.data.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.scypheon.sdk.core.safety.AiResourceGovernor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AndroidResourceGovernor: API-Compatible, Event-Driven Hardware Monitor.
 * Uses ACTION_BATTERY_CHANGED to monitor temperature and levels without polling.
 */
@Singleton
class AndroidResourceGovernor @Inject constructor(
    @ApplicationContext private val context: Context
) : AiResourceGovernor {

    private val _state = MutableStateFlow(AiResourceGovernor.DeviceState.NORMAL)
    override val state: StateFlow<AiResourceGovernor.DeviceState> = _state

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // Temperature in tenths of a degree Celsius (380 = 38.0C)
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100 / scale) else 100

            _state.value = when {
                // Enterprise: Critical thresholds for disaster zones
                temp > 45.0f || pct < 10 -> AiResourceGovernor.DeviceState.CRITICAL
                temp > 38.0f || pct < 20 -> AiResourceGovernor.DeviceState.THROTTLED
                else -> AiResourceGovernor.DeviceState.NORMAL
            }
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Note: ACTION_BATTERY_CHANGED is a sticky broadcast, no receiver exported flag needed for standard usage
        // but adding it for modern Android compliance if targeting higher APIs.
        context.registerReceiver(receiver, filter)
    }

    override fun close() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
