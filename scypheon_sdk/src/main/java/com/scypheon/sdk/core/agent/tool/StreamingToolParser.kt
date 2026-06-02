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
                val json = parseFaultTolerantJson(jsonText)
                val name = if (json.has("toolName")) json.getString("toolName") else json.getString("name")
                val argsJson = json.optJSONObject("arguments")
                
                val args = mutableMapOf<String, Any?>()
                argsJson?.keys()?.forEach { key ->
                    args[key] = argsJson.get(key)
                }

                Timber.i("🛠️ [TOOL_PARSER] Intercepted call: $name")
                buffer.delete(0, endIndex + 12)
                ToolCall(name, args)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse tool call JSON even with auto-recovery: $jsonText")
                buffer.delete(0, endIndex + 12)
                null
            }
        }
        
        return null
    }

    /**
     * Self-Healing LLM Tool Parser (Enterprise Grade)
     * Automatically fixes common LLM JSON hallucinations.
     */
    private fun parseFaultTolerantJson(rawJson: String): JSONObject {
        return try {
            JSONObject(rawJson) // Fast path: Try standard parse first
        } catch (e: Exception) {
            Timber.w("🛠️ [TOOL_PARSER] JSON Parse failed. Engaging Auto-Recovery Engine.")
            var healedJson = rawJson.trim()
            
            // 1. Strip trailing commas before closing braces
            healedJson = healedJson.replace(Regex(",\\s*\\}"), "}")
            healedJson = healedJson.replace(Regex(",\\s*\\]"), "]")
            
            // 2. Fix unescaped quotes inside values (Basic heuristic: if a quote is preceded by a word char and followed by space)
            // A bit risky for regex, but we can fix obvious newlines
            healedJson = healedJson.replace("\n", "\\n").replace("\r", "")
            
            // 3. Balance missing braces
            val openBraces = healedJson.count { it == '{' }
            val closeBraces = healedJson.count { it == '}' }
            if (openBraces > closeBraces) {
                healedJson += "}".repeat(openBraces - closeBraces)
            }
            
            Timber.i("🛠️ [TOOL_PARSER] Healed JSON: $healedJson")
            JSONObject(healedJson)
        }
    }
}
