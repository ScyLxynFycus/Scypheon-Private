package com.scypheon.sdk.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import timber.log.Timber
import java.util.*

/**
 * AegisVoiceEngine: The local "Voicebox" for Scypheon.
 * Wraps Android native TTS to provide off-line speech synthesis for AI responses.
 */
class AegisVoiceEngine(context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set language to US/UK English or Indonesian based on system locale
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Timber.e("TTS: Language not supported or missing data.")
                } else {
                    isInitialized = true
                    Timber.i("TTS: Aegis Voice Engine Online.")
                }
            } else {
                Timber.e("TTS: Initialization failed.")
            }
        }
    }

    /**
     * Speaks the provided text if TTS is ready.
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Timber.w("TTS: Voice engine not ready yet.")
            return
        }

        // Clean text from protocol tokens if any remain
        val cleanText = text.replace(Regex("<[^>]*>"), "")
        
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "Aegis_Inference_Speech")
        Timber.v("TTS: Speaking response.")
    }

    /**
     * Stops any ongoing speech.
     */
    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
