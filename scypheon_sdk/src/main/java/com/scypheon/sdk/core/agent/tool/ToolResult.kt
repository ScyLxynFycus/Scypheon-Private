package com.scypheon.sdk.core.agent.tool

/**
 * Sealed class for structured tool execution results.
 */
sealed class ToolResult {
    data class Success(
        val data: Any?, 
        val latencyMs: Long = 0, 
        val metadata: Map<String, String> = emptyMap(),
        val summary: String? = null
    ) : ToolResult()

    data class Error(
        val reason: String, 
        val cause: Throwable? = null, 
        val latencyMs: Long = 0,
        val recoveryHints: List<String> = emptyList()
    ) : ToolResult()

    data class AwaitingApproval(
        val toolName: String, 
        val args: Map<String, Any?>, 
        val reason: String
    ) : ToolResult()

    data class Fallback(
        val data: Any?, 
        val source: FallbackSource, 
        val latencyMs: Long = 0
    ) : ToolResult()

    val isSuccess: Boolean get() = this is Success
    val isPending: Boolean get() = this is AwaitingApproval

    companion object {
        /**
         * Executes a block safely, capturing latency and exceptions.
         */
        inline fun executeSafely(block: () -> ToolResult): ToolResult {
            val start = System.currentTimeMillis()
            return try {
                val result = block()
                // Inject latency if not set
                when (result) {
                    is Success -> if (result.latencyMs == 0L) result.copy(latencyMs = System.currentTimeMillis() - start) else result
                    is Error -> if (result.latencyMs == 0L) result.copy(latencyMs = System.currentTimeMillis() - start) else result
                    is Fallback -> if (result.latencyMs == 0L) result.copy(latencyMs = System.currentTimeMillis() - start) else result
                    else -> result
                }
            } catch (e: Exception) {
                Error(
                    reason = e.message ?: "Unknown execution error",
                    cause = e,
                    latencyMs = System.currentTimeMillis() - start
                )
            }
        }
    }
}

enum class FallbackSource { STATIC_RULE, CACHED_RESPONSE, SAFE_DEFAULT }
