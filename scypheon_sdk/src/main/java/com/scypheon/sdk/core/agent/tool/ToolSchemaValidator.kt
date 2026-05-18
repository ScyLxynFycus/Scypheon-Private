package com.scypheon.sdk.core.agent.tool

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolSchemaValidator @Inject constructor() {
    fun validate(toolName: String, arguments: Map<String, Any?>): ToolSchemaValidation {
        // Basic implementation - can be extended with JSON Schema later
        return ToolSchemaValidation(isValid = true)
    }
}
