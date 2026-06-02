package com.scypheon.sdk.core.resilience

import com.scypheon.sdk.core.agent.tool.*

/**
 * FallbackEngine: Base interface for the resilience recovery system.
 */
interface FallbackEngine {
    /**
     * Attempts to recover from a tool failure using tiered logic.
     */
    suspend fun recover(
        originalCall: ToolCall,
        failureReason: FailureCategory
    ): ToolResult
}

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
}
