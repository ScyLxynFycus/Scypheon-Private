package com.scypheon.sdk.core.engine

import com.scypheon.sdk.core.annotations.SafetyCritical

/**
 * RuleBasedFallbackEngine: Lightweight fallback for thermal/battery throttling.
 * Provides basic safety logic when the LLM is gated.
 */
@SafetyCritical
interface RuleBasedFallbackEngine {
    suspend fun execute(): String
}
