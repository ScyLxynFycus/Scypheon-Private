package com.scypheon.sdk.core.engine

import kotlinx.coroutines.flow.Flow

/**
 * BaseAiEngine defines the contract for all AI inference backends
 * (llama.cpp, LiteRT-LM, etc.) within the Scypheon ecosystem.
 */
interface BaseAiEngine {
    val engineId: String

    /**
     * Human-readable name shown in the UI (e.g., "Gemma 4 Elite").
     */
    var friendlyName: String
    val hardwareStatus: String

    /**
     * Initializes the engine with the provided model path.
     * This is typically where native libraries are loaded and RAM is allocated.
     */
    suspend fun initialize(modelPath: String, nCtx: Int = 4096): Boolean

    /**
     * Generates a streaming response for the given prompt.
     */
    fun generateResponse(
        prompt: String,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        maxTokens: Int = 1024
    ): Flow<String>

    /**
     * Releases native resources and frees up RAM.
     */
    fun release()

    /**
     * Returns true if the engine is currently initialized and ready for inference.
     */
    fun isReady(): Boolean
}
