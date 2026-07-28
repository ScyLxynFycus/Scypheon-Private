package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.agent.skills.MathSkill
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalculateBasicTool: High-precision mathematical engine for humanitarian data.
 * Implements Socratic guidance for STEM tutoring contexts.
 */
@Singleton
class CalculateBasicTool @Inject constructor(
    private val mathSkill: MathSkill
) : BaseTool() {
    override val name: String = "calculate_basic"

    override val triggerDescription: String = "Performs mathematical calculations."; override val description: String = "Performs mathematical calculations. Supporting Socratic mode for STEM tutoring."

    override fun isConcurrencySafe(args: Map<String, Any?>): Boolean = true

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "expression": {
              "type": "string",
              "description": "The mathematical expression to evaluate (e.g., '120 * 0.5')"
            },
            "socratic_guide": {
              "type": "boolean",
              "description": "If true, returns hint and steps instead of just the final answer."
            }
          },
          "required": ["expression"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        val expression = args["expression"]?.toString()
            ?: return ToolResult.Error("Missing expression", null, 0)
        
        val isSocratic = args["socratic_guide"] as? Boolean ?: false

        return try {
            val result = mathSkill.calculate(expression)
            
            if (isSocratic) {
                // Return a structure that encourages the LLM to explain the steps
                ToolResult.Success(mapOf(
                    "answer" to result,
                    "guidance" to "Tutor Mode: Explain the logic of '$expression' step-by-step. Do not just show the result.",
                    "status" to "STEM_SOCRATIC_READY"
                ), latencyMs = System.currentTimeMillis() - start)
            } else {
                ToolResult.Success(result, latencyMs = System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Calculation failed", e, System.currentTimeMillis() - start)
        }
    }

    override fun getActivityDescription(args: Map<String, Any?>): String =
        "Calculating: ${args["expression"]}"
}

