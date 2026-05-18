package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.agent.skills.MedicalSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugInteractionTool @Inject constructor(
    private val medicalSkill: MedicalSkill
) : BaseTool() {
    override val name: String = "check_interaction"
    
    override val description: String = "Checks for potential drug-drug or drug-condition interactions. Mandatory before prescribing or recommending any medication."
    
    override fun isConcurrencySafe(args: Map<String, Any?>): Boolean = true

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "drug": {
              "type": "string",
              "description": "The name of the drug to check for interactions."
            }
          },
          "required": ["drug"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val drug = args["drug"]?.toString() ?: throw IllegalArgumentException("Missing drug parameter")
            val result = medicalSkill.checkInteraction(drug)
            ToolResult.Success(result, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Interaction check failed", e, System.currentTimeMillis() - start)
        }
    }

    override fun getActivityDescription(args: Map<String, Any?>): String = 
        "Checking interactions for: ${args["drug"]}"
}
