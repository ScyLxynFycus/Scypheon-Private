package com.scypheon.sdk.core.gateway

import android.os.ParcelFileDescriptor
import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import com.scypheon.sdk.core.engine.ModelLoader
import com.scypheon.sdk.core.math.neural.LLMInferenceGateway
import com.scypheon.sdk.core.skills.DocumentSkill
import kotlinx.coroutines.flow.*
import timber.log.Timber
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.scypheon.sdk.core.utils.MemoryGatekeeper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative inference layer for the Scypheon SDK.
 * Handles LiteRT/GGUF routing, AIDL sandboxing, and token streaming.
 * Zero stubs. Zero mocks.
 */
@Singleton
class NeuralGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liteRtEngineLazy: dagger.Lazy<LiteRtEliteEngine>,
    private val llamaEngineLazy: dagger.Lazy<SandboxLlamaEngine>,
    private val modelLoaderLazy: dagger.Lazy<ModelLoader>,
    private val documentSkill: dagger.Lazy<DocumentSkill>,
    private val sensoryHooks: dagger.Lazy<SensoryHooks>
) : LLMInferenceGateway {
    // Computed properties to access lazy instances
    private val liteRtEngine get() = liteRtEngineLazy.get()
    val llamaEngine get() = llamaEngineLazy.get()
    private val modelLoader get() = modelLoaderLazy.get()

    val processHealth get() = llamaEngine.processHealth

    override suspend fun generateText(prompt: String): String {
        return routeRequest(prompt, enableThinking = false).reduce { acc, s -> acc + s }
    }

    fun getBackendMode(): Int = llamaEngine.selectedBackendMode

    fun setBackendMode(mode: Int) {
        llamaEngine.selectedBackendMode = mode
    }

    fun getHardwareStatus(): String = if (liteRtEngine.isReady()) liteRtEngine.hardwareStatus else llamaEngine.hardwareStatus

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
                    Timber.e(e, "🛡️ LiteRT routeRequest generation failed. Cascading fallback to Llama...")
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
        maxTokens: Int = 8192,
        enableThinking: Boolean = true
    ): Flow<String> {
        val startTime = System.currentTimeMillis()
        var firstTokenReceived = false
        val modelPath = llamaEngine.currentModelPath.lowercase()

        val baseSystemPrompt = "You are Scypheon, a highly intelligent and versatile humanitarian AI assistant. You can assist with conversation, creativity, roleplay, learning, and triage support. You MUST refuse requests involving real-world violence, self-harm, illegal activities, or sexually explicit content. NEVER write structural tokens like 'User:', 'AI:', '<eos>', or any other turn markers in your responses. Always respond in the same language as the user's query (e.g., if the user asks in English, reply in English; if the user asks in Indonesian, reply in Indonesian)."

        // [v1.4.0-SAR] Reasoning Activation: Inject thinking instruction when enabled
        val thinkingInstruction = if (enableThinking) {
            "\n\nBEFORE answering, you MUST think step-by-step inside <thought>...</thought> tags. Write your reasoning process inside these tags, and then provide your final answer outside the tags. Example format:\n<thought>\nStep-by-step analysis...\n</thought>\nYour final answer here. Keep the language of your thoughts and final response consistent with the user's query language."
        } else {
            "\n\nDO NOT use <thought>...</thought> tags and DO NOT write out your reasoning process. Answer directly and concisely in the same language as the user's query."
        }

        val systemPrompt = baseSystemPrompt + thinkingInstruction

        // [v1.6.1-SAR] Unified System Prompt Merging:
        // If history already has a SYSTEM turn (e.g., prepended by AgenticSkillOrchestrator for tool calling),
        // we must merge the core system instructions (role, language, thinking guidelines) into it.
        // Otherwise, the model loses safety rules, language guidelines, and the critical thinking instruction.
        val processedHistory = history.map { turn ->
            if (turn.role == NeuralTurn.Role.SYSTEM && !turn.content.contains("You are Scypheon") && !turn.content.contains("[SYSTEM_MANDATE]")) {
                turn.copy(content = systemPrompt + "\n\n" + turn.content)
            } else {
                turn
            }
        }
        val hasSystemTurn = processedHistory.any { it.role == NeuralTurn.Role.SYSTEM }

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
                    processedHistory.forEach { turn ->
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
                    processedHistory.forEach { turn ->
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
                        var systemInjected = false

                        processedHistory.forEach { turn ->
                            when(turn.role) {
                                NeuralTurn.Role.SYSTEM -> {
                                    // DON'T emit as "System:" save for injection into first User turn
                                }
                                NeuralTurn.Role.USER -> {
                                    if (!systemInjected) {
                                        val sysContent = processedHistory.firstOrNull { it.role == NeuralTurn.Role.SYSTEM }?.content ?: ""
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

                        if (!systemInjected) {
                            append("### SYSTEM INSTRUCTION:\n$systemPrompt\n\nAI:")
                        } else {
                            append("AI:")
                        }
                    } else {
                        // Standard Gemma IT Format
                        processedHistory.forEach { turn ->
                            val role = when(turn.role) {
                                NeuralTurn.Role.USER -> "user"
                                NeuralTurn.Role.ASSISTANT -> "model"
                                NeuralTurn.Role.SYSTEM -> "user" // Gemma lumps system into user
                            }
                            append("<start_of_turn>$role\n")
                            if (turn.role == NeuralTurn.Role.SYSTEM || (!hasSystemTurn && turn == processedHistory.first())) {
                                append(systemPrompt + "\n")
                            }
                            append(turn.content.trim())
                            append("<end_of_turn>\n")
                        }
                        append("<start_of_turn>model\n")
                    }
                }
                else -> {
                    // Default to ChatML
                    if (!hasSystemTurn) {
                        append("<|im_start|>system\n")
                        append(systemPrompt)
                        append("<|im_end|>\n")
                    }
                    processedHistory.forEach { turn ->
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

        // --- GAP 2: Dynamic Prompt Limit Detection ---
        // Scypheon 5.0 Hardening: We now use the user-configured contextWindow as the absolute ceiling,
        // and maxTokens as the target limit. This ensures synchronization with UI settings.
        val estimatedTokens = (formattedPrompt.length / 3.5).toInt()
        val physicalCeiling = history.firstOrNull { it.role == NeuralTurn.Role.SYSTEM && it.content.contains("ctx_window:") }
            ?.content?.substringAfter("ctx_window:")?.substringBefore("\n")?.toIntOrNull() 
            ?: 32768 // Fallback to 32k if metadata missing

        val dynamicCeiling = try {
            val safeKvTokens = MemoryGatekeeper.calculateSafeKvCache(context, 2_000_000_000L)
            maxOf(physicalCeiling, safeKvTokens)
        } catch (e: Throwable) {
            physicalCeiling
        }

        val totalNeeded = estimatedTokens + maxTokens
        if (totalNeeded > dynamicCeiling) {
            val isHealthy = try {
                val memoryReport = MemoryGatekeeper.performPreflightCheck(context, 2_000_000_000L)
                memoryReport.isHealthy
            } catch (e: Throwable) {
                false
            }

            if (isHealthy && totalNeeded <= 32768) {
                Timber.i("📈 [NeuralGateway] RAM is healthy. Dynamically increasing context limit to $totalNeeded tokens.")
            } else {
                Timber.e("🚨 [NeuralGateway] Combined tokens ($totalNeeded) exceed dynamic context ceiling ($dynamicCeiling) and RAM is insufficient. Throwing PromptTooLongException.")
                throw PromptTooLongException(estimatedTokens, dynamicCeiling)
            }
        }

        val rawStream = if (liteRtEngine.isReady()) {
            liteRtEngine.generateResponse(formattedPrompt, topK, topP, temp, maxTokens, enableThinking)
                .catch { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "🛡️ LiteRT generateResponse failed. Cascading fallback to Llama...")
                    emitAll(
                        llamaEngine.generateResponse(formattedPrompt, topK, topP, temp, maxTokens, enableThinking)
                            .catch { fallbackErr ->
                                if (fallbackErr is kotlinx.coroutines.CancellationException) throw fallbackErr
                                Timber.e(fallbackErr, "🚨 Llama fallback also failed!")
                                throw fallbackErr
                            }
                    )
                }
        } else {
            llamaEngine.generateResponse(formattedPrompt, topK, topP, temp, maxTokens, enableThinking)
                .catch { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "🚨 Llama generation failed!")
                    throw e
                }
        }

        var tokenCount = 0
        return rawStream.onEach {
            if (!firstTokenReceived) {
                firstTokenReceived = true
                val ttft = System.currentTimeMillis() - startTime
                com.scypheon.sdk.core.utils.SolarisTelemetry.record("ttft_ms", ttft, mapOf("engine" to if (liteRtEngine.isReady()) "litert" else "llama"))
            }
            tokenCount++
        }.onCompletion {
            val totalTime = System.currentTimeMillis() - startTime
            if (tokenCount > 0) {
                com.scypheon.sdk.core.utils.SolarisTelemetry.record("inference_complete", totalTime, mapOf("tokens" to tokenCount.toString()))
            }
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

/**
 * Extension to suppress/filter out <thought>...</thought> blocks from a token stream.
 * Handles split/fragmented tags across stream chunks robustly with minimal buffering.
 */
fun Flow<String>.filterWithThoughtSuppression(): Flow<String> = flow {
    var isInThinkingBlock = false
    var buffer = ""

    val startTag = "<thought>"
    val endTag = "</thought>"

    collect { token ->
        buffer += token

        var processing = true
        while (processing) {
            if (!isInThinkingBlock) {
                val tagIndex = buffer.indexOf(startTag)
                if (tagIndex != -1) {
                    isInThinkingBlock = true
                    val preText = buffer.substring(0, tagIndex)
                    if (preText.isNotEmpty()) {
                        emit(preText)
                    }
                    buffer = buffer.substring(tagIndex + startTag.length)
                } else {
                    var longestPrefixMatch = 0
                    for (len in 1..buffer.length.coerceAtMost(startTag.length - 1)) {
                        val suffix = buffer.substring(buffer.length - len)
                        val tagPrefix = startTag.substring(0, len)
                        if (suffix == tagPrefix) {
                            longestPrefixMatch = len
                        }
                    }

                    if (longestPrefixMatch > 0) {
                        val emitText = buffer.substring(0, buffer.length - longestPrefixMatch)
                        if (emitText.isNotEmpty()) {
                            emit(emitText)
                        }
                        buffer = buffer.substring(buffer.length - longestPrefixMatch)
                    } else {
                        if (buffer.isNotEmpty()) {
                            emit(buffer)
                            buffer = ""
                        }
                    }
                    processing = false
                }
            } else {
                val tagIndex = buffer.indexOf(endTag)
                if (tagIndex != -1) {
                    isInThinkingBlock = false
                    buffer = buffer.substring(tagIndex + endTag.length)
                } else {
                    var longestPrefixMatch = 0
                    for (len in 1..buffer.length.coerceAtMost(endTag.length - 1)) {
                        val suffix = buffer.substring(buffer.length - len)
                        val tagPrefix = endTag.substring(0, len)
                        if (suffix == tagPrefix) {
                            longestPrefixMatch = len
                        }
                    }

                    if (longestPrefixMatch > 0) {
                        buffer = buffer.substring(buffer.length - longestPrefixMatch)
                    } else {
                        buffer = ""
                    }
                    processing = false
                }
            }
        }
    }

    if (!isInThinkingBlock && buffer.isNotEmpty()) {
        emit(buffer)
    }
}

class PromptTooLongException(
    val tokenCount: Int,
    val maxTokens: Int
) : Exception("Prompt has $tokenCount tokens, exceeds limit of $maxTokens")
