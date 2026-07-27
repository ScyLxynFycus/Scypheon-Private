package com.scypheon.sdk.core.humanitarian.education

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live Offline English Tutor powered by Gemma (MediaPipe).
 * Integrates Android's native offline Speech-to-Text and Text-to-Speech
 * to provide a continuous, voice-to-voice interactive language lesson.
 */
@Singleton
class LiveEnglishTutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmEngine: LiteRtEliteEngine,
    private val memoryManager: com.scypheon.sdk.core.memory.DualMemoryManager,
    private val sensoryHooks: dagger.Lazy<com.scypheon.sdk.core.gateway.SensoryHooks>,
    private val router: dagger.Lazy<com.scypheon.sdk.core.agent.SkillIntentRouter>,
    private val orchestrator: dagger.Lazy<com.scypheon.sdk.core.agent.skills.AgenticSkillOrchestrator>
) : TextToSpeech.OnInitListener, com.scypheon.sdk.core.humanitarian.ScypheonAgent {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false
    @Volatile private var isTtsReady = false
    
    var isListening = false
        private set

    override fun warmUp() {
        if (isInitialized) return
        Timber.i(" [SAR] Warming up LiveEnglishTutor (Initializing TTS/STT)...")
        isTtsReady = false
        tts = TextToSpeech(context, this)
        setupSpeechRecognizer()
        isInitialized = true
    }

    override fun release() {
        Timber.i(" [SAR] Releasing LiveEnglishTutor resources...")
        shutdown()
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Defaulting to English output for the tutor's voice
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.e("TTS: US English is not supported or missing data on this device.")
            } else {
                isTtsReady = true
                // Proactive: Trigger quiz after successful initialization
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(1000)
                    triggerPopQuiz()
                }
            }
        } else {
            Timber.e("TTS Initialization failed")
        }
    }

    /**
     * Guided Learning: Object-Naming Mode.
     * The tutor "sees" what the user is holding and teaches the English name.
     */
    fun startObjectNamingSession(imageUri: android.net.Uri) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            Timber.i("📖 [TUTOR] Sensory learning initiated...")
            val auditResult = sensoryHooks.get().performMultiModalAudit(imageUri)
            val prompt = """
                You are a proactive English Tutor. I have just performed a sensory audit of the student's environment.
                Result: $auditResult
                Identify one prominent object and teach the student how to pronounce it and use it in a sentence.
                Be encouraging and interactive.
            """.trimIndent()
            
            llmEngine.generateResponse(prompt).collect { response ->
                speakOut(response)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { Timber.i("Student started speaking...") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    Timber.i("Student finished speaking.")
                }
                override fun onError(error: Int) {
                    Timber.e("Speech recognition error code: $error")
                    isListening = false
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val userText = matches[0]
                        Timber.i("Recognized Student Speech: $userText")
                        processStudentInput(userText)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Timber.e("Native Speech Recognition not available on this device")
        }
    }

    /**
     * Starts listening to the student. Forces offline mode.
     */
    fun startListening() {
        if (isListening) return
        try {
            val systemLocale = Locale.getDefault().toLanguageTag()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Force offline recognition (Requires downloaded language pack on Android)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                // Default to Indonesian if system locale is not available
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (systemLocale.isNotBlank()) systemLocale else "id-ID")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start speech recognizer. Ensure the app has microphone permissions.")
            isListening = false
        }
    }

    /**
     * Spaced Repetition: Proactive Vocabulary Quiz.
     * Queries the Knowledge Graph for words the student struggles with and triggers a quiz.
     */
    fun triggerPopQuiz() {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val struggles = memoryManager.querySubject("Student")
                .filter { it.contains("struggles_with") }
                .map { it.substringAfter("struggles_with").trim() }
            
            if (struggles.isNotEmpty()) {
                val targetWord = struggles.shuffled().first()
                Timber.i("🧠 [TUTOR] Memory-based Pop Quiz triggered for word: $targetWord")
                
                val prompt = """
                    You are a proactive English Tutor.
                    The student has previously struggled with the word: "$targetWord".
                    Ask the student a question using this word to test their progress, or ask them to use it in a sentence.
                    Be encouraging.
                """.trimIndent()
                
                llmEngine.generateResponse(prompt).collect { response ->
                    speakOut(response)
                }
            }
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    internal var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal fun processStudentInput(input: String) {
        val sanitizedInput = input.trim()
        Timber.i("🧹 STT Input: $sanitizedInput")

        scope.launch {
            val routing = router.get().routeQuick(sanitizedInput, maxLatencyMs = 80)
            
            when {
                routing.skillType == com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.MEDICAL ||
                routing.skillType == com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.RESILIENCE -> {
                    // SAFETY ESCALATION: Full orchestrator, even if latency spikes
                    Timber.w("🚨 [TUTOR] Safety Escalation Triggered for query!")
                    val report = orchestrator.get().orchestrateMission("tutor_escalation", sanitizedInput)
                    speakOut(report.text)
                    return@launch
                }
                routing.skillType == com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.EDUCATION && routing.confidence > 0.92f -> {
                    // FAST PATH: Direct to tutor LLM
                    Timber.i("📖 [TUTOR] Fast-path routing active.")
                }
                else -> {
                    Timber.i("📖 [TUTOR] Standard generation active.")
                }
            }

            val prompt = """
                You are a helpful, encouraging offline English Tutor.
                A student just said: "$sanitizedInput"

                Your tasks:
                1. If it's in Indonesian, translate it. If it's broken English, correct the grammar gently.
                2. Explain the meaning clearly.
                3. Provide 2 simple examples of how to use it in a sentence.
                4. If there's a difficult word, break down the spelling (e.g., A-P-P-L-E).
                
                EXTRACT KNOWLEDGE:
                If the student makes a recurring mistake, output [KNOWLEDGE: Student, struggles_with, WORD] so I can remember to review it later.

                Keep your response concise and conversational, as it will be spoken aloud by a Text-to-Speech engine. Do not use complex markdown formatting.
            """.trimIndent()

            try {
                val aiResponse = llmEngine.generateResponse(prompt).reduce { acc, value -> acc + value }
                Timber.i("Tutor Response: $aiResponse")

                // Extract [KNOWLEDGE: ...] tags and save them programmatically to memory graph
                val knowledgeRegex = Regex("""\[KNOWLEDGE:\s*([^,\]]+),\s*([^,\]]+),\s*([^,\]]+)\]""", RegexOption.IGNORE_CASE)
                knowledgeRegex.findAll(aiResponse).forEach { match ->
                    val subject = match.groupValues[1].trim()
                    val relation = match.groupValues[2].trim()
                    val obj = match.groupValues[3].trim()
                    memoryManager.saveFact(subject, relation, obj)
                    Timber.d("💡 [Tutor] Extracted knowledge and saved to memory graph: ($subject, $relation, $obj)")
                }

                // Strip the knowledge tags before passing response to TTS
                val cleanResponse = aiResponse.replace(knowledgeRegex, "").replace(Regex("""\s+"""), " ").trim()

                // Speak the response aloud
                speakOut(cleanResponse)
            } catch (e: Exception) {
                Timber.e(e, "Error during offline generation in LiveEnglishTutor")
                speakOut("Maaf, saya mengalami sedikit kesulitan teknis. Bisa diulang?")
            }
        }
    }

    internal fun speakOut(text: String) {
        // [v1.0.5-SAR] Hybrid Speech Routing: Prefers Native AI Speech (Gemma 4) if supported
        // Current implementation defaults to system TTS until Multimodal Speech Manifest is verified.
        val useNativeSpeech = false 
        
        if (useNativeSpeech) {
            Timber.i(" [SAR] Routing to Native AI Speech Player")
            // Future Integration: NativeSpeechPlayer.play(text)
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TutorResponse")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
