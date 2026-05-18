package com.scypheon.sdk.core.resilience

import com.scypheon.sdk.core.agent.tool.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FallbackEngine @Inject constructor(
    private val toolRegistry: ToolRegistry
) {
    suspend fun handle(call: ToolCall, error: String?, context: ExecutionContext): ToolResult {
        val tool = toolRegistry.resolve(call.toolName)
        val start = System.currentTimeMillis()
        
        return ToolResult.Error(
            reason = error ?: "No fallback available for ${call.toolName}",
            cause = null,
            latencyMs = System.currentTimeMillis() - start
        )
    }
}
