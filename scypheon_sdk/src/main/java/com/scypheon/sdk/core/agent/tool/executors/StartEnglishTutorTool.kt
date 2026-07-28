package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.humanitarian.education.LiveEnglishTutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartEnglishTutorTool @Inject constructor(
    private val englishTutor: LiveEnglishTutor
) : BaseTool() {
    override val name: String = "start_english_tutor"
    
    override val description: String = "Initializes the Live English Tutor session. Use this when the user wants to practice speaking, needs pronunciation help, or wants an interactive lesson."
    
    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "mode": {
              "type": "string",
              "enum": ["PRACTICE", "LESSON", "EXAM"],
              "description": "The pedagogical mode for the session."
            }
          },
          "required": ["mode"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        return try {
            if (!englishTutor.isReady()) {
                englishTutor.warmUp()
            }
            ToolResult.Success("SUCCESS: Live English Tutor session initialized in ${args["mode"]} mode.", latencyMs = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Tutor start failed", e, System.currentTimeMillis() - start)
        }
    }

    override fun getActivityDescription(args: Map<String, Any?>): String = 
        "Warming up the English Tutor (${args["mode"]})..."
}
