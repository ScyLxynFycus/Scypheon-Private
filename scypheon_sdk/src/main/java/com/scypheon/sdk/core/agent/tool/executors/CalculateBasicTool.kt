package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.agent.skills.MathSkill
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalculateBasicTool: High-precision mathematical engine for humanitarian data.
 */
@Singleton
class CalculateBasicTool @Inject constructor(
    private val mathSkill: MathSkill
) : BaseTool() {
    override val name: String = "calculate_basic"
    
    override val description: String = "Performs basic mathematical calculations. Use this for dose calculations, budget balancing, or geometric data processing."
    
    override fun isConcurrencySafe(args: Map<String, Any?>): Boolean = true
    
    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "expression": {
              "type": "string",
              "description": "The mathematical expression to evaluate (e.g., '120 * 0.5')"
            }
          },
          "required": ["expression"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        val expression = args["expression"]?.toString()
            ?: return ToolResult.Error("Missing expression", null, 0)
        
        return try {
            val result = mathSkill.calculate(expression)
            ToolResult.Success(result, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Calculation failed", e, System.currentTimeMillis() - start)
        }
    }

    override fun getActivityDescription(args: Map<String, Any?>): String = 
        "Calculating: ${args["expression"]}"
}
