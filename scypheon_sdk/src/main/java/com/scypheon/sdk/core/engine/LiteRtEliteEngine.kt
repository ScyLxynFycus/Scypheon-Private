package com.scypheon.sdk.core.engine

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.utils.SolarisTelemetry
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiteRtEliteEngine implements optimized inference for Gemma 3/4 models
 * using the modern LiteRT-LM framework. It prioritizes GPU/NPU acceleration.
 * Fully hardened with resilience circuit-breaker recovery and telemetry integration.
 */
@Singleton
class LiteRtEliteEngine @Inject constructor(
    private val circuitBreaker: ResilienceCircuitBreaker
) : BaseAiEngine {
    
    companion object {
        private val isLibraryLoaded = java.util.concurrent.atomic.AtomicBoolean(false)

        private fun ensureLibraryLoaded() {
            if (isLibraryLoaded.get()) return
            synchronized(this) {
                if (isLibraryLoaded.get()) return
                try {
                    System.loadLibrary("litertlm_jni")
                    isLibraryLoaded.set(true)
                    Timber.i("✅ LiteRT-LM JNI Library loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Timber.e(e, "❌ Failed to load litertlm_jni. Ensure the .so is bundled in the APK.")
                    throw e
                }
            }
        }
    }

    override val engineId: String = "litert_elite"
    override var friendlyName: String = "Gemma Elite (LiteRT)"
    
    override val hardwareStatus: String
        get() = if (isInitialized) "NPU [Accelerated]" else "Idle"
    
    private var engine: Engine? = null
    private var activeConversation: com.google.ai.edge.litertlm.Conversation? = null
    private var isInitialized = false

    override suspend fun initialize(modelPath: String, nCtx: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            circuitBreaker.execute("litert_engine") {
                ensureLibraryLoaded()
                Timber.i("Initializing LiteRT-LM Elite Engine with model: $modelPath")
                if (modelPath.isBlank() || !java.io.File(modelPath).exists()) {
                    throw java.io.FileNotFoundException("LiteRT-LM Model file not found or path is empty: $modelPath")
                }
                
                // If there was an existing engine, close it first to prevent native memory leaks
                try {
                    engine?.close()
                } catch (closeEx: Exception) {
                    // Ignored
                }
                engine = null
                isInitialized = false
                
                val config = EngineConfig(modelPath)
                engine = Engine(config)
                engine?.initialize()
                isInitialized = true
                
                // Record telemetry success
                SolarisTelemetry.record("litert_init", 1L, mapOf("model" to modelPath))
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LiteRT-LM Elite Engine via circuit breaker")
            isInitialized = false
            // Record telemetry failure
            SolarisTelemetry.record("litert_init_fail", 0L, mapOf("error" to (e.message ?: "Unknown")))
            false
        }
    }

    override fun generateResponse(
        prompt: String,
        topK: Int,
        topP: Float,
        temp: Float,
        maxTokens: Int,
        enableThinking: Boolean
    ): Flow<String> {
        // NOTE: LiteRt (Gemma 4 Elite) uses its own internal state management 
        // for generation length, but we accept maxTokens for API parity.
        return generateMultimodalResponse(prompt, null)
    }

    /**
     * Generates a multimodal response (text + image).
     * Wrapped in a resilient circuit-breaker wrapper to catch native/JNI crash cascades.
     */
    fun generateMultimodalResponse(prompt: String, image: android.graphics.Bitmap?): Flow<String> = flow {
        val currentEngine = engine ?: throw IllegalStateException("LiteRT Engine not initialized")
        
        // 🛡️ [SAR] Session Lifecycle: Close previous conversation before creating new one.
        // LiteRT only supports 1 session at a time — not closing causes FAILED_PRECONDITION.
        try {
            activeConversation?.close()
        } catch (e: Exception) {
            Timber.w(e, "⚠️ [LiteRT] Failed to close previous conversation (non-fatal)")
        }
        activeConversation = null
        
        val conversation = currentEngine.createConversation()
        activeConversation = conversation
        
        try {
            circuitBreaker.execute("litert_engine") {
                if (image != null) {
                    val stream = ByteArrayOutputStream()
                    image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                    val imageBytes = stream.toByteArray()
                    
                    val multimodalContents = Contents.of(
                        Content.ImageBytes(imageBytes),
                        Content.Text(prompt)
                    )
                    
                    emitAll(conversation.sendMessageAsync(multimodalContents).map { it.toString() })
                } else {
                    emitAll(conversation.sendMessageAsync(prompt).map { it.toString() })
                }
                // Record telemetry success
                SolarisTelemetry.record("litert_inference_success", 1L)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during LiteRT multimodal inference via circuit breaker")
            SolarisTelemetry.record("litert_inference_fail", 0L, mapOf("error" to (e.message ?: "Unknown")))
            // Clean up failed conversation to prevent stale session
            try { conversation.close() } catch (_: Exception) {}
            activeConversation = null
            throw e
        }
    }.flowOn(Dispatchers.Default)

    override fun release() {
        Timber.i("Releasing LiteRT-LM Elite Engine resources")
        try {
            activeConversation?.close()
        } catch (e: Exception) {
            // Ignored
        }
        activeConversation = null
        try {
            engine?.close()
        } catch (e: Exception) {
            // Ignored
        }
        engine = null
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized && try {
        // BUG FIX: Return false on exception, do not bypass the circuit breaker!
        circuitBreaker.allowRequest("litert_engine")
    } catch (e: Exception) {
        false
    }
}
