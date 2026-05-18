package com.scypheon.sdk.core.gateway

import android.os.ParcelFileDescriptor
import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import com.scypheon.sdk.core.engine.ModelLoader
import com.scypheon.sdk.core.skills.DocumentSkill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative inference layer for the Scypheon SDK.
 * Handles LiteRT/GGUF routing, AIDL sandboxing, and token streaming.
 * Zero stubs. Zero mocks.
 */
@Singleton
class NeuralGateway @Inject constructor(
    private val liteRtEngineLazy: dagger.Lazy<LiteRtEliteEngine>,
    private val llamaEngineLazy: dagger.Lazy<SandboxLlamaEngine>,
    private val modelLoaderLazy: dagger.Lazy<ModelLoader>,
    private val documentSkill: dagger.Lazy<DocumentSkill>,
    private val sensoryHooks: dagger.Lazy<SensoryHooks>
) {
    // Computed properties to access lazy instances
    private val liteRtEngine get() = liteRtEngineLazy.get()
    val llamaEngine get() = llamaEngineLazy.get()
    private val modelLoader get() = modelLoaderLazy.get()

    val processHealth get() = llamaEngine.processHealth

    fun getBackendMode(): Int = llamaEngine.selectedBackendMode
    
    fun setBackendMode(mode: Int) {
        llamaEngine.selectedBackendMode = mode
    }

    fun getHardwareStatus(): String = llamaEngine.hardwareStatus

    fun isReady(): Boolean = llamaEngine.isReady() || liteRtEngine.isReady()

    suspend fun initializeLiteRt(modelPath: String, nCtx: Int): Boolean {
        return liteRtEngine.initialize(modelPath, nCtx)
    }
    
    suspend fun probeBackend(modelPath: String, mode: Int): Boolean {
        return llamaEngine.probeBackend(modelPath, mode)
    }

    fun release() {
        llamaEngine.release()
        liteRtEngine.release()
    }

    fun releaseLlama() {
        llamaEngine.release()
    }

    fun releaseLiteRt() {
        liteRtEngine.release()
    }

    /**
     * Routes a prompt to the optimal inference engine based on hardware state.
     * Returns a Flow of tokens for real-time streaming.
     */
    fun routeRequest(prompt: String, enableThinking: Boolean = true): Flow<String> {
        return if (liteRtEngine.isReady()) {
            liteRtEngine.generateResponse(prompt, 51, 0.95f, 0.8f, 4096, enableThinking)
                .catch { e ->
                    Timber.e(e, "🚨 LiteRT routeRequest generation failed. Cascading fallback to Llama...")
                    emitAll(llamaEngine.generateResponse(prompt, 51, 0.95f, 0.8f, 4096, enableThinking))
                }
        } else {
            llamaEngine.generateResponse(prompt, 51, 0.95f, 0.8f, 4096, enableThinking)
        }
    }

    fun generateResponse(
        history: List<NeuralTurn>,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        maxTokens: Int = 2048,
        enableThinking: Boolean = true
    ): Flow<String> {
        val modelPath = llamaEngine.currentModelPath.lowercase()
        
        val baseSystemPrompt = "Anda adalah Scypheon, asisten AI humaniter yang cerdas dan serbaguna. Anda bisa membantu percakapan, kreativitas, roleplay, belajar, dan dukungan triase. Anda HARUS menolak permintaan yang melibatkan kekerasan nyata, self-harm, aktivitas ilegal, atau konten seksual eksplisit. JANGAN pernah menulis token struktural seperti 'User:', 'AI:', '<eos>', atau penanda giliran lainnya dalam respons Anda."
        
        // [v1.4.0-SAR] Reasoning Activation: Inject thinking instruction when enabled
        val thinkingInstruction = if (enableThinking) {
            "\n\nSEBELUM menjawab, Anda WAJIB berpikir langkah demi langkah di dalam tag <thought>...</thought>. Tulis proses penalaran Anda di dalam tag tersebut, lalu berikan jawaban final di luar tag. Contoh format:\n<thought>\nAnalisis langkah demi langkah...\n</thought>\nJawaban final Anda di sini."
        } else {
            "\n\nJANGAN gunakan tag <thought>...</thought> dan JANGAN menuliskan proses penalaran Anda. Jawab secara langsung secara lugas."
        }
        
        val systemPrompt = baseSystemPrompt + thinkingInstruction
        val hasSystemTurn = history.any { it.role == NeuralTurn.Role.SYSTEM }

        val formattedPrompt = buildString {
            when {
                modelPath.contains("llama-3") || modelPath.contains("llama3") -> {
                    // Llama-3 Format
                    append("<|begin_of_text|>")
                    if (!hasSystemTurn) {
                        append("<|start_header_id|>system<|end_header_id|>\n\n")
                        append(systemPrompt)
                        append("<|eot_id|>\n")
                    }
                    history.forEach { turn ->
                        val role = when(turn.role) {
                            NeuralTurn.Role.USER -> "user"
                            NeuralTurn.Role.ASSISTANT -> "assistant"
                            NeuralTurn.Role.SYSTEM -> "system"
                        }
                        append("<|start_header_id|>$role<|end_header_id|>\n\n")
                        append(turn.content.trim())
                        append("<|eot_id|>\n")
                    }
                    append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                }
                modelPath.contains("mistral") || modelPath.contains("mixtral") -> {
                    // Mistral INST Format
                    if (!hasSystemTurn) {
                        append("[INST] System: $systemPrompt\n")
                    }
                    history.forEach { turn ->
                        when(turn.role) {
                            NeuralTurn.Role.USER -> append("[INST] ${turn.content.trim()} [/INST]\n")
                            NeuralTurn.Role.ASSISTANT -> append("${turn.content.trim()}\n")
                            NeuralTurn.Role.SYSTEM -> append("[INST] System: ${turn.content.trim()}\n")
                        }
                    }
                }
                modelPath.contains("gemma") && !modelPath.contains("chatml") -> {
                    // Detect Unsloth fine-tuned models (e2b = "Easy to Build" Unsloth naming)
                    val isUnsloth = modelPath.contains("e2b") || modelPath.contains("unsloth")
                    
                    if (isUnsloth) {
                        // [v1.1.5-SAR] UNSLOTH PLAIN-TEXT FORMAT
                        // Unsloth LoRA fine-tunes ONLY know "User:" and "AI:" roles.
                        // They were NEVER trained with "System:" — sending a separate
                        // System turn is ignored. We MUST inject the system instruction
                        // as a preamble inside the first User message.
                        var systemInjected = false
                        
                        history.forEach { turn ->
                            when(turn.role) {
                                NeuralTurn.Role.SYSTEM -> {
                                    // DON'T emit as "System:" — save for injection into first User turn
                                }
                                NeuralTurn.Role.USER -> {
                                    if (!systemInjected) {
                                        val sysContent = history.firstOrNull { it.role == NeuralTurn.Role.SYSTEM }?.content ?: ""
                                        val fullContext = if (sysContent.isNotBlank()) {
                                            "### SYSTEM INSTRUCTION:\n$systemPrompt\n$sysContent\n\nUser: ${turn.content.trim()}"
                                        } else {
                                            "### SYSTEM INSTRUCTION:\n$systemPrompt\n\nUser: ${turn.content.trim()}"
                                        }
                                        append("$fullContext\n")
                                        systemInjected = true
                                    } else {
                                        append("User: ${turn.content.trim()}\n")
                                    }
                                }
                                NeuralTurn.Role.ASSISTANT -> append("AI: ${turn.content.trim()}\n")
                            }
                        }
                        
                        // If no user turn existed to inject into, prepend system as context
                        if (!systemInjected) {
                            append("### SYSTEM INSTRUCTION:\n$systemPrompt\n\nAI:")
                        } else {
                            append("AI:")
                        }
                    } else {
                        // Standard Gemma IT Format
                        history.forEach { turn ->
                            val role = when(turn.role) {
                                NeuralTurn.Role.USER -> "user"
                                NeuralTurn.Role.ASSISTANT -> "model"
                                NeuralTurn.Role.SYSTEM -> "user" // Gemma lumps system into user
                            }
                            append("<start_of_turn>$role\n")
                            if (turn.role == NeuralTurn.Role.SYSTEM || (!hasSystemTurn && turn == history.first())) {
                                append(systemPrompt + "\n")
                            }
                            append(turn.content.trim())
                            append("<end_of_turn>\n")
                        }
                        append("<start_of_turn>model\n")
                    }
                }
                else -> {
                    // Default to ChatML (Scypheon Gemma-4 custom, Qwen, etc)
                    if (!hasSystemTurn) {
                        append("<|im_start|>system\n")
                        append(systemPrompt)
                        append("<|im_end|>\n")
                    }
                    history.forEach { turn ->
                        val role = when(turn.role) {
                            NeuralTurn.Role.USER -> "user"
                            NeuralTurn.Role.ASSISTANT -> "assistant"
                            NeuralTurn.Role.SYSTEM -> "system" 
                        }
                        append("<|im_start|>$role\n")
                        append(turn.content.trim())
                        append("<|im_end|>\n")
                    }
                    append("<|im_start|>assistant\n")
                }
            }
        }
        return if (liteRtEngine.isReady()) {
            liteRtEngine.generateResponse(formattedPrompt, topK, topP, temp, maxTokens, enableThinking)
                .catch { e ->
                    Timber.e(e, "🚨 LiteRT generateResponse failed. Cascading fallback to Llama...")
                    emitAll(llamaEngine.generateResponse(formattedPrompt, topK, topP, temp, maxTokens, enableThinking))
                }
        } else {
            llamaEngine.generateResponse(formattedPrompt, topK, topP, temp, maxTokens, enableThinking)
        }
    }

    suspend fun attachTensorMemory(pfd: ParcelFileDescriptor, size: Long, hash: String): Boolean {
        return llamaEngine.attachTensorMemory(pfd, size, hash)
    }

    suspend fun nativeKvRestore(seqId: Int, lastPos: Int) {
        llamaEngine.nativeKvRestore(seqId, lastPos)
    }

    suspend fun injectToken(tokenId: Int, kvOffset: Int, sequenceNumber: Long) {
        llamaEngine.injectToken(tokenId, kvOffset, sequenceNumber)
    }

    data class NeuralTurn(
        val role: Role,
        val content: String
    ) {
        enum class Role {
            USER,
            ASSISTANT,
            SYSTEM
        }
    }
    suspend fun promoteToForeground(): Boolean {
        return llamaEngine.promoteToForeground()
    }
}
