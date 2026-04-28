package com.scypheon.sdk.core.memory

import kotlinx.coroutines.flow.StateFlow

/**
 * Enterprise Vector Engine Interface.
 * Allows swapping between LiteRT (TFLite) and Sandbox (Llama) embedding backends.
 */
interface IVectorEngine {
    enum class EngineState { Idle, Initializing, Ready, Failed }
    
    val state: StateFlow<EngineState>
    
    /**
     * Initializes the engine with the provided model path.
     */
    suspend fun initialize(modelPath: String? = null)
    
    /**
     * Generates a semantic vector for the given text.
     */
    suspend fun embedText(text: String): FloatArray?
    
    /**
     * Calculates cosine similarity between two vectors.
     */
    fun calculateCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        // Architect Directive: Defensive guard against empty vectors or dimension mismatch
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) {
            return 0.0f
        }
        
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        
        val denominator = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0.0f
    }
    
    /**
     * Closes the engine and frees resources.
     */
    fun close()
}
