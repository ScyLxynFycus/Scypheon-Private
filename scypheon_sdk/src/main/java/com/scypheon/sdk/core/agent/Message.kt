package com.scypheon.sdk.core.agent

/**
 * Represents a single message in the agentic conversation.
 * Mirrors Claude Code's message structure with support for thinking blocks and metadata.
 */
data class Message(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val isThinking: Boolean = false,
    val isMeta: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
