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
import com.scypheon.sdk.core.engine.AssetExtractor
import com.scypheon.sdk.core.gateway.NeuralGateway
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Killer Feature: Deaf Environment Guardian (Google LiteRT Audio)
 * Constantly listens to the environment completely offline using the device's microphone.
 * Classifies dangerous/important sounds (sirens, breaking glass, baby crying) that a deaf person cannot hear.
 * Alerts the user immediately via aggressive vibrations and UI callbacks.
 */
class DeafEnvironmentGuardian(
    private val context: Context,
    private val onAlertTriggered: (String, String) -> Unit
) : com.scypheon.sdk.core.humanitarian.ScypheonAgent {
    private var audioClassifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var backgroundExecutor: ScheduledExecutorService? = null
    
    // Updated for MediaPipe 0.10.14+
    private var cachedAudioData: com.google.mediapipe.tasks.components.containers.AudioData? = null

    var isListening = false
        private set

    override fun warmUp() {
        if (audioClassifier != null) return
        Timber.i(" [SAR] Warming up DeafEnvironmentGuardian...")
        
        // 🛡️ [SBI] Stealth Model Discovery
        val registry = com.scypheon.sdk.core.utils.AssetExtractor.discoverModels(context)
        val modelPath = registry.memoryModel ?: "yamnet.tflite"
        
        initialize(modelPath)
    }

    override fun release() {
        Timber.i(" [SAR] Releasing DeafEnvironmentGuardian resources...")
        shutdown()
    }

    override fun isReady(): Boolean = audioClassifier != null

    // The threshold needed to trigger an emergency alert (e.g. 70% confidence)
    private val CONFIDENCE_THRESHOLD = 0.70f

    // Critical sounds to monitor from the YamNet/AudioSet taxonomy
    private val EMERGENCY_SOUNDS = mapOf(
        "Fire alarm" to AlertProfile("🔥 EMERGENCY: Fire Alarm!", HapticPattern.SOS),
        "Smoke detector" to AlertProfile("🔥 EMERGENCY: Smoke Detector!", HapticPattern.SOS),
        "Siren" to AlertProfile("🚨 WARNING: Siren Nearby!", HapticPattern.ALERT),
        "Glass" to AlertProfile("⚠️ ALERT: Breaking Glass!", HapticPattern.SHARP),
        "Crying, sobbing" to AlertProfile("👶 ALERT: Baby Crying.", HapticPattern.PULSE),
        "Knock" to AlertProfile("🚪 SOMEONE IS KNOCKING.", HapticPattern.TAP),
        "Dog" to AlertProfile("🐕 Dog barking.", HapticPattern.TAP)
    )

    enum class HapticPattern {
        SOS,    // SOS Pattern
        ALERT,  // Triple pulse
        SHARP,  // Double sharp tap
        PULSE,  // Long soft pulse
        TAP     // Double tap
    }

    data class AlertProfile(val message: String, val pattern: HapticPattern)

    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 6000L // Don't spam the same alert every second

    fun initialize(modelPath: String) {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
            
            val internalPath = com.scypheon.sdk.core.utils.AssetExtractor.getModelPath(context, modelPath)
            if (internalPath.isNotEmpty() && internalPath.startsWith("/")) {
                Timber.i(" [SAR] Loading Stealth Model from internal storage: $internalPath")
                val file = java.io.File(internalPath)
                val fis = java.io.FileInputStream(file)
                try {
                    val channel = fis.channel
                    val buffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
                    baseOptionsBuilder.setModelAssetBuffer(buffer)
                } finally {
                    fis.close()
                }
            } else {
                Timber.i(" [SAR] Loading Model from assets: $modelPath")
                baseOptionsBuilder.setModelAssetPath(modelPath)
            }

            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(com.google.mediapipe.tasks.audio.core.RunningMode.AUDIO_STREAM)
                .setMaxResults(3) // Only care about the top 3 loudest sounds
                .build()

            audioClassifier = AudioClassifier.createFromOptions(context, options)
            Timber.i("✅ DeafEnvironmentGuardian initialized with offline LiteRT AudioClassifier.")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to init AudioClassifier")
        }
    }

    /**
     * Starts continuous offline audio sampling and classification.
     */
    fun startListening() {
        if (isListening || audioClassifier == null) return

        try {
            // MediaPipe 0.10.14 AudioRecord wrapper
            audioRecord = audioClassifier?.createAudioRecord()

            // Initialize the cached AudioData container
            if (cachedAudioData == null) {
                val format = com.google.mediapipe.tasks.components.containers.AudioData.AudioDataFormat.builder()
                    .setNumOfChannels(1)
                    .setSampleRate(16000f)
                    .build()
                cachedAudioData = com.google.mediapipe.tasks.components.containers.AudioData.create(format, 16000)
            }

            audioRecord?.startRecording()
            isListening = true

            // Run classification loop on a background thread
            backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
            backgroundExecutor?.scheduleAtFixedRate({
                classifyAudioBuffer()
            }, 0, 500, TimeUnit.MILLISECONDS) // Classify every 500ms

            Timber.i("🎧 Guardian is now actively monitoring the environment.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AudioRecord")
            isListening = false
        }
    }

    private fun classifyAudioBuffer() {
        val audioDataContainer = cachedAudioData ?: return
        
        // Load the hardware audio buffer into the MediaPipe container
        audioDataContainer.load(audioRecord)

        // ML Inference
        val result: AudioClassifierResult? = audioClassifier?.classify(audioDataContainer)

        // AudioClassifierResult.classificationResults() -> List<ClassificationResult>
        // ClassificationResult.classifications() -> List<Classifications>
        // Classifications.categories() -> List<Category>
        result?.classificationResults()?.firstOrNull()?.classifications()?.firstOrNull()?.categories()?.forEach { category ->
            val label = category.categoryName()
            val score = category.score()

            // If the sound is in our emergency list and the AI is confident
            if (score >= CONFIDENCE_THRESHOLD && EMERGENCY_SOUNDS.containsKey(label)) {
                triggerEmergencyProtocol(label, EMERGENCY_SOUNDS[label]!!)
            }
        }
    }

    private fun triggerEmergencyProtocol(label: String, profile: AlertProfile) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN_MS) return

        lastAlertTime = currentTime
        Timber.w("🚨 GUARDIAN ALERT: $label (Confidence high)")

        // 1. Vibrate with nuanced pattern
        vibrateDevice(profile.pattern)

        // 2. Send the message back to the UI (Chat Screen)
        onAlertTriggered(label, profile.message)
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
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            Timber.e("Failed to vibrate device: ${e.message}")
        }
    }

    fun stopListening() {
        isListening = false
        backgroundExecutor?.shutdownNow()
        audioRecord?.stop()
        Timber.i("🛑 Guardian monitoring disabled.")
    }

    fun shutdown() {
        stopListening()
        audioClassifier?.close()
        audioClassifier = null
    }
}
