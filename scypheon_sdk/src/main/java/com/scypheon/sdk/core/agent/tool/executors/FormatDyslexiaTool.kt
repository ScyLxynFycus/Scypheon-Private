package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.agent.skills.AccessibilitySkill
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatDyslexiaTool @Inject constructor(
    private val accessibilitySkill: AccessibilitySkill
) : BaseTool() {
    override val name: String = "format_dyslexia"
    
    override val description: String = "Formats the given text to be more readable for users with dyslexia. Uses specialized fonts (OpenDyslexic) and spacing."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "text": {
              "type": "string",
              "description": "The text to format."
            }
          },
          "required": ["text"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        val text = args["text"]?.toString()
            ?: return ToolResult.Error("Missing text parameter", null, 0)
        
        return try {
            val formatted = accessibilitySkill.formatForDyslexia(text)
            ToolResult.Success("FORMATTED_TEXT:\n$formatted", latencyMs = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Formatting failed", e, System.currentTimeMillis() - start)
        }
    }

    override fun getActivityDescription(args: Map<String, Any?>): String = 
        "Applying dyslexia-friendly formatting..."
}
