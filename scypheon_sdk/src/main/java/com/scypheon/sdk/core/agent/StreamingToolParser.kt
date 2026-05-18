package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.tool.ToolCall
import org.json.JSONObject
import timber.log.Timber

/**
 * A reactive parser that identifies tool calls within a raw token stream.
 * Optimized for mobile memory usage (no regex on large buffers).
 */
class StreamingToolParser {
    private val buffer = StringBuilder()
    private var isCapturing = false
    private var depth = 0 // For nested JSON tracking if needed

    companion object {
        private const val START_TAG = "<tool_call>"
        private const val END_TAG = "</tool_call>"
    }

    /**
     * Processes a new token and returns a ToolCall if one was completed.
     */
    fun processToken(token: String): ToolCall? {
        buffer.append(token)
        
        val currentText = buffer.toString()
        
        if (!isCapturing && currentText.contains(START_TAG)) {
            isCapturing = true
            // Trim everything before the start tag to keep buffer small
            val startIndex = currentText.indexOf(START_TAG)
            buffer.delete(0, startIndex + START_TAG.length)
        }

        if (isCapturing && buffer.contains(END_TAG)) {
            val endIndex = buffer.indexOf(END_TAG)
            val jsonPayload = buffer.substring(0, endIndex).trim()
            
            // Cleanup for next call
            buffer.delete(0, endIndex + END_TAG.length)
            isCapturing = false
            
            return try {
                parseJson(jsonPayload)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse tool call JSON: $jsonPayload")
                null
            }
        }
        
        // Safety: Prevent buffer bloat if no tags found
        if (!isCapturing && buffer.length > 500) {
            buffer.delete(0, 400)
        }

        return null
    }

    private fun parseJson(json: String): ToolCall {
        val obj = JSONObject(json)
        val name = obj.getString("name")
        val args = mutableMapOf<String, String>()
        
        val argsObj = obj.optJSONObject("arguments")
        argsObj?.keys()?.forEach { key ->
            args[key] = argsObj.get(key).toString()
        }
        
        return ToolCall(name, args)
    }

    fun reset() {
        buffer.setLength(0)
        isCapturing = false
    }
}
