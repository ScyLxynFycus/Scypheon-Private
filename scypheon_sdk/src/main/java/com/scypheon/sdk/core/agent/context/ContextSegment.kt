package com.scypheon.sdk.core.agent.context

enum class ContextPriority { LOW, HIGH, CRITICAL }

data class ContextSegment(
    val id: String,
    val text: String,
    val priority: ContextPriority,
    val tokens: Int,
    val timestamp: Long
)
