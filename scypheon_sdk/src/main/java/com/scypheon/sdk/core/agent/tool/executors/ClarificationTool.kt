package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClarificationTool @Inject constructor() : BaseTool() {
    override val name: String = "request_clarification"
    
    override val description: String = "Asks the user for missing information required for a skill. Use this when the initial query is too vague (e.g., 'I feel sick' without symptoms)."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "missing_fields": {
              "type": "string",
              "description": "The specific information needed from the user."
            }
          },
          "required": ["missing_fields"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        val fields = args["missing_fields"] ?: "vitals, onset, severity"
        return ToolResult.Success(
            "CLARIFY_REQUEST: Please provide [$fields] for accurate triage.", 
            System.currentTimeMillis() - start
        )
    }
}
