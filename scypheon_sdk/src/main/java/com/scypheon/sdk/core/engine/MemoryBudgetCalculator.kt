package com.scypheon.sdk.core.engine

/**
 * Enterprise-grade Memory Budgeting for llama.cpp on Android.
 * Calculates dynamic n_ctx based on available RAM and model parameters.
 */
object MemoryBudgetCalculator {
    
    /**
     * Estimates KV Cache size in MB.
     * Approximation: 1.8MB per 1K context tokens per 1B parameters.
     */
    fun estimateKvCacheMb(modelSizeMb: Long, contextTokens: Int): Long {
        // Approximate parameter count from GGUF size (Q4_K_M/Q6_K baseline)
        val paramB = (modelSizeMb / 1024.0) * 1.8 
        return (paramB * (contextTokens / 1024.0) * 1.8).toLong()
    }

    /**
     * Computes the maximum safe context window given stable memory budget.
     */
    fun computeSafeContext(stableMemMb: Long, modelSizeMb: Long, minContext: Int = 512): Int {
        val nativeOverheadMb = 850L // llama.cpp runtime + stacks + system safety buffer
        val availableForKv = stableMemMb - modelSizeMb - nativeOverheadMb
        
        if (availableForKv <= 128) return minContext // Not enough for anything beyond minimal

        val paramB = (modelSizeMb / 1024.0) * 1.8
        // Reverse: ctx = (avail / (1.8 * paramB)) * 1024
        val maxCtx = ((availableForKv / (1.8 * paramB)) * 1024).toInt()
        
        // Align to 256 for optimal memory paging
        return maxCtx.coerceIn(minContext, 8192).let { (it / 256) * 256 }
    }
}
