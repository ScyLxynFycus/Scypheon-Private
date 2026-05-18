package com.scypheon.sdk.core.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * LiveAudioPipeline — Continuous Ambient Audio Awareness for Scypheon Live.
 * 
 * [v1.5.0-SAR] Makes the AI "hear" the environment continuously.
 * 
 * Two capabilities:
 * 1. RMS audio level monitoring (for waveform visualization + VAD)
 * 2. Ambient sound context (can be extended with YAMNet classification)
 * 
 * The audio level is used for:
 * - Waveform animation on the orb
 * - Voice Activity Detection (detect when user starts/stops talking)
 * - Silence detection (trigger end-of-turn after N seconds of silence)
 */
@Singleton
class LiveAudioPipeline @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        
        // Voice Activity Detection thresholds
        private const val VAD_SPEECH_THRESHOLD_DB = -30f   // Above this = speech
        private const val VAD_SILENCE_THRESHOLD_DB = -45f   // Below this = silence
        private const val SILENCE_DURATION_MS = 1500L       // 1.5s of silence = end of turn
    }

    // ═══════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var analysisJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Audio level (0.0 - 1.0, normalized for UI)
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Raw dB level
    private val _audioDb = MutableStateFlow(-60f)
    val audioDb: StateFlow<Float> = _audioDb.asStateFlow()

    // VAD state
    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    // Ambient context
    private val _ambientContext = MutableStateFlow<AmbientContext>(AmbientContext.quiet())
    val ambientContext: StateFlow<AmbientContext> = _ambientContext.asStateFlow()

    // Callbacks
    var onSpeechStart: (() -> Unit)? = null
    var onSpeechEnd: (() -> Unit)? = null
    var onAudioLevel: ((Float) -> Unit)? = null

    // VAD internals
    private var lastSpeechTime = 0L
    private var wasSpeaking = false

    // ═══════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════

    fun start() {
        if (isRecording) return
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Timber.e("🔊 [AUDIO] RECORD_AUDIO permission not granted")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Timber.e("🔊 [AUDIO] Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("🔊 [AUDIO] AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            // Launch continuous audio analysis
            analysisJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                
                while (isActive && isRecording) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readCount > 0) {
                        processAudioBuffer(buffer, readCount)
                    }
                }
            }

            Timber.i("🔊 [AUDIO] LiveAudioPipeline started (${SAMPLE_RATE}Hz)")
        } catch (e: SecurityException) {
            Timber.e(e, "🔊 [AUDIO] Security exception — permission denied")
        } catch (e: Exception) {
            Timber.e(e, "🔊 [AUDIO] Failed to start audio pipeline")
        }
    }

    fun stop() {
        isRecording = false
        analysisJob?.cancel()
        analysisJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Timber.e(e, "🔊 [AUDIO] Error stopping pipeline")
        }

        _audioLevel.value = 0f
        _audioDb.value = -60f
        _isSpeechDetected.value = false
        wasSpeaking = false
        Timber.i("🔊 [AUDIO] LiveAudioPipeline stopped")
    }

    // ═══════════════════════════════════════════════════════════════
    // Audio Analysis
    // ═══════════════════════════════════════════════════════════════

    private fun processAudioBuffer(buffer: ShortArray, readCount: Int) {
        // Calculate RMS (Root Mean Square) for audio level
        var sum = 0.0
        for (i in 0 until readCount) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        val rms = sqrt(sum / readCount)
        
        // Convert to dB (with floor to prevent -Infinity)
        val db = if (rms > 0) (20 * log10(rms / 32768.0)).toFloat() else -60f
        val clampedDb = db.coerceIn(-60f, 0f)
        
        // Normalize to 0-1 for UI (map -60dB..0dB to 0..1)
        val normalizedLevel = ((clampedDb + 60f) / 60f).coerceIn(0f, 1f)
        
        _audioDb.value = clampedDb
        _audioLevel.value = normalizedLevel
        onAudioLevel?.invoke(normalizedLevel)

        // Voice Activity Detection
        val now = System.currentTimeMillis()
        val isSpeaking = clampedDb > VAD_SPEECH_THRESHOLD_DB

        if (isSpeaking) {
            lastSpeechTime = now
            if (!wasSpeaking) {
                wasSpeaking = true
                _isSpeechDetected.value = true
                onSpeechStart?.invoke()
                Timber.d("🔊 [VAD] Speech START (${clampedDb}dB)")
            }
        } else if (wasSpeaking && (now - lastSpeechTime > SILENCE_DURATION_MS)) {
            wasSpeaking = false
            _isSpeechDetected.value = false
            onSpeechEnd?.invoke()
            Timber.d("🔊 [VAD] Speech END (silence for ${SILENCE_DURATION_MS}ms)")
        }

        // Update ambient context
        _ambientContext.value = AmbientContext(
            audioLevelDb = clampedDb,
            audioLevelNormalized = normalizedLevel,
            isSpeechActive = wasSpeaking,
            noiseLevel = when {
                clampedDb > -15f -> NoiseLevel.LOUD
                clampedDb > -30f -> NoiseLevel.MODERATE
                clampedDb > -45f -> NoiseLevel.QUIET
                else -> NoiseLevel.SILENT
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════

    enum class NoiseLevel { SILENT, QUIET, MODERATE, LOUD }

    data class AmbientContext(
        val audioLevelDb: Float,
        val audioLevelNormalized: Float,
        val isSpeechActive: Boolean,
        val noiseLevel: NoiseLevel
    ) {
        companion object {
            fun quiet() = AmbientContext(-60f, 0f, false, NoiseLevel.SILENT)
        }

        fun toContextString(): String {
            return "Ambient: ${noiseLevel.name.lowercase()} environment" +
                if (isSpeechActive) ", user is speaking" else ""
        }
    }
}
