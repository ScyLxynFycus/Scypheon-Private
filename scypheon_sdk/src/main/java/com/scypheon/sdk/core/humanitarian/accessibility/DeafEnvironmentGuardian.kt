package com.scypheon.sdk.core.humanitarian.accessibility

import android.content.Context
import android.media.AudioRecord
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.core.BaseOptions
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeafEnvironmentGuardian @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : com.scypheon.sdk.core.humanitarian.ScypheonAgent {
    
    private var audioClassifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var backgroundExecutor: ScheduledExecutorService? = null
    private var cachedAudioData: com.google.mediapipe.tasks.components.containers.AudioData? = null

    private var onAlertTriggered: ((String, String) -> Unit)? = null

    var isListening = false
        private set

    fun setOnAlertTriggeredListener(listener: (String, String) -> Unit) {
        this.onAlertTriggered = listener
    }

    override fun warmUp() {
        if (audioClassifier != null) return
        val modelPath = "yamnet.tflite"
        initialize(modelPath)
    }

    override fun release() {
        shutdown()
    }

    override fun isReady(): Boolean = audioClassifier != null

    private val CONFIDENCE_THRESHOLD = 0.70f

    private val EMERGENCY_SOUNDS = mapOf(
        "Fire alarm" to AlertProfile("🔥 EMERGENCY: Fire Alarm!", HapticPattern.SOS),
        "Smoke detector" to AlertProfile("🔥 EMERGENCY: Smoke Detector!", HapticPattern.SOS),
        "Siren" to AlertProfile("🚨 WARNING: Siren Nearby!", HapticPattern.ALERT),
        "Glass" to AlertProfile("⚠️ ALERT: Breaking Glass!", HapticPattern.SHARP),
        "Crying, sobbing" to AlertProfile("👶 ALERT: Baby Crying.", HapticPattern.PULSE),
        "Knock" to AlertProfile("🚪 SOMEONE IS KNOCKING.", HapticPattern.TAP),
        "Dog" to AlertProfile("🐕 Dog barking.", HapticPattern.TAP)
    )

    enum class HapticPattern { SOS, ALERT, SHARP, PULSE, TAP }
    data class AlertProfile(val message: String, val pattern: HapticPattern)

    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 6000L

    fun initialize(modelPath: String) {
        try {
            val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelPath)
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(com.google.mediapipe.tasks.audio.core.RunningMode.AUDIO_STREAM)
                .setMaxResults(3)
                .build()

            audioClassifier = AudioClassifier.createFromOptions(context, options)
            Timber.i("✅ DeafEnvironmentGuardian initialized.")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to init AudioClassifier")
        }
    }

    fun startListening() {
        if (isListening || audioClassifier == null) return
        try {
            audioRecord = audioClassifier?.createAudioRecord()
            if (cachedAudioData == null) {
                val format = com.google.mediapipe.tasks.components.containers.AudioData.AudioDataFormat.builder()
                    .setNumOfChannels(1)
                    .setSampleRate(16000f)
                    .build()
                cachedAudioData = com.google.mediapipe.tasks.components.containers.AudioData.create(format, 16000)
            }
            audioRecord?.startRecording()
            isListening = true
            backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
            backgroundExecutor?.scheduleAtFixedRate({ classifyAudioBuffer() }, 0, 500, TimeUnit.MILLISECONDS)
            Timber.i("🎧 Guardian is now actively monitoring.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AudioRecord")
            isListening = false
        }
    }

    private fun classifyAudioBuffer() {
        val audioDataContainer = cachedAudioData ?: return
        audioDataContainer.load(audioRecord)
        val result: AudioClassifierResult? = audioClassifier?.classify(audioDataContainer)
        result?.classificationResults()?.firstOrNull()?.classifications()?.firstOrNull()?.categories()?.forEach { category ->
            val label = category.categoryName()
            val score = category.score()
            if (score >= CONFIDENCE_THRESHOLD && EMERGENCY_SOUNDS.containsKey(label)) {
                triggerEmergencyProtocol(label, EMERGENCY_SOUNDS[label]!!)
            }
        }
    }

    private fun triggerEmergencyProtocol(label: String, profile: AlertProfile) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN_MS) return
        lastAlertTime = currentTime
        vibrateDevice(profile.pattern)
        onAlertTriggered?.invoke(label, profile.message)
    }

    private fun vibrateDevice(pattern: HapticPattern) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (timings, amplitudes) = when (pattern) {
                    HapticPattern.SOS -> longArrayOf(0, 200, 100, 200, 100, 200, 100, 600, 100, 600, 100, 600) to intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
                    HapticPattern.ALERT -> longArrayOf(0, 300, 100, 300, 100, 300) to intArrayOf(0, 255, 0, 255, 0, 255)
                    HapticPattern.SHARP -> longArrayOf(0, 100, 50, 100) to intArrayOf(0, 255, 0, 255)
                    HapticPattern.PULSE -> longArrayOf(0, 1000) to intArrayOf(0, 150)
                    HapticPattern.TAP -> longArrayOf(0, 50, 200, 50) to intArrayOf(0, 200, 0, 200)
                }
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            Timber.e("Vibration failed: ${e.message}")
        }
    }

    fun stopListening() {
        isListening = false
        backgroundExecutor?.shutdownNow()
        audioRecord?.stop()
    }

    fun shutdown() {
        stopListening()
        audioClassifier?.close()
        audioClassifier = null
    }
}
