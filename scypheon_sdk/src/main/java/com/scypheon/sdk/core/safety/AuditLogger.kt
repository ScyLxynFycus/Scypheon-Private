package com.scypheon.sdk.core.safety

import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.security.AuditChain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    private val auditChain: AuditChain
) {
    suspend fun logToolInvocationRequest(sessionId: String, toolCalls: List<ToolCall>) {
        // Updated field name: name -> toolName
        val toolNames = toolCalls.map { it.toolName }.joinToString(", ")
        auditChain.logEvent("TOOL_INVOCATION_REQUEST", "Session: $sessionId | Tools: $toolNames")
    }

    suspend fun logExecutionResult(sessionId: String, result: ToolResult) {
        val payload = when (result) {
            is ToolResult.Success -> "SUCCESS | Latency: ${result.latencyMs}ms"
            is ToolResult.Error -> "ERROR | Reason: ${result.reason} | Cause: ${result.cause}"
            is ToolResult.Fallback -> "FALLBACK | Source: ${result.source}"
            is ToolResult.AwaitingApproval -> "AWAITING_APPROVAL | Tool: ${result.toolName} | Reason: ${result.reason}"
        }
        auditChain.logEvent("TOOL_EXECUTION_RESULT", "Session: $sessionId | $payload")
    }

    suspend fun logCriticalFailure(sessionId: String, toolName: String, error: String) {
        auditChain.logEvent("CRITICAL_TOOL_FAILURE", "Session: $sessionId | Tool: $toolName | Error: $error")
    }
}
