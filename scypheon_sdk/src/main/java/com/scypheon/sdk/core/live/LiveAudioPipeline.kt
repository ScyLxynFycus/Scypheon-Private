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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * LiveAudioPipeline: Continuous ambient audio awareness for Scypheon Live Mode.
 *
 * Provides real-time audio level monitoring for waveform visualization, Voice
 * Activity Detection (VAD) for turn-taking, and ambient context classification.
 *
 * Thread-safety:
 * - [isRecording] uses [AtomicBoolean] for lock-free read from any thread.
 * - [lifecycleMutex] serializes [start]/[stop] to prevent AudioRecord conflicts.
 * - [analysisJob] is always accessed under [lifecycleMutex] guard.
 *
 * Resource management:
 * - Implements [Closeable] for deterministic cleanup via ViewModel.onCleared().
 * - [stop] awaits pending analysis coroutine completion before releasing AudioRecord.
 * - State is reset only after hardware resources are fully released.
 */
@Singleton
class LiveAudioPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareLeakDetector: com.scypheon.sdk.core.telemetry.HardwareLeakDetector
) : Closeable {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // Voice Activity Detection thresholds
        private const val VAD_SPEECH_THRESHOLD_DB = -30f
        private const val VAD_SILENCE_THRESHOLD_DB = -45f
        private const val SILENCE_DURATION_MS = 1500L
    }

    // ═════════════════════════════════════════════════════════════════
    // State — all observable state is exposed as StateFlow
    // ═════════════════════════════════════════════════════════════════

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var analysisJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serializes [start]/[stop] calls to prevent AudioRecord resource conflicts.
     * Without this, a rapid stop→start sequence can attempt to create a new
     * AudioRecord while the old one is still being released.
     */
    private val lifecycleMutex = Mutex()

    /** Normalized audio level (0.0 - 1.0) for UI waveform rendering. */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    /** Raw dB level (-60 to 0) for signal processing. */
    private val _audioDb = MutableStateFlow(-60f)
    val audioDb: StateFlow<Float> = _audioDb.asStateFlow()

    /** True when speech is actively detected above the VAD threshold. */
    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    /** Composite ambient context for injection into LLM system prompt. */
    private val _ambientContext = MutableStateFlow<AmbientContext>(AmbientContext.quiet())
    val ambientContext: StateFlow<AmbientContext> = _ambientContext.asStateFlow()

    // Callbacks
    var onSpeechStart: (() -> Unit)? = null
    var onSpeechEnd: (() -> Unit)? = null
    var onAudioLevel: ((Float) -> Unit)? = null

    // VAD internals
    private var lastSpeechTime = 0L
    private var wasSpeaking = false

    // ═════════════════════════════════════════════════════════════════
    // Lifecycle — serialized via Mutex
    // ═════════════════════════════════════════════════════════════════

    /**
     * Starts audio capture and continuous analysis.
     *
     * This method is idempotent — calling it while already recording is a no-op.
     * Waits for any pending [stop] operation to complete before acquiring the
     * microphone resource.
     */
    fun start() {
        if (isRecording.get()) return

        scope.launch {
            lifecycleMutex.withLock {
                if (isRecording.get()) return@launch

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    Timber.e("[AUDIO] RECORD_AUDIO permission not granted")
                    return@launch
                }

                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Timber.e("[AUDIO] Invalid buffer size: $bufferSize")
                    return@launch
                }

                try {
                    val record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL,
                        ENCODING,
                        bufferSize * 2
                    )

                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        Timber.e("[AUDIO] AudioRecord failed to initialize")
                        record.release()
                        return@launch
                    }

                    audioRecord = record
                    record.startRecording()
                    isRecording.set(true)
                    hardwareLeakDetector.reportMicStart()

                    analysisJob = scope.launch {
                        val buffer = ShortArray(bufferSize / 2)
                        while (isActive && isRecording.get()) {
                            val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                            if (readCount > 0) {
                                processAudioBuffer(buffer, readCount)
                            } else if (readCount < 0) {
                                Timber.e("[AUDIO] Read error: $readCount")
                                break
                            } else {
                                yield()
                            }
                        }
                    }

                    Timber.i("[AUDIO] LiveAudioPipeline started (${SAMPLE_RATE}Hz)")
                } catch (e: SecurityException) {
                    Timber.e(e, "[AUDIO] Security exception — permission revoked at runtime")
                } catch (e: Exception) {
                    Timber.e(e, "[AUDIO] Failed to start audio pipeline")
                }
            }
        }
    }

    /**
     * Stops audio capture, awaits analysis completion, and releases hardware.
     * Guaranteed atomic teardown via NonCancellable context.
     */
    suspend fun stop() = withContext(NonCancellable) {
        if (!isRecording.compareAndSet(true, false)) return@withContext

        lifecycleMutex.withLock {
            try {
                analysisJob?.cancelAndJoin()
                analysisJob = null

                audioRecord?.let { record ->
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Timber.w(e, "[AUDIO] AudioRecord.stop() failed — already stopped")
                    }
                    record.release()
                }
                audioRecord = null
            } catch (e: Exception) {
                Timber.e(e, "[AUDIO] Error during pipeline shutdown")
            } finally {
                // State reset happens AFTER hardware release — not before
                resetState()
                hardwareLeakDetector.reportMicStop()
                Timber.i("[AUDIO] LiveAudioPipeline stopped")
            }
        }
    }

    /**
     * Deterministic resource release. Called from ViewModel.onCleared() or
     * when the hosting lifecycle is destroyed.
     */
    override fun close() {
        runBlocking(NonCancellable) {
            stop()
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // Audio Analysis
    // ═════════════════════════════════════════════════════════════════

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
                Timber.d("[VAD] Speech START (${clampedDb}dB)")
            }
        } else if (wasSpeaking && (now - lastSpeechTime > SILENCE_DURATION_MS)) {
            wasSpeaking = false
            _isSpeechDetected.value = false
            onSpeechEnd?.invoke()
            Timber.d("[VAD] Speech END (silence for ${SILENCE_DURATION_MS}ms)")
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

    private fun resetState() {
        _audioLevel.value = 0f
        _audioDb.value = -60f
        _isSpeechDetected.value = false
        wasSpeaking = false
    }

    // ═════════════════════════════════════════════════════════════════
    // Data Classes
    // ═════════════════════════════════════════════════════════════════

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
