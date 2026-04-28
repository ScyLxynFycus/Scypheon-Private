package com.scypheon.sdk.core.gateway

import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import com.scypheon.sdk.core.security.AegisPrivacyShield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NeuralGateway is the unified hub for all AI inference in Scypheon.
 * It dynamically routes requests between LiteRT-LM (Gemma 4 Elite)
 * and Llama.cpp (Universal GGUF) based on model type and device capability.
 */
@Singleton
class NeuralGateway @Inject constructor(
    private val liteRtEngine: LiteRtEliteEngine,
    val llamaEngine: SandboxLlamaEngine,
    val modelLoader: com.scypheon.sdk.core.engine.ModelLoader,
    private val documentSkill: dagger.Lazy<com.scypheon.sdk.core.skills.DocumentSkill>,
    private val sensoryHooks: dagger.Lazy<SensoryHooks>
) {
    val processHealth = llamaEngine.processHealth

    /**
     * Represent a single turn in a conversational context.
     */
    data class NeuralTurn(
        val role: Role,
        val content: String
    ) {
        enum class Role { SYSTEM, USER, ASSISTANT }
    }

    /**
     * The path to the currently active model. 
     * Setting this does NOT load the model immediately (Lazy Loading).
     */
    var currentModelPath: String? = null

    /**
     * Ensures the Llama engine is active and LiteRT is released.
     */
    private suspend fun ensureLlamaReady(modelPath: String, nCtx: Int = 4096): Boolean {
        if (liteRtEngine.isReady()) {
            Timber.w("[HOTSWAP] Releasing LiteRT to make room for Llama.")
            liteRtEngine.release()
        }
        
        return if (llamaEngine.isReady() && currentModelPath == modelPath) {
            true
        } else {
            llamaEngine.initialize(modelPath, nCtx)
        }
    }

    /**
     * Ensures the LiteRT engine is active and Llama is released.
     */
    private suspend fun ensureLiteRtReady(modelPath: String, nCtx: Int = 2048): Boolean {
        if (llamaEngine.isReady()) {
            Timber.w("[HOTSWAP] Releasing Llama engine to make room for LiteRT.")
            llamaEngine.release()
        }

        return if (liteRtEngine.isReady() && currentModelPath == modelPath) {
            true
        } else {
            liteRtEngine.initialize(modelPath, nCtx)
        }
    }

    /**
     * Initializes engines while enforcing strict exclusivity.
     */
    suspend fun initialize(elitePath: String, universalPath: String, nCtx: Int = 4096): Boolean {
        Timber.i("Initializing NeuralGateway engines (Strict Exclusive Mode)...")

        return when {
            universalPath.isNotBlank() -> {
                val success = ensureLlamaReady(universalPath, nCtx)
                if (success) currentModelPath = universalPath
                success
            }
            elitePath.isNotBlank() -> {
                val success = ensureLiteRtReady(elitePath, nCtx)
                if (success) currentModelPath = elitePath
                success
            }
            else -> false
        }
    }

    /**
     * Checks if at least one engine is ready for inference.
     */
    fun isReady(): Boolean {
        return liteRtEngine.isReady() || llamaEngine.isReady()
    }

    /**
     * Retrieves the current hardware status from the active engine.
     */
    fun getHardwareStatus(): String {
        val path = currentModelPath ?: return "Not Loaded"
        return if (path.contains(".task") || path.endsWith(".litertlm")) {
            liteRtEngine.hardwareStatus
        } else {
            llamaEngine.hardwareStatus
        }
    }

    /**
     * Sets the preferred backend mode for the Llama engine.
     * 0: AUTO, 1: FORCE_CPU, 2: FORCE_GPU
     */
    fun setBackendMode(mode: Int) {
        llamaEngine.selectedBackendMode = mode
    }

    fun getBackendMode(): Int = llamaEngine.selectedBackendMode

    val isMaliDevice: Boolean get() = llamaEngine.isMaliDevice

    /**
     * Unified generation method that routes based on the current context.
     * Automatically detects humanitarian intent (Education, Reminiscence) to apply personas.
     * ALIGNMENT: Respects the multi-turn protocol of Gemini/Qwen architects.
     */
    fun generateResponse(
        history: List<NeuralTurn>, 
        topK: Int = 51, 
        topP: Float = 0.95f, 
        temp: Float = 0.8f,
        maxTokens: Int = 1024,
        enableThinking: Boolean = true
    ): Flow<String> {
        val path = currentModelPath ?: ""
        
        // Humanitarian Intent Detection (from the latest user message)
        val lastUserMsg = history.lastOrNull { it.role == NeuralTurn.Role.USER }?.content ?: ""
        val detectedIntent = detectIntent(lastUserMsg)
        val systemInstruction = getSystemInstruction(detectedIntent, enableThinking)
        
        // Assemble the full message list with the system instruction at the head
        val fullHistory = mutableListOf<NeuralTurn>().apply {
            add(NeuralTurn(NeuralTurn.Role.SYSTEM, systemInstruction))
            addAll(history)
        }
        
        return processRequest(fullHistory, path, topK, topP, temp, maxTokens)
    }

    /**
     * Legacy/Convenience wrapper for single-prompt requests.
     * Automatically wraps the prompt in a USER turn for the Gemma protocol.
     */
    fun routeRequest(prompt: String): Flow<String> {
        val turn = NeuralTurn(NeuralTurn.Role.USER, prompt)
        return generateResponse(listOf(turn))
    }

    private fun detectIntent(prompt: String): String {
        val lowercase = prompt.lowercase()
        return when {
            lowercase.contains("inggris") || lowercase.contains("english") || 
            lowercase.contains("belajar") || lowercase.contains("tanya") && lowercase.contains("bahasa") -> "EDUCATION"
            
            lowercase.length > 50 && (lowercase.contains("dulu") || lowercase.contains("ingat") || 
            lowercase.contains("kenangan") || lowercase.contains("masa kecil")) -> "REMINISCENCE"
            
            lowercase.contains("baca") || lowercase.contains("scan") || 
            lowercase.contains("dokumen") || lowercase.contains("ocr") || 
            lowercase.contains("tulisannya") -> "DOCUMENT_OCR"
            
            else -> "GENERAL"
        }
    }

    private fun getSystemInstruction(intent: String, enableThinking: Boolean): String {
        val thinkingDirective = if (enableThinking) {
            "\nReasoning Protocol: Think step-by-step before answering. Use <thought> tags for your internal reasoning if the task is complex."
        } else ""

        val baseInstruction = when (intent) {
            "EDUCATION" -> """
                The user is a child wanting to learn English. Be a patient and encouraging teacher.
                1. If there are grammar mistakes, correct them gently.
                2. Provide examples of word usage.
                3. Keep explanations simple and avoid complex markdown unless necessary for clarity.
            """.trimIndent()
            
            "REMINISCENCE" -> """
                You are accompanying an elderly person sharing their memories. Be a deeply empathetic listener.
                1. Validate their emotions.
                2. Ask gentle follow-up questions about the details of their stories.
                3. If specific names of people or important places are mentioned, record them using the format [FACT: "name/place"].
            """.trimIndent()
            
            else -> AegisPrivacyShield.SYSTEM_GUARDRAIL_PROMPT
        }
        
        return baseInstruction + thinkingDirective
    }

    /**
     * [COMPETITION PROTOCOL] Implements the official Gemma Instruct templates.
     * Focused exclusively on Gemma 4 and Gemma 3n for the Hackathon.
     */
    private fun applyChatTemplate(turns: List<NeuralTurn>, modelPath: String): String {
        val path = modelPath.lowercase()
        val builder = StringBuilder()
        
        // Official Gemma Instruct Protocol (Gemma 3n / Gemma 4):
        // <start_of_turn>user
        // [System instruction if first turn]\n[User message]<end_of_turn>
        // <start_of_turn>model
        // [Assistant response]<end_of_turn>
        
        var systemText = ""
        turns.forEachIndexed { index, turn ->
            when (turn.role) {
                NeuralTurn.Role.SYSTEM -> {
                    systemText = turn.content.trim() + "\n\n"
                }
                NeuralTurn.Role.USER -> {
                    builder.append("<start_of_turn>user\n")
                    // Note: Gemma 3n/4 expects system instructions to be blended into the first user turn 
                    // to maintain role-based attention consistency.
                    if (index == 1 && systemText.isNotEmpty()) { 
                        builder.append(systemText)
                    }
                    builder.append(turn.content.trim()).append("<end_of_turn>\n")
                }
                NeuralTurn.Role.ASSISTANT -> {
                    builder.append("<start_of_turn>model\n")
                    builder.append(turn.content.trim()).append("<end_of_turn>\n")
                }
            }
        }
        builder.append("<start_of_turn>model\n") // Prompt for completion
        
        return builder.toString()
    }

    /**
     * Routes a text prompt to the best available engine.
     * Automatically handles AI Edge Guardrails and Redaction.
     */
    fun processRequest(
        history: List<NeuralTurn>, 
        modelPath: String,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        maxTokens: Int = 1024
    ): Flow<String> {
        // Redact all user messages in history
        val safeHistory = history.map { turn ->
            if (turn.role == NeuralTurn.Role.USER) {
                // Check for malicious intent on the latest user message
                if (turn == history.last() && AegisPrivacyShield.isMaliciousIntent(turn.content)) {
                    return flowOf("Access Denied: Safety violation.")
                }
                turn.copy(content = AegisPrivacyShield.redact(turn.content))
            } else turn
        }
        
        // Apply the official chat template
        val finalPrompt = applyChatTemplate(safeHistory, modelPath)

        val isLiteRt = modelPath.contains(".task") || modelPath.endsWith(".litertlm")
        val isGguf = modelPath.endsWith(".gguf")

        return when {
            isLiteRt -> {
                Timber.i("[ROUTE] LiteRT-LM Elite Engine")
                if (llamaEngine.isReady()) {
                    Timber.w("[HOTSWAP] Releasing Llama for LiteRT.")
                    llamaEngine.release()
                }
                liteRtEngine.generateResponse(finalPrompt, topK, topP, temp, maxTokens)
            }
            isGguf -> {
                Timber.i("[ROUTE] Llama Universal Core Engine")
                if (liteRtEngine.isReady()) {
                    Timber.w("[HOTSWAP] Releasing LiteRT for Llama.")
                    liteRtEngine.release()
                }
                llamaEngine.generateResponse(finalPrompt, topK, topP, temp, maxTokens)
            }
            else -> {
                when {
                    liteRtEngine.isReady() -> liteRtEngine.generateResponse(finalPrompt, topK, topP, temp)
                    llamaEngine.isReady() -> llamaEngine.generateResponse(finalPrompt, topK, topP, temp)
                    else -> flowOf("Error: No engine ready.")
                }
            }
        }
    }

    /**
     * Routes a multimodal request (text + image).
     * [v1.0.5-SAR] Multimodal Guard: Fallback to OCR if the model doesn't support images.
     */
    fun processMultimodalRequest(request: MultimodalRequest, modelPath: String): Flow<String> {
        if (AegisPrivacyShield.isMaliciousIntent(request.prompt)) {
            return flowOf("Access Denied: This request violates safety guardrails.")
        }

        val isGguf = modelPath.endsWith(".gguf")
        val isElite = modelPath.contains(".task") || modelPath.endsWith(".litertlm")
        
        // --- MULTIMODAL GUARD ---
        // If it's a GGUF model (often text-only) or intent is specifically DOCUMENT_OCR, 
        // we use the DocumentSkill (MLKit) to extract text first.
        val intent = detectIntent(request.prompt)
        
        if (request.image != null && (isGguf || intent == "DOCUMENT_OCR")) {
            Timber.w(" [SAR] Model or Intent requires OCR fallback. Extracting text via DocumentSkill...")
            return flow {
                val extractedText = documentSkill.get().extractText(request.image!!)
                val enrichedPrompt = "User provided an image with this text: \"$extractedText\"\n\nUser Question: ${request.prompt}"
                
                val safePrompt = AegisPrivacyShield.redact(enrichedPrompt)
                val turns = listOf(
                    NeuralTurn(NeuralTurn.Role.SYSTEM, AegisPrivacyShield.SYSTEM_GUARDRAIL_PROMPT),
                    NeuralTurn(NeuralTurn.Role.USER, safePrompt)
                )
                
                emitAll(processRequest(turns, modelPath))
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        }

        val safePrompt = AegisPrivacyShield.redact(request.prompt)
        val finalPrompt = applyChatTemplate(
            listOf(
                NeuralTurn(NeuralTurn.Role.SYSTEM, AegisPrivacyShield.SYSTEM_GUARDRAIL_PROMPT),
                NeuralTurn(NeuralTurn.Role.USER, safePrompt)
            ),
            modelPath
        )

        return when {
            isElite -> {
                Timber.i("Routing multimodal to LiteRT-LM Elite")
                liteRtEngine.generateMultimodalResponse(finalPrompt, request.image)
            }
            isGguf -> {
                // Fallback handled above, but as a safety:
                Timber.i("Routing multimodal to Llama.cpp Universal (Vision limited)")
                llamaEngine.generateResponse(finalPrompt)
            }
            else -> flowOf("Error: Unsupported model format.")
        }
    }

    /**
     * Releases resources from both engines.
     */
    fun release() {
        Timber.i("Releasing NeuralGateway engines...")
        liteRtEngine.release()
        llamaEngine.release()
        currentModelPath = null
    }

    private fun routeToLiteRt(prompt: String, modelPath: String): Flow<String> {
        return liteRtEngine.generateResponse(prompt)
    }

    private fun routeToLlama(prompt: String, modelPath: String): Flow<String> {
        return llamaEngine.generateResponse(prompt)
    }

    // SAR PHASE 3: Zero-Latency Handoff & Recovery Proxy
    suspend fun attachTensorMemory(pfd: android.os.ParcelFileDescriptor, size: Long, modelHash: String): Boolean {
        return llamaEngine.attachTensorMemory(pfd, size, modelHash)
    }

    suspend fun nativeKvRestore(seqId: Int, lastPos: Int) {
        llamaEngine.nativeKvRestore(seqId, lastPos)
    }

    suspend fun injectToken(tokenId: Int, kvOffset: Int, sequenceNumber: Long) {
        llamaEngine.injectToken(tokenId, kvOffset, sequenceNumber)
    }

    /**
     * [HOTSWAP] Unified embedding entry point.
     * Enforces mutual exclusion: LiteRT is released before delegating to the Llama sandbox.
     * Returns FloatArray(0) if the model is not loaded or native extraction fails.
     */
    suspend fun getEmbeddings(text: String, modelPath: String): FloatArray {
        // Enforce Llama-only RAM policy during embedding pass
        if (liteRtEngine.isReady()) {
            Timber.w("[HOTSWAP] Embedding request: releasing LiteRT to free RAM for Llama piggyback.")
            liteRtEngine.release()
        }

        return if (llamaEngine.isReady()) {
            Timber.i("[EMBED] Delegating embedding request to Sandbox engine.")
            llamaEngine.getEmbeddings(text) ?: FloatArray(0)
        } else {
            Timber.w("[EMBED] Llama engine not ready. Attempting to load $modelPath first.")
            val loaded = ensureLlamaReady(modelPath)
            if (loaded) {
                llamaEngine.getEmbeddings(text) ?: FloatArray(0)
            } else {
                Timber.e("[EMBED] Engine load failed. Returning empty embedding.")
                FloatArray(0)
            }
        }
    }
}
