package com.scypheon.app.data.local

import com.scypheon.sdk.core.engine.RuleBasedFallbackEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleBasedFallbackEngineImpl @Inject constructor() : RuleBasedFallbackEngine {
    override suspend fun execute(): String {
        return "⚠️ System Performance Throttled. Switching to rule-based safety verification. Please ensure the device is in a cool environment and has sufficient battery."
    }
}
