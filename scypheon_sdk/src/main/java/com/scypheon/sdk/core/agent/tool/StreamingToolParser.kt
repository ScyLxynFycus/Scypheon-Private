package com.scypheon.sdk.core.agent.tool

import org.json.JSONObject
import timber.log.Timber

/**
 * StreamingToolParser: 
 * Real-time interceptor for tool calls embedded in LLM token streams.
 * Ported from the Claude Code Thinking-to-Action transition pattern.
 */
class StreamingToolParser {
    private val buffer = StringBuilder()
    
    fun reset() {
        buffer.setLength(0)
    }

    /**
     * Processes a single token. If a complete tool call is detected, returns it.
     * Expects format: <tool_call>{"name": "tool_name", "arguments": {"key": "value"}}</tool_call>
     */
    fun processToken(token: String): ToolCall? {
        buffer.append(token)
        val currentText = buffer.toString()

        val startIndex = currentText.indexOf("<tool_call>")
        val endIndex = currentText.indexOf("</tool_call>")

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val jsonText = currentText.substring(startIndex + 11, endIndex).trim()
            return try {
                val json = JSONObject(jsonText)
                val name = if (json.has("toolName")) json.getString("toolName") else json.getString("name")
                val argsJson = json.optJSONObject("arguments")
                
                val args = mutableMapOf<String, Any?>()
                argsJson?.keys()?.forEach { key ->
                    args[key] = argsJson.get(key)
                }

                Timber.i("🛠️ [TOOL_PARSER] Intercepted call: $name")
                // BUG FIX: Do not clear the entire buffer. Delete only up to the end of the parsed tag.
                // This prevents dropping tokens that arrive after the </tool_call> tag in the same chunk.
                buffer.delete(0, endIndex + 12)
                ToolCall(name, args)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse tool call JSON: $jsonText")
                buffer.delete(0, endIndex + 12)
                null
            }
        }
        
        return null
    }
}
