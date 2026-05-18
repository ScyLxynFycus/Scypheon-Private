package com.scypheon.sdk.core.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContinuousSpeechRecognizer — Streaming speech-to-text for Scypheon Live.
 * 
 * [v1.5.0-SAR] Unlike the one-shot RecognizerIntent used in the chat input,
 * this provides continuous listening with:
 * - Real-time partial results (show transcription as user speaks)
 * - Auto-restart after each utterance (continuous conversation)
 * - RMS dB forwarding for waveform visualization
 * - Silence detection (end-of-speech triggers inference)
 */
@Singleton
class ContinuousSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRestart = false

    // Callbacks
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onListeningStateChanged: ((Boolean) -> Unit)? = null

    private val recognitionIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Shorter silence detection for more responsive turn-taking
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Timber.d("🎤 [STT] Ready for speech")
            isListening = true
            onListeningStateChanged?.invoke(true)
        }

        override fun onBeginningOfSpeech() {
            Timber.d("🎤 [STT] User started speaking")
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged?.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Timber.d("🎤 [STT] User stopped speaking")
            isListening = false
            onListeningStateChanged?.invoke(false)
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                SpeechRecognizer.ERROR_AUDIO -> "audio_error"
                SpeechRecognizer.ERROR_CLIENT -> "client_error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "no_permission"
                SpeechRecognizer.ERROR_NETWORK -> "network_error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network_timeout"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                SpeechRecognizer.ERROR_SERVER -> "server_error"
                else -> "unknown_$error"
            }
            Timber.w("🎤 [STT] Error: $errorMsg")

            // Auto-restart on non-fatal errors (no_match, timeout = user was quiet)
            if (shouldRestart && error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT
                )
            ) {
                restartListening()
            } else if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                onError?.invoke(errorMsg)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val finalText = matches?.firstOrNull()?.trim()

            if (!finalText.isNullOrBlank()) {
                Timber.i("🎤 [STT] Final: \"$finalText\"")
                onFinalResult?.invoke(finalText)
            }

            // Auto-restart for continuous listening
            // Note: We DON'T restart immediately here — the orchestrator 
            // will call startListening() again after AI finishes speaking
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()?.trim()

            if (!partialText.isNullOrBlank()) {
                onPartialResult?.invoke(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Initialize the speech recognizer.
     * Must be called on the main thread.
     */
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Timber.e("🎤 [STT] Speech recognition not available on this device")
            return
        }

        release()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        Timber.i("🎤 [STT] ContinuousSpeechRecognizer initialized")
    }

    /**
     * Start listening for speech.
     */
    fun startListening() {
        shouldRestart = true
        try {
            speechRecognizer?.startListening(recognitionIntent)
            Timber.d("🎤 [STT] Listening started")
        } catch (e: Exception) {
            Timber.e(e, "🎤 [STT] Failed to start listening")
        }
    }

    /**
     * Stop listening (pause, not destroy).
     */
    fun stopListening() {
        shouldRestart = false
        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Timber.e(e, "🎤 [STT] Failed to stop listening")
        }
    }

    private fun restartListening() {
        if (!shouldRestart) return
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(recognitionIntent)
            Timber.d("🎤 [STT] Auto-restarted listening")
        } catch (e: Exception) {
            Timber.e(e, "🎤 [STT] Failed to restart listening")
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        shouldRestart = false
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Timber.e(e, "🎤 [STT] Failed to release")
        }
    }
}
