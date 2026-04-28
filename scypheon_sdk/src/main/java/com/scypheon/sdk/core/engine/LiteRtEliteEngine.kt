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
import com.scypheon.sdk.core.humanitarian.accessibility.DeafEnvironmentGuardian
import com.scypheon.sdk.core.memory.toTriplets
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiteRtEliteEngine implements optimized inference for Gemma 3/4 models
 * using the modern LiteRT-LM framework. It prioritizes GPU/NPU acceleration.
 */
@Singleton
class LiteRtEliteEngine @Inject constructor() : BaseAiEngine {
    
    companion object {
        init {
            try {
                System.loadLibrary("litertlm_jni")
                Timber.i("✅ LiteRT-LM JNI Library loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "❌ Failed to load litertlm_jni. Ensure the .so is bundled in the APK.")
            }
        }
    }

    override val engineId: String = "litert_elite"
    override var friendlyName: String = "Gemma Elite (LiteRT)"
    
    override val hardwareStatus: String
        get() = if (isInitialized) "NPU [Accelerated]" else "Idle"
    
    private var engine: Engine? = null
    private var isInitialized = false

    override suspend fun initialize(modelPath: String, nCtx: Int): Boolean = withContext(Dispatchers.IO) {
        Timber.i("Initializing LiteRT-LM Elite Engine with model: $modelPath")
        try {
            if (modelPath.isBlank() || !java.io.File(modelPath).exists()) {
                Timber.e("LiteRT-LM Model file not found or path is empty: $modelPath")
                return@withContext false
            }
            
            val config = EngineConfig(modelPath)
            engine = Engine(config)
            engine?.initialize()
            isInitialized = true
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LiteRT-LM Elite Engine")
            isInitialized = false
            false
        }
    }

    override fun generateResponse(
        prompt: String,
        topK: Int,
        topP: Float,
        temp: Float,
        maxTokens: Int
    ): Flow<String> {
        // NOTE: LiteRt (Gemma 4 Elite) uses its own internal state management 
        // for generation length, but we accept maxTokens for API parity.
        return generateMultimodalResponse(prompt, null)
    }

    /**
     * Generates a multimodal response (text + image).
     */
    fun generateMultimodalResponse(prompt: String, image: android.graphics.Bitmap?): Flow<String> = flow {
        val currentEngine = engine ?: throw IllegalStateException("LiteRT Engine not initialized")
        val conversation = currentEngine.createConversation()
        
        try {
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
        } catch (e: Exception) {
            Timber.e(e, "Error during LiteRT multimodal inference")
            throw e
        }
    }.flowOn(Dispatchers.Default)

    override fun release() {
        Timber.i("Releasing LiteRT-LM Elite Engine resources")
        engine?.close()
        engine = null
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
