package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.humanitarian.medical.MedicalTriageGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirstAidTool @Inject constructor(
    private val triageGateway: MedicalTriageGateway
) : BaseTool() {
    override val name: String = "get_first_aid"
    
    override val description: String = "Retrieves verified first-aid protocols for a given symptom or emergency. Critical for life-saving guidance in disaster zones."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "symptom": {
              "type": "string",
              "description": "The symptom or emergency condition (e.g., 'heavy bleeding', 'snake bite')."
            }
          },
          "required": ["symptom"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val symptom = args["symptom"]?.toString() ?: return@withContext ToolResult.Error(
                reason = "Missing required parameter: symptom",
                cause = null,
                latencyMs = System.currentTimeMillis() - start
            )
            
            val protocol = triageGateway.getFirstAidProtocol(symptom)
            
            if (protocol != null) {
                ToolResult.Success(
                    data = protocol,
                    latencyMs = System.currentTimeMillis() - start,
                    meta = mapOf("symptom" to symptom)
                )
            } else {
                ToolResult.Fallback(
                    data = "No verified first-aid protocol found for '$symptom'. Seek professional medical assistance.",
                    source = FallbackSource.STATIC_RULE,
                    latencyMs = System.currentTimeMillis() - start
                )
            }
        } catch (e: Exception) {
            ToolResult.Error(
                reason = "Execution failed: ${e.message}",
                cause = e,
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    override fun getActivityDescription(args: Map<String, Any?>): String = 
        "Retrieving first-aid protocol for: ${args["symptom"]}"
}
