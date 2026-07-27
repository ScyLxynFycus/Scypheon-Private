package com.scypheon.sdk.core.agent.tool

import org.json.JSONObject
import timber.log.Timber

/**
 * StreamingToolParser: 
 * Real-time interceptor for tool calls embedded in LLM token streams.
 * Ported from the Claude Code Thinking-to-Action transition pattern.
 * Provides safe text emission to prevent leaking XML tags into the UI stream.
 */
class StreamingToolParser {
    private var buffer = ""
    private var isInsideToolCall = false
    private val startTag = "<tool_call>"
    private val endTag = "</tool_call>"

    class ParsingResult(
        val safeTextToEmit: String,
        val toolCall: ToolCall?
    )

    fun reset() {
        buffer = ""
        isInsideToolCall = false
    }

    /**
     * Processes a single token. If a complete tool call is detected, returns it.
     * Buffers tokens to prevent emitting `<tool_call>` markers to the UI.
     */
    fun processToken(token: String): ParsingResult {
        if (buffer.length + token.length > 65536) {
            Timber.e("CRITICAL: StreamingToolParser buffer size limit exceeded (64KB). Resetting to prevent OOM.")
            reset()
            return ParsingResult("", null)
        }
        buffer += token
        var safeEmit = ""
        var foundToolCall: ToolCall? = null

<<<<<<< Updated upstream
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
=======
        var processing = true
        while (processing) {
            if (!isInsideToolCall) {
                val tagIndex = buffer.indexOf(startTag)
                if (tagIndex != -1) {
                    isInsideToolCall = true
                    safeEmit += buffer.substring(0, tagIndex)
                    buffer = buffer.substring(tagIndex + startTag.length)
                } else {
                    // Check for partial tag match at the end
                    var longestPrefixMatch = 0
                    for (len in 1..buffer.length.coerceAtMost(startTag.length - 1)) {
                        val suffix = buffer.substring(buffer.length - len)
                        val tagPrefix = startTag.substring(0, len)
                        if (suffix == tagPrefix) {
                            longestPrefixMatch = len
                        }
                    }
                    
                    if (longestPrefixMatch > 0) {
                        safeEmit += buffer.substring(0, buffer.length - longestPrefixMatch)
                        buffer = buffer.substring(buffer.length - longestPrefixMatch)
                    } else {
                        safeEmit += buffer
                        buffer = ""
                    }
                    processing = false
                }
            } else {
                // Inside tool call, wait for end tag
                val endIndex = buffer.indexOf(endTag)
                if (endIndex != -1) {
                    isInsideToolCall = false
                    val jsonText = buffer.substring(0, endIndex).trim()
                    buffer = buffer.substring(endIndex + endTag.length)
                    
                    foundToolCall = parseJson(jsonText)
                    processing = buffer.isNotEmpty() && foundToolCall == null
                    if (foundToolCall != null) {
                        processing = false
                    }
                } else {
                    processing = false
                }
>>>>>>> Stashed changes
            }
        }
        
        return ParsingResult(safeEmit, foundToolCall)
    }
    
    private fun parseJson(jsonText: String): ToolCall? {
        return try {
            val json = JSONObject(jsonText)
            val name = if (json.has("toolName")) json.getString("toolName") else json.getString("name")
            val argsJson = json.optJSONObject("arguments")
            
            val args = mutableMapOf<String, Any?>()
            argsJson?.keys()?.forEach { key ->
                args[key] = argsJson.get(key)
            }

            Timber.i("🛠️ [TOOL_PARSER] Intercepted call: $name")
            ToolCall(name, args)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse tool call JSON: $jsonText")
            null
        }
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
