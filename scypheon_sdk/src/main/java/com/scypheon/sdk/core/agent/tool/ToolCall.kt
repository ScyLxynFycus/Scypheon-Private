package com.scypheon.sdk.core.agent.tool

import java.util.UUID

/**
 * Data class representing a tool execution request from the LLM.
 */
data class ToolCall(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val callId: String = UUID.randomUUID().toString(),
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ToolCall
        if (toolName != other.toolName) return false
        if (arguments != other.arguments) return false
        if (callId != other.callId) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = toolName.hashCode()
        result = 31 * result + arguments.hashCode()
        result = 31 * result + callId.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
