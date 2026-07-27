package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralQueryTool @Inject constructor() : BaseTool() {
    override val name: String = "general_query"
    
    override val aliases: List<String> = listOf("get_time", "small_talk")
    
    override val description: String = "Handles general utility queries like current time, system greeting, or simple interactions."
    
    override fun isConcurrencySafe(args: Map<String, Any?>): Boolean = true

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "type": {
              "type": "string",
              "enum": ["TIME", "GREETING"],
              "description": "The type of general query."
            }
          },
          "required": ["type"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        return when(args["type"]?.toString()) {
            "TIME" -> {
                val time = Calendar.getInstance().time.toString()
                ToolResult.Success("CURRENT_TIME: $time", latencyMs = System.currentTimeMillis() - start)
            }
            "GREETING" -> ToolResult.Success(
                "GREETING: Hello! I am Scypheon, your humanitarian assistant. How can I help today?", 
                latencyMs = System.currentTimeMillis() - start
            )
            else -> ToolResult.Error("Unsupported query type", null, System.currentTimeMillis() - start)
        }
    }
}
