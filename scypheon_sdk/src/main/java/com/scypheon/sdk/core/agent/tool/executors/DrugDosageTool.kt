package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.agent.skills.MedicalSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugDosageTool @Inject constructor(
    private val medicalSkill: MedicalSkill
) : BaseTool() {
    override val name: String = "get_drug_dosage"
    
    override val description: String = "Retrieves standard dosage information for a specific drug and age group. Must be verified via ClinicalValidator."
    
    override fun isConcurrencySafe(args: Map<String, Any?>): Boolean = true

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "drug": {
              "type": "string",
              "description": "The name of the drug."
            },
            "age_group": {
              "type": "string",
              "enum": ["infant", "child", "adult", "elderly"],
              "description": "The target age group for the dosage."
            }
          },
          "required": ["drug"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val drug = args["drug"]?.toString() ?: throw IllegalArgumentException("Missing drug parameter")
            val ageGroup = args["age_group"]?.toString() ?: "adult"
            
            val result = medicalSkill.getDosage(drug, ageGroup)
            ToolResult.Success(result, latencyMs = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Dosage lookup failed", e, System.currentTimeMillis() - start)
        }
    }

    override fun getActivityDescription(args: Map<String, Any?>): String = 
        "Looking up dosage for: ${args["drug"]} (${args["age_group"] ?: "adult"})"
}
