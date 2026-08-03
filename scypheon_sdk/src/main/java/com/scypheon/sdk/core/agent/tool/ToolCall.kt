package com.scypheon.sdk.core.agent.tool

import java.util.UUID

/**
 * Data class representing a tool execution request from the LLM.
 */
data class ToolCall(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val callId: String = UUID.randomUUID().toString()
)
