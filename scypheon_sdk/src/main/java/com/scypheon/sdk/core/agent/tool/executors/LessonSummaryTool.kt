package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.agent.skills.TutorSkill
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LessonSummaryTool @Inject constructor(
    private val tutorSkill: TutorSkill
) : BaseTool() {
    override val name: String = "get_lesson_summary"
    
    override val description: String = "Generates a verified pedagogical summary of a given topic. Useful for review and certification tracking."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "topic": {
              "type": "string",
              "description": "The educational topic to summarize."
            }
          },
          "required": ["topic"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val topic = args["topic"]?.toString() ?: "General"
        val start = System.currentTimeMillis()
        return try {
            val summary = tutorSkill.getSummary(topic)
            ToolResult.Success(summary, latencyMs = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Summary failed", e, System.currentTimeMillis() - start)
        }
    }
}
