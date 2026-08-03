package com.scypheon.sdk.core.agent.tool

/**
 * Sealed class untuk hasil eksekusi tool yang aman dan terstruktur.
 */
sealed class ToolResult {
    data class Success(val data: Any?, val latencyMs: Long, val meta: Map<String, String> = emptyMap()) : ToolResult()
    data class Error(val reason: String, val cause: Throwable?, val latencyMs: Long) : ToolResult()
    data class AwaitingApproval(val toolName: String, val args: Map<String, Any?>, val reason: String) : ToolResult()
    data class Fallback(val data: Any?, val source: FallbackSource, val latencyMs: Long) : ToolResult()

    val isSuccess: Boolean get() = this is Success
    val isPending: Boolean get() = this is AwaitingApproval
}

enum class FallbackSource { STATIC_RULE, CACHED_RESPONSE, SAFE_DEFAULT }
