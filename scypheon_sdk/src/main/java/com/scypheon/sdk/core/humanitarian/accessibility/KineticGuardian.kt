package com.scypheon.sdk.core.humanitarian.accessibility

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.speech.tts.TextToSpeech
import com.scypheon.sdk.core.memory.DualMemoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import kotlin.math.sqrt

/**
 * Enterprise Humanitarian Feature: Context-Aware Sensory Fusion (Fall Detection).
 * Transforms the AI from a mere chatbot into a physical, environmental wingman.
 * Monitors Accelerometer (G-Force) and Ambient Light to detect if an elderly user has fallen
 * and potentially passed out in the dark.
 */
class KineticGuardian(
    private val context: Context,
    private val memoryManager: DualMemoryManager,
    private val onEmergencyTriggered: (String, String) -> Unit
) : SensorEventListener, TextToSpeech.OnInitListener, com.scypheon.sdk.core.humanitarian.ScypheonAgent {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private var tts: TextToSpeech? = null

    private var isMonitoring = false
    private var currentLux = -1f

    // Fall Detection Constants
    private val FALL_THRESHOLD_GRAVITY = 2.5f // 2.5G spike is a hard fall
    private var lastFallTime = 0L
    private val FALL_COOLDOWN_MS = 10000L // 10 seconds cooldown between alerts

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun warmUp() {
        if (tts != null) return
        Timber.i(" [SAR] Warming up KineticGuardian...")
        tts = TextToSpeech(context, this)
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    override fun release() {
        Timber.i(" [SAR] Releasing KineticGuardian resources...")
        stopMonitoring()
        tts?.stop()
        tts?.shutdown()
        tts = null
        sensorManager = null
    }

    override fun isReady(): Boolean = tts != null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("id", "ID"))
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return

        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        isMonitoring = true
        Timber.i("🛡️ KineticGuardian (Sensory Fusion) is now actively monitoring physical physics.")
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        sensorManager?.unregisterListener(this)
        isMonitoring = false
        Timber.i("🛑 KineticGuardian monitoring disabled.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                currentLux = event.values[0]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Calculate the total G-Force magnitude
                val gX = x / SensorManager.GRAVITY_EARTH
                val gY = y / SensorManager.GRAVITY_EARTH
                val gZ = z / SensorManager.GRAVITY_EARTH

                val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

                if (gForce > FALL_THRESHOLD_GRAVITY) {
                    handlePotentialFall(gForce)
                }
            }
        }
    }

    private fun handlePotentialFall(gForce: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFallTime < FALL_COOLDOWN_MS) return

        lastFallTime = currentTime
        val isDark = currentLux in 0f..5f // Under 5 lux is pitch black

        val environmentContext = if (isDark) "in a dark area" else "in a bright room"
        Timber.e("🚨 CRITICAL: Fall Detected! G-Force: $gForce. Environment: $environmentContext (Lux: $currentLux)")

        // 1. Save Sensory Snapshot to RAG (Episodic Memory)
        scope.launch {
            val memorySnapshot = "[KINETIC_TRAUMA] At ${java.util.Date()}: Patient experienced a ${gForce}G physical shock $environmentContext."
            memoryManager.saveMessage("medical_telemetry", memorySnapshot, isUser = false)
        }
        
        // --- EMERGENCY HAPTIC ALERT ---
        // Provides kinetic feedback for the hearing impaired (Inclusivity Theme)
        // vibrationNotifier.pulse(3) 

        // 2. Proactive Voice Check (TTS)
        val alertMessage = "Emergency Alert: I detected a heavy impact. Are you okay? Do you need urgent medical assistance?"
        tts?.speak(alertMessage, TextToSpeech.QUEUE_FLUSH, null, "FallDetection")

        // 3. Notify UI (GlobalLiveEventBus)
        val uiWarning = "⚠️ [KineticGuardian] Severe Physical Impact Detected ($gForce G) $environmentContext. Awaiting user response..."
        onEmergencyTriggered("Trauma", uiWarning)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for simple fall detection
    }
}
