package com.scypheon.sdk.core.live

import android.content.Context
import android.graphics.Bitmap
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiveSessionOrchestrator — The brain of Scypheon Live.
 * 
 * [v1.5.0-SAR] Real-time multimodal conversation loop inspired by 
 * Gemini Live / ChatGPT Advanced Voice Mode.
 * 
 * Architecture:
 *   User speaks → STT → text → Gemma 4 inference → text → TTS → User hears
 *                  ↑                                              ↓
 *           [CameraX frames → Vision context injected]      [Auto-listen again]
 * 
 * The orchestrator manages turn-taking, vision context injection,
 * and the continuous listen-process-speak cycle.
 */
@Singleton
class LiveSessionOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: NeuralGateway,
    private val blackBoxVault: BlackBoxVault
) {
    // ═══════════════════════════════════════════════════════════════
    // State Machine
    // ═══════════════════════════════════════════════════════════════
    
    sealed class LiveState {
        object Idle : LiveState()
        object Listening : LiveState()
        data class UserSpeaking(val partialText: String) : LiveState()
        data class Processing(val userText: String) : LiveState()
        data class AiSpeaking(val responseText: String, val progress: Float = 0f) : LiveState()
        data class Error(val message: String) : LiveState()
    }

    private val _state = MutableStateFlow<LiveState>(LiveState.Idle)
    val state: StateFlow<LiveState> = _state.asStateFlow()

    // Conversation history for multi-turn
    private val conversationHistory = mutableListOf<NeuralGateway.NeuralTurn>()
    
    // Vision context (injected from camera pipeline)
    private var latestVisionContext: String? = null
    private var latestCameraFrame: Bitmap? = null
    
    // Audio/ambient context (injected from audio pipeline)
    private var latestAmbientContext: String? = null
    
    // Coroutine scope for the live session
    private var sessionJob: Job? = null
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Transcript accumulator for UI display
    private val _transcript = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    // Audio level for waveform visualization (0.0 - 1.0)
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // ═══════════════════════════════════════════════════════════════
    // Session Lifecycle
    // ═══════════════════════════════════════════════════════════════

    fun startSession() {
        if (_state.value != LiveState.Idle) {
            Timber.w("🎙️ [LIVE] Session already active. Ignoring start.")
            return
        }

        Timber.i("🎙️ [LIVE] Starting Live Session...")
        blackBoxVault.logEvent("LIVE_SESSION_START", "Scypheon Live Mode activated")

        // Add system prompt for AGENTIC live conversation
        conversationHistory.clear()
        conversationHistory.add(
            NeuralGateway.NeuralTurn(
                NeuralGateway.NeuralTurn.Role.SYSTEM,
                """You are in LIVE CONVERSATION mode with real-time multimodal awareness.
                |You can SEE through the device camera and HEAR the environment.
                |
                |Rules:
                |1. Keep responses SHORT and conversational (1-3 sentences max)
                |2. Be natural, like talking to a friend face-to-face
                |3. You will receive [VISION CONTEXT: ...] with what you currently see
                |4. You will receive [AMBIENT: ...] with what you currently hear  
                |5. PROACTIVELY reference what you see — don't wait to be asked
                |6. If you see something interesting or dangerous, mention it naturally
                |7. Respond in the same language the user speaks
                |8. Do NOT use markdown, emojis, or formatting — speak naturally
                |9. If the user asks "what do you see", describe the scene in detail
                |10. You are self-aware of your surroundings at all times""".trimMargin()
            )
        )

        _state.value = LiveState.Listening
        _transcript.value = emptyList()
    }

    fun stopSession() {
        Timber.i("🎙️ [LIVE] Stopping Live Session...")
        sessionJob?.cancel()
        sessionJob = null
        _state.value = LiveState.Idle
        latestVisionContext = null
        latestCameraFrame = null
        blackBoxVault.logEvent("LIVE_SESSION_STOP", "Scypheon Live Mode deactivated. Turns: ${conversationHistory.size}")
    }

    val isActive: Boolean get() = _state.value != LiveState.Idle

    // ═══════════════════════════════════════════════════════════════
    // Speech Input (from ContinuousSpeechRecognizer)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when partial speech results arrive (real-time transcription).
     */
    fun onPartialSpeech(partialText: String) {
        if (!isActive) return
        _state.value = LiveState.UserSpeaking(partialText)
    }

    /**
     * Called by the audio analyzer to update the waveform visualization.
     */
    fun onAudioLevel(rmsDb: Float) {
        // Normalize RMS dB to 0-1 range (typical speech: -40 to 0 dB)
        val normalized = ((rmsDb + 40f) / 40f).coerceIn(0f, 1f)
        _audioLevel.value = normalized
    }

    /**
     * Called when the user finishes speaking (final STT result).
     * Triggers the full inference cycle.
     */
    fun onUserSpeechComplete(finalText: String) {
        if (!isActive || finalText.isBlank()) {
            // Resume listening if empty
            _state.value = LiveState.Listening
            return
        }

        Timber.i("🎙️ [LIVE] User said: \"$finalText\"")
        _state.value = LiveState.Processing(finalText)

        // Add to transcript
        _transcript.update { it + TranscriptEntry(finalText, isUser = true) }

        // Launch inference
        sessionJob = sessionScope.launch {
            try {
                val response = generateLiveResponse(finalText)
                
                if (isActive) {
                    _transcript.update { it + TranscriptEntry(response, isUser = false) }
                    _state.value = LiveState.AiSpeaking(response)

                    // The ViewModel will handle TTS and then call onAiFinishedSpeaking()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "🎙️ [LIVE] Inference failed")
                _state.value = LiveState.Error(e.message ?: "Inference failed")
                delay(2000)
                if (isActive) _state.value = LiveState.Listening
            }
        }
    }

    /**
     * Called when TTS finishes speaking the AI response.
     * Resumes listening for the next turn.
     */
    fun onAiFinishedSpeaking() {
        if (isActive) {
            Timber.d("🎙️ [LIVE] AI finished speaking. Resuming listening.")
            _state.value = LiveState.Listening
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Vision Context (from CameraX pipeline)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Injects vision context from object detection / scene analysis.
     * This context is automatically included in the next inference turn.
     */
    fun injectVisionContext(description: String) {
        latestVisionContext = description
        Timber.d("🎙️ [LIVE] Vision context updated: $description")
    }

    /**
     * Stores the latest camera frame for multimodal inference.
     */
    fun injectCameraFrame(frame: Bitmap) {
        latestCameraFrame?.recycle()
        latestCameraFrame = frame.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Injects ambient audio context (noise level, sound classification).
     */
    fun injectAmbientContext(description: String) {
        latestAmbientContext = description
    }

    // ═══════════════════════════════════════════════════════════════
    // Inference
    // ═══════════════════════════════════════════════════════════════

    private suspend fun generateLiveResponse(userText: String): String = withContext(Dispatchers.IO) {
        // Build the user turn with PROACTIVE environmental context
        val enrichedQuery = buildString {
            append(userText)
            latestVisionContext?.let { vision ->
                append("\n\n[VISION CONTEXT: $vision]")
            }
            latestAmbientContext?.let { ambient ->
                append("\n[AMBIENT: $ambient]")
            }
        }

        conversationHistory.add(
            NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.USER, enrichedQuery)
        )

        // Collect streaming tokens into final response
        val responseBuilder = StringBuilder()
        
        gateway.generateResponse(
            conversationHistory,
            topK = 40,
            topP = 0.9f,
            temp = 0.7f,
            maxTokens = 256, // Short responses for live mode
            enableThinking = false // No thinking block in live mode
        ).collect { token ->
            responseBuilder.append(token)
        }

        val response = responseBuilder.toString().trim()

        // Add to conversation history
        conversationHistory.add(
            NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, response)
        )

        // Trim history to prevent context overflow (keep last 20 turns)
        if (conversationHistory.size > 22) { // 2 system + 20 conversation
            val systemPrompts = conversationHistory.take(1)
            val recentTurns = conversationHistory.takeLast(20)
            conversationHistory.clear()
            conversationHistory.addAll(systemPrompts)
            conversationHistory.addAll(recentTurns)
        }

        blackBoxVault.logEvent("LIVE_INFERENCE", "Q: ${userText.take(50)}... A: ${response.take(50)}...")
        
        response
    }

    // ═══════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════

    data class TranscriptEntry(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
}
