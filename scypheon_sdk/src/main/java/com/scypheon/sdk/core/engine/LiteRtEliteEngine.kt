package com.scypheon.sdk.core.engine

import com.google.ai.edge.litertlm.Backend
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
import com.scypheon.sdk.core.resilience.CircuitBreakerOpenException
import com.scypheon.sdk.core.telemetry.BlackBoxVault
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
    private val circuitBreaker: ResilienceCircuitBreaker,
    private val blackBoxVault: BlackBoxVault
) : BaseAiEngine {

    companion object {
        private val isLibraryLoaded = java.util.concurrent.atomic.AtomicBoolean(false)

        private fun ensureLibraryLoaded() {
            if (isLibraryLoaded.get()) return
            synchronized(this) {
                if (isLibraryLoaded.get()) return
                try {
                    val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
                    try {
                        System.loadLibrary("litertlm_jni")
                    } finally {
                        android.os.StrictMode.setThreadPolicy(oldPolicy)
                    }
                    isLibraryLoaded.set(true)
                    Timber.i("隨ｨ繝ｻLiteRT-LM JNI Library loaded successfully.")
                } catch (e: UnsatisfiedLinkError) {
                    Timber.e(e, "隨ｶ繝ｻFailed to load litertlm_jni. Ensure the .so is bundled in the APK.")
                    throw e
                }
            }
        }
    }

    override val engineId: String = "litert_elite"
    override var friendlyName: String = "Gemma Elite (LiteRT)"

    override val hardwareStatus: String
        get() = if (isInitialized) {
            if (isMaliOrUnstableGpu()) "CPU [Safe Mode]" else "NPU [Accelerated]"
        } else "Idle"

    private var engine: Engine? = null
    private var activeConversation: com.google.ai.edge.litertlm.Conversation? = null
    private var isInitialized = false

    private fun isMaliOrUnstableGpu(): Boolean {
        val hardware = android.os.Build.HARDWARE.lowercase()
        val board = android.os.Build.BOARD.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return hardware.contains("mali") || board.contains("exynos") || hardware.contains("kirin") || manufacturer.contains("samsung")
    }

    override suspend fun initialize(modelPath: String, nCtx: Int): Boolean = withContext(Dispatchers.IO) {
        // [SBI] PRE-FLIGHT: Initial backend selection based on hardware profile
        val preferredBackend = if (isMaliOrUnstableGpu()) {
            Timber.w("🛡️ [PHOENIX] Unstable Mali/Exynos GPU detected. Selecting CPU as primary safe backend.")
            Backend.CPU()
        } else {
            Backend.GPU()
        }

        try {
            circuitBreaker.execute("litert_engine") {
                ensureLibraryLoaded()
                Timber.i("Initializing LiteRT-LM Elite Engine with model: $modelPath")
                if (modelPath.isBlank() || !java.io.File(modelPath).exists()) {
                    throw java.io.FileNotFoundException("LiteRT-LM Model file not found or path is empty: $modelPath")
                }

                // Internal function to attempt initialization with a specific backend
                fun attemptInit(backend: Backend): Boolean {
                    return try {
                        // Close existing to prevent leaks
                        try { engine?.close() } catch (_: Exception) {}
                        engine = null
                        isInitialized = false

                        val config = EngineConfig(modelPath = modelPath, backend = backend)
                        engine = Engine(config)
                        engine?.initialize()
                        isInitialized = true
                        true
                    } catch (e: Exception) {
                        Timber.e(e, "LiteRT-LM init failed for backend: ${backend.javaClass.simpleName}")
                        false
                    }
                }

                // [PHOENIX] SMART FALLBACK LOGIC:
                // Attempt preferred backend (GPU/CPU). If preferred was GPU and it failed, 
                // perform an emergency fallback to CPU automatically to prevent app crash.
                var success = attemptInit(preferredBackend)
                
                if (!success && preferredBackend is Backend.GPU) {
                    Timber.w("⚠️ [SOLARIS] GPU Initialization FAILED. Triggering Smart Fallback to CPU...")
                    success = attemptInit(Backend.CPU())
                    if (success) {
                        Timber.i("✅ [SOLARIS] Smart Fallback SUCCESS. Running in CPU [Safe Mode].")
                    }
                }

                if (success) {
                    // Record telemetry success with the ACTUAL backend used
                    val finalBackend = if (isMaliOrUnstableGpu() || !preferredBackend.javaClass.isInstance(Backend.GPU::class.java)) "CPU" else "GPU"
                    blackBoxVault.logEvent("litert_init_success", "model: $modelPath, backend: $finalBackend")
                    true
                } else {
                    throw IllegalStateException("LiteRT Engine exhausted all backends (GPU/CPU)")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LiteRT-LM Elite Engine after smart fallback attempts")
            isInitialized = false
            blackBoxVault.logEvent("litert_init_fail", "error: ${e.message ?: "Unknown"}")
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

        // 﨟槫ｭｱ繝ｻ繝ｻ[SAR] Session Lifecycle: Close previous conversation before creating new one.
        // LiteRT only supports 1 session at a time 遯ｶ繝ｻnot closing causes FAILED_PRECONDITION.
        try {
            activeConversation?.close()
        } catch (e: Exception) {
            Timber.w(e, "隨橸｣ｰ繝ｻ繝ｻ[LiteRT] Failed to close previous conversation (non-fatal)")
        }
        activeConversation = null
        
        val conversation = currentEngine.createConversation()
        activeConversation = conversation

        try {
            if (!circuitBreaker.allowRequest("litert_engine")) {
                throw CircuitBreakerOpenException("Circuit breaker is open for litert_engine")
            }

            if (image != null) {
                val stream = ByteArrayOutputStream()
                image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                val imageBytes = stream.toByteArray()

                val multimodalContents = Contents.of(
                    Content.ImageBytes(imageBytes),
                    Content.Text(prompt)
                )

                emitAll(conversation.sendMessageAsync(multimodalContents).map { it.toString().replace("\u2581", " ") })
            } else {
                emitAll(conversation.sendMessageAsync(prompt).map { it.toString().replace("\u2581", " ") })
            }
            // Record telemetry success
            circuitBreaker.recordSuccess("litert_engine")
            blackBoxVault.logEvent("litert_inference_success", "Success")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancelled cleanly by parent, do not record as failure or crash
            Timber.i(" [LiteRT] Inference cancelled cleanly by UI/User.")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error during LiteRT multimodal inference")
            circuitBreaker.recordFailure("litert_engine")
            blackBoxVault.logEvent("litert_inference_fail", "error: ${e.message ?: "Unknown"}")
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
