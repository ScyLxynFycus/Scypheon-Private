package com.scypheon.sdk.core.memory

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout

/**
 * 100% Google-Native Vector Embeddings for Semantic Search.
 * Implementation using MediaPipe (LiteRT) TextEmbedder.
 * Conforms to IVectorEngine for dynamic memory swapping.
 */
class LiteRtVectorEngine(private val context: Context) : IVectorEngine {

    private var textEmbedder: TextEmbedder? = null
    
    private val _state = MutableStateFlow<IVectorEngine.EngineState>(IVectorEngine.EngineState.Idle)
    override val state = _state.asStateFlow()

    private var failureCount = 0
    private var circuitOpen = false

    /**
     * Loads the embedding model (hotswappable) using Coroutines.
     * Implements 5s timeout, 3x exponential backoff, and CircuitBreaker logic.
     *  Default Shadow Path: context.filesDir/.shm/.gateway_sync.bin
     */
    override suspend fun initialize(modelPath: String?) = withContext(Dispatchers.IO) {
        val finalPath = modelPath ?: "${context.filesDir.absolutePath}/.shm/.gateway_sync.bin"
        
        if (circuitOpen) {
            Timber.e("[PHOENIX] LiteRT circuit breaker is OPEN. Initialization aborted.")
            _state.value = IVectorEngine.EngineState.Failed
            return@withContext
        }

        _state.value = IVectorEngine.EngineState.Initializing
        var attempt = 1
        var delayMs = 500L

        while (attempt <= 3) {
            val result = kotlin.runCatching {
                withTimeout(5000) {
                    val baseOptionsBuilder = BaseOptions.builder()
                    if (finalPath.startsWith("/")) {
                        val file = File(finalPath)
                        if (!file.exists()) throw IOException("Model file not found: $finalPath")
                        
                        FileInputStream(file).use { fis ->
                            val channel = fis.channel
                            val buffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
                            baseOptionsBuilder.setModelAssetBuffer(buffer)
                        }
                    } else {
                        baseOptionsBuilder.setModelAssetPath(finalPath)
                    }

                    // [SAR] Acceleration Protocol: Attempt GPU/NNAPI first
                    try {
                        baseOptionsBuilder.setDelegate(Delegate.GPU)
                        val options = TextEmbedder.TextEmbedderOptions.builder()
                            .setBaseOptions(baseOptionsBuilder.build())
                            .setQuantize(true)
                            .build()
                        textEmbedder = TextEmbedder.createFromOptions(context, options)
                        Timber.i("[PHOENIX] LiteRtVectorEngine loaded (Delegate: GPU/NNAPI)")
                    } catch (e: Exception) {
                        Timber.w("[PHOENIX] GPU delegate failed ($e). Falling back to CPU.")
                        baseOptionsBuilder.setDelegate(Delegate.CPU)
                        val options = TextEmbedder.TextEmbedderOptions.builder()
                            .setBaseOptions(baseOptionsBuilder.build())
                            .setQuantize(true)
                            .build()
                        textEmbedder = TextEmbedder.createFromOptions(context, options)
                        Timber.i("[PHOENIX] LiteRtVectorEngine loaded (Delegate: CPU)")
                    }
                    
                    _state.value = IVectorEngine.EngineState.Ready
                    failureCount = 0
                }
            }

            if (result.isSuccess) return@withContext

            val exception = result.exceptionOrNull()
            Timber.e(exception, "[PHOENIX] LiteRtVectorEngine load failure (Attempt $attempt/3)")
            
            // BUG FIX: increment inside loop so circuit breaker trips after 3 total failures,
            // not after 3 full retry cycles (which would require 9 attempts).
            failureCount++
            if (failureCount >= 3) {
                circuitOpen = true
                Timber.e("[PHOENIX] LiteRT circuit breaker ACTIVATED. Disabling embedding engine.")
                _state.value = IVectorEngine.EngineState.Failed
                return@withContext
            }

            attempt++
            if (attempt <= 3) {
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2
            } else {
                _state.value = IVectorEngine.EngineState.Failed
            }
        }
    }

    /**
     * Converts a string of text into a high-dimensional mathematical vector
     * for offline semantic similarity search.
     */
    override suspend fun embedText(text: String): FloatArray? {
        if (_state.value != IVectorEngine.EngineState.Ready || textEmbedder == null) {
            Timber.e("LiteRtVectorEngine is not ready. Current state: ${_state.value}")
            return null
        }

        return try {
            val result = textEmbedder?.embed(text)
            val embedding = result?.embeddingResult()?.embeddings()?.firstOrNull()
            embedding?.floatEmbedding()
        } catch (e: Exception) {
            Timber.e(e, "Failed to embed text: $text")
            null
        }
    }

    override fun close() {
        textEmbedder?.close()
        textEmbedder = null
        _state.value = IVectorEngine.EngineState.Idle
    }
}
