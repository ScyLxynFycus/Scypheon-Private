package com.scypheon.sdk.core.agent.tool

/**
 * Sealed class representing the outcome of an atomic tool execution.
 * Hardened for enterprise stability with support for grounding summaries and metadata.
 */
sealed class ToolResult {
    data class Success(
        val data: Any?,
        val summary: String? = null,
        val latencyMs: Long = 0L,
        val metadata: Map<String, String> = emptyMap()
    ) : ToolResult()

    data class Error(
        val reason: String,
        val cause: Throwable? = null,
        val latencyMs: Long = 0L,
        val recoveryHints: List<String> = emptyList()
    ) : ToolResult()

    data class Fallback(
        val data: Any?,
        val source: FallbackSource,
        val latencyMs: Long = 0L,
        val metadata: Map<String, String> = emptyMap()
    ) : ToolResult()

    data class AwaitingApproval(
        val toolName: String,
        val reason: String
    ) : ToolResult()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isFallback: Boolean get() = this is Fallback

    companion object {
        /**
         * Safely executes a block of code and wraps it in a robust ToolResult.
         */
        inline fun executeSafely(block: () -> Any?): ToolResult {
            val startTime = System.currentTimeMillis()
            return try {
                val result = block()
                if (result is ToolResult) return result
                Success(
                    data = result,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                Error(
                    reason = e.message ?: "Unknown execution error",
                    cause = e,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }
}

enum class FallbackSource {
    STATIC_RULE,
    CACHED_RESPONSE,
    DEFAULT_PROTOCOL
}
