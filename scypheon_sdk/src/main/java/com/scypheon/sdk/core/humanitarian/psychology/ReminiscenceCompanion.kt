package com.scypheon.sdk.core.humanitarian.psychology

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.memory.DualMemoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * Humanitarian Impact Feature: Elderly Reminiscence Therapy Companion
 * Designed to combat loneliness and mild dementia in the elderly completely offline.
 * Acts as an empathetic, patient listener that prompts memories, validates emotions,
 * and extracts significant biographical facts into the SQLite Vector DB (RAG) to remember them forever.
 */
class ReminiscenceCompanion(
    private val context: Context,
    private val gateway: NeuralGateway,
    private val memoryManager: DualMemoryManager,
    private val onMessageGenerated: (String) -> Unit
) : TextToSpeech.OnInitListener, com.scypheon.sdk.core.humanitarian.ScypheonAgent {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false
    
    var isListening = false
        private set

    override fun warmUp() {
        if (isInitialized) return
        Timber.i(" [SAR] Warming up ReminiscenceCompanion (Initializing TTS/STT)...")
        tts = TextToSpeech(context, this)
        setupSpeechRecognizer()
        isInitialized = true
    }

    override fun release() {
        Timber.i(" [SAR] Releasing ReminiscenceCompanion resources...")
        shutdown()
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("id", "ID")) // Default to Indonesian for empathy
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val story = matches[0]
                        processElderlyStory(story)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        if (isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
        isListening = true
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    /**
     * Start the therapy session by introducing a gentle memory prompt.
     */
    fun initiateTherapySession() {
        val opening = "Halo... Saya di sini untuk menemani Anda. Maukah Anda menceritakan sebuah kenangan yang paling indah saat Anda masih kecil?"
        speakAndNotify(opening)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun processElderlyStory(story: String) {
        onMessageGenerated("🗣️ Oma/Opa: \"$story\"")

        scope.launch {
            // 1. Retrieve any past context about them using vector search
            val historicalContext = memoryManager.searchSimilarMemories(story, 2).joinToString(" ")
            
            val prompt = """
                You are a patient, warm, and highly empathetic companion for an elderly person undergoing reminiscence therapy.

                Past known context about them: "$historicalContext"
                They just told you this story: "$story"

                Task 1 (Empathy): Reply to their story warmly. Validate their feelings. Ask one gentle follow-up question to keep them talking.
                Task 2 (Memory Extraction): If they mentioned a new important biographical fact (e.g., a family member's name, a past job, a favorite food), write it at the very end of your response exactly like this: [FACT: "loves eating mangoes"] or [FACT: "grandson named Budi"]. If no new fact, do not write the tag.

                Keep your main reply short, conversational, and in Indonesian (Bahasa Indonesia). Do not use markdown.
            """.trimIndent()

            val aiResponse = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }

            // Extract the fact to save to the Vector DB
            val factRegex = Regex("\\[FACT: (.*?)\\]")
            val match = factRegex.find(aiResponse)

            var cleanResponse = aiResponse
            if (match != null) {
                val newFact = match.groupValues[1]
                Timber.i("🧠 Extracted new elderly fact: $newFact")

                // Save fact securely into SQLite BLOB vector storage
                // We use session "profile" or saveMessage to index it for semantic retrieval later
                memoryManager.saveMessage("biography", "Important Fact: $newFact", false)

                // Remove the fact tag so the TTS doesn't speak it out loud
                cleanResponse = aiResponse.replace(match.value, "").trim()
            }

            speakAndNotify(cleanResponse)
        }
    }

    private fun speakAndNotify(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "Reminiscence")
        onMessageGenerated("🤖 Companion: $message")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
