package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.SessionContext

// --- Supporting Interfaces & Enums for AgenticRouter ---

enum class SafetyVerdict { SAFE, FLAGGED, BLOCKED }

interface SafetyPipeline {
    suspend fun evaluateInput(query: String, session: SessionContext): SafetyVerdict
}

interface SystemMonitor {
    suspend fun captureSnapshot(): DeviceEnvironment
}

sealed class FinalResponse {
    data class Success(val text: String, val traceId: String, val source: ResponseSource) : FinalResponse()
    data class Blocked(val reason: String, val traceId: String) : FinalResponse()
    data class Error(val fallbackMessage: String, val traceId: String) : FinalResponse()
}

enum class ResponseSource { OODA_FAST_PATH, ORIGA_DEEP_PATH, SAFE_FALLBACK }

// Extending OutputValidator from ActStep
interface RouterOutputValidator {
    data class FinalValidationResult(
        val isValid: Boolean,
        val reason: String,
        val piiDetected: Boolean = false,
        val hallucinationScore: Float = 0.0f
    )
    suspend fun validateFinalResponse(text: String, env: DeviceEnvironment): FinalValidationResult
}

// Extending AuditLogger
interface RouterAuditLogger {
    fun logSecurityBlock(traceId: String, query: String, reason: String)
    fun logPipelineFailure(traceId: String, cause: Throwable?)
    fun logDeepReasoningSuccess(traceId: String, reason: String)
}
