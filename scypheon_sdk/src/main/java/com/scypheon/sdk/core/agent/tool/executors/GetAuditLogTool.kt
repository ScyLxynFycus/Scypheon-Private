package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.agent.skills.ExplainabilitySkill
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAuditLogTool @Inject constructor(
    private val explainabilitySkill: ExplainabilitySkill
) : BaseTool() {
    override val name: String = "get_audit_log"
    
    override val description: String = "Retrieves recent security and action audit logs for transparency and explainability."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "limit": {
              "type": "integer",
              "description": "Number of recent logs to retrieve."
            }
          }
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        return try {
            val logs = explainabilitySkill.getRecentAudit()
            ToolResult.Success(logs, latencyMs = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Audit lookup failed", e, System.currentTimeMillis() - start)
        }
    }
}
