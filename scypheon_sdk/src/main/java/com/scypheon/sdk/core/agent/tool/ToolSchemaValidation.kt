package com.scypheon.sdk.core.agent.tool

/**
 * Result of a tool schema validation check.
 */
data class ToolSchemaValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)
