package com.scypheon.sdk.core.resilience

<<<<<<< Updated upstream
import com.scypheon.sdk.core.agent.tool.*

/**
 * FallbackEngine: Base interface for the resilience recovery system.
 */
interface FallbackEngine {
    /**
     * Attempts to recover from a tool failure using tiered logic.
     */
=======
import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.ToolResult

/**
 * Enterprise-grade fallback engine for graceful degradation.
 * 
 * Implements 4-tier cascade: Graph -> Cache -> Static -> Honest
 * 
 * Design principles:
 * - NEVER hallucinate (no LLM in fallback path)
 * - NEVER bypass safety
 * - ALWAYS explain failure reason
 * - ALWAYS offer recovery path
 */
interface FallbackEngine {
>>>>>>> Stashed changes
    suspend fun recover(
        originalCall: ToolCall,
        failureReason: FailureCategory
    ): ToolResult
}

<<<<<<< Updated upstream
/**
 * DefaultFallbackEngine: Basic implementation that returns a standard error.
 */
open class DefaultFallbackEngine(
    private val toolRegistry: ToolRegistry
) : FallbackEngine {
    
    override suspend fun recover(
        originalCall: ToolCall,
        failureReason: FailureCategory
    ): ToolResult {
        val start = System.currentTimeMillis()
        return ToolResult.Error(
            reason = failureReason.userMessage,
            cause = null,
            latencyMs = System.currentTimeMillis() - start
        )
    }

    // Keep legacy handle for compatibility if needed elsewhere
    suspend fun handle(call: ToolCall, error: String?, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        return ToolResult.Error(
            reason = error ?: "No fallback available for ${call.toolName}",
            cause = null,
            latencyMs = System.currentTimeMillis() - start
        )
    }
=======
enum class FailureCategory(val displayName: String, val userMessage: String) {
    THERMAL_THROTTLE(
        "Thermal Protection",
        "Device temperature is too high for AI processing. Please wait 30 seconds."
    ),
    ENGINE_CRASH(
        "Engine Recovery",
        "AI engine is restarting. Your request will be processed shortly."
    ),
    TIMEOUT(
        "Processing Timeout",
        "The request is taking longer than expected. Please simplify your question."
    ),
    CIRCUIT_OPEN(
        "Service Degraded",
        "AI service is temporarily unavailable due to repeated failures."
    ),
    OOM(
        "Memory Pressure",
        "Not enough memory to process this request. Try closing other apps."
    ),
    UNKNOWN(
        "Temporary Issue",
        "An unexpected issue occurred. Please try again."
    )
>>>>>>> Stashed changes
}
