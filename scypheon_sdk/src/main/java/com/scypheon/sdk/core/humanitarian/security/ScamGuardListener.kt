package com.scypheon.sdk.core.humanitarian.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import timber.log.Timber

/**
 * Separate SpeechRecognizer instance dedicated to listening to phone calls.
 * Feeds transcripts to ScamGuard.
 */
class ScamGuardListener(
    private val context: Context,
    private val scamGuard: ScamGuard
) {
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    init {
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { Timber.i("ScamGuard: Listening to call...") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (isListening) {
                        // Continuous listening loop for a phone call
                        startListening()
                    }
                }
                override fun onError(error: Int) {
                    Timber.e("ScamGuard speech recognition error code: $error")
                    if (isListening) {
                        startListening()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val callText = matches[0]
                        Timber.i("ScamGuard Call Transcript: $callText")
                        scamGuard.processConversationTranscript(callText)
                    }
                    if (isListening) {
                        startListening()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val callText = matches[0]
                        scamGuard.processConversationTranscript(callText)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Timber.e("Native Speech Recognition not available on this device for ScamGuard")
        }
    }

    fun startListening() {
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
    }

    fun shutdown() {
        stopListening()
        speechRecognizer?.destroy()
    }
}
