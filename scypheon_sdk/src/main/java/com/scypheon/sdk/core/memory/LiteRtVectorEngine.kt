package com.scypheon.sdk.core.memory

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import timber.log.Timber
import java.io.File
import java.nio.MappedByteBuffer
import java.io.FileInputStream
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 100% Google-Native Vector Embeddings for Semantic Search.
 * Implementation using MediaPipe (LiteRT) TextEmbedder.
 * Conforms to IVectorEngine for dynamic memory swapping.
 */
@Singleton
class LiteRtVectorEngine @Inject constructor(@ApplicationContext private val context: Context) : IVectorEngine {

    private var textEmbedder: TextEmbedder? = null

    // Keep FileInputStream alive so the MappedByteBuffer backing channel remains valid
    private var modelFileStream: FileInputStream? = null
    private var modelMappedBuffer: MappedByteBuffer? = null
    
    private val _state = MutableStateFlow<IVectorEngine.EngineState>(IVectorEngine.EngineState.Idle)
    override val state = _state.asStateFlow()

    private var failureCount = 0
    private var circuitOpen = false

    companion object {
        /** Minimum expected size for .gateway_sync.bin (~186 MB uncompressed) */
        private const val GATEWAY_SYNC_MIN_SIZE = 190_000_000L
    }

    /**
     * Loads the embedding model (hotswappable) using Coroutines.
     * Implements 5s timeout, 3x exponential backoff, and CircuitBreaker logic.
     *  Default Shadow Path: context.filesDir/.shm/.gateway_sync.bin
     */
    override suspend fun initialize(modelPath: String?) = withContext(Dispatchers.IO) {
        var finalPath = modelPath
        if (finalPath == null) {
            val extracted = com.scypheon.sdk.core.utils.AssetExtractor.extractAndVerify(context, ".gateway_sync.bin")
            if (extracted) {
                finalPath = com.scypheon.sdk.core.utils.AssetExtractor.getModelPath(context, ".gateway_sync.bin")
            }
        }
        
        if (finalPath.isNullOrEmpty()) {
            Timber.i("[PHOENIX] LiteRT Embedding asset (.gateway_sync.bin) not found in stealth storage. Attempting shadow sync...")
            val syncSuccess = com.scypheon.sdk.core.utils.ShadowSyncManager.ensureSynced(context)
            if (syncSuccess) {
                finalPath = com.scypheon.sdk.core.utils.AssetExtractor.getModelPath(context, ".gateway_sync.bin")
            }
        }

        if (finalPath.isNullOrEmpty()) {
            Timber.e("[PHOENIX] LiteRT Embedding asset (.gateway_sync.bin) not found in stealth storage.")
            _state.value = IVectorEngine.EngineState.Failed
            return@withContext
        }
        
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

                        // Pre-flight size validation to catch truncated assets early
                        val fileSize = file.length()
                        Timber.i("[PHOENIX] Model pre-flight: path=$finalPath, size=$fileSize bytes")
                        if (fileSize < GATEWAY_SYNC_MIN_SIZE) {
                            Timber.e("[PHOENIX] TRUNCATED MODEL DETECTED: $fileSize bytes < $GATEWAY_SYNC_MIN_SIZE minimum. Purging corrupt file.")
                            file.delete()
                            throw IOException("Model file truncated ($fileSize bytes). Deleted for re-extraction on next launch.")
                        }

                        // Keep FIS open as class field — MappedByteBuffer requires backing channel to stay alive
                        val fis = FileInputStream(file)
                        val channel = fis.channel
                        val mappedBuffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
                        modelFileStream = fis
                        modelMappedBuffer = mappedBuffer
                        baseOptionsBuilder.setModelAssetBuffer(mappedBuffer)
                    } else {
                        baseOptionsBuilder.setModelAssetPath(finalPath)
                    }

                    // [SAR] Acceleration Protocol: Attempt GPU/NNAPI first
                    try {
                        baseOptionsBuilder.setDelegate(Delegate.GPU)
                        val options = TextEmbedder.TextEmbedderOptions.builder()
                            .setBaseOptions(baseOptionsBuilder.build())
                            .setQuantize(false)
                            .build()
                        textEmbedder = TextEmbedder.createFromOptions(context, options)
                        Timber.i("[PHOENIX] LiteRtVectorEngine loaded (Delegate: GPU/NNAPI)")
                    } catch (e: Exception) {
                        Timber.w("[PHOENIX] GPU delegate failed ($e). Falling back to CPU.")
                        baseOptionsBuilder.setDelegate(Delegate.CPU)
                        val options = TextEmbedder.TextEmbedderOptions.builder()
                            .setBaseOptions(baseOptionsBuilder.build())
                            .setQuantize(false)
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

            // Clean up any partially-opened resources from this failed attempt
            modelFileStream?.runCatching { close() }
            modelFileStream = null
            modelMappedBuffer = null

            // Auto-purge: if the error is a truncation/buffer mismatch, delete the file
            // so AssetExtractor re-extracts a clean copy on next launch
            if (exception?.message?.contains("truncated", ignoreCase = true) == true ||
                exception?.message?.contains("buffer", ignoreCase = true) == true) {
                if (!finalPath.isNullOrEmpty() && finalPath.startsWith("/")) {
                    val corruptFile = File(finalPath)
                    if (corruptFile.exists()) {
                        corruptFile.delete()
                        Timber.w("[PHOENIX] Auto-purged corrupt model: $finalPath")
                    }
                }
            }
            
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
        // Release the memory-mapped buffer and its backing stream
        modelMappedBuffer = null
        modelFileStream?.runCatching { close() }
        modelFileStream = null
        _state.value = IVectorEngine.EngineState.Idle
    }
}
