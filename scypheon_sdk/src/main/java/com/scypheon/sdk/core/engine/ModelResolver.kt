package com.scypheon.sdk.core.engine

import com.scypheon.sdk.core.utils.HardwarePreferences
import javax.inject.Inject
import javax.inject.Singleton

sealed class InferenceCapability {
    data class Full(val modelPath: String, val contextSize: Int) : InferenceCapability()
    data class Lightweight(val modelPath: String, val contextSize: Int) : InferenceCapability()
    object LocalHeuristicOnly : InferenceCapability() // Fallback to keyword/regex matching
    object NoModelsAvailable : InferenceCapability()
}

@Singleton
class ModelResolver @Inject constructor(
    private val registry: ModelRegistry,
    private val preferences: HardwarePreferences,
    private val integrityGuard: ModelIntegrityGuard
) {
    
    suspend fun resolveActiveCapability(): InferenceCapability {
        val models = registry.getAvailableModels()
        if (models.isEmpty()) return InferenceCapability.NoModelsAvailable

        val stableMem = preferences.getStableMemoryClass()
        
        // Strategy: Iterate through discovered models from heaviest to lightest
        for (candidate in models) {
            val safeCtx = MemoryBudgetCalculator.computeSafeContext(stableMem, candidate.sizeMb)
            
            // Minimum acceptable intelligence threshold: 512 context tokens
            if (safeCtx >= 512) {
                val isVerified = integrityGuard.verifyOrReject(candidate.path, null) // In prod, provide hash
                if (!isVerified) continue
                
                return if (safeCtx >= 1024) {
                    InferenceCapability.Full(candidate.path, safeCtx)
                } else {
                    InferenceCapability.Lightweight(candidate.path, safeCtx)
                }
            }
        }

        // If no models fit in RAM, fallback to heuristic mode
        return InferenceCapability.LocalHeuristicOnly
    }
}
