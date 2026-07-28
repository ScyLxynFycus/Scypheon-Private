package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.tool.ToolCall
import org.json.JSONObject
import timber.log.Timber

/**
 * A reactive parser that identifies tool calls within a raw token stream.
 * Optimized for DeepSeek-V4 DSML schema and mobile memory usage (no regex on large buffers).
 */
class StreamingToolParser {
    private val buffer = StringBuilder()
    private var isCapturing = false
    private var depth = 0 // For nested JSON tracking if needed

    companion object {
        // DeepSeek-V4 specific schema markers
        private const val START_TAG = "<|DSML|tool_calls>"
        private const val END_TAG = "</|DSML|tool_calls>"
        private const val INVOKE_START = "<|DSML|invoke name=\""
        private const val INVOKE_END = "</|DSML|invoke>"
    }

    /**
     * Processes a new token and returns a list of ToolCalls if a block was completed.
     */
    fun processToken(token: String): List<ToolCall>? {
        if (buffer.length + token.length > 65536) {
            Timber.e("CRITICAL: StreamingToolParser buffer size limit exceeded (64KB). Resetting to prevent OOM.")
            reset()
            return null
        }
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
            val dsmlPayload = buffer.substring(0, endIndex).trim()
            
            // Cleanup for next call
            buffer.delete(0, endIndex + END_TAG.length)
            isCapturing = false
            
            return try {
                parseDSML(dsmlPayload)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse tool call DSML: $dsmlPayload")
                null
            }
        }
        
        // Safety: Prevent buffer bloat if no tags found
        if (!isCapturing && buffer.length > 500) {
            buffer.delete(0, 400)
        }

        return null
    }

    private fun parseDSML(dsml: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        var searchIndex = 0
        
        while (true) {
            val startInvoke = dsml.indexOf(INVOKE_START, searchIndex)
            if (startInvoke == -1) break
            
            val nameStart = startInvoke + INVOKE_START.length
            val nameEnd = dsml.indexOf("\">", nameStart)
            if (nameEnd == -1) break
            
            val toolName = dsml.substring(nameStart, nameEnd)
            
            val blockEnd = dsml.indexOf(INVOKE_END, nameEnd)
            if (blockEnd == -1) break
            
            val paramsBlock = dsml.substring(nameEnd + 2, blockEnd)
            val args = parseDSMLParameters(paramsBlock)
            
            calls.add(ToolCall(toolName, args))
            searchIndex = blockEnd + INVOKE_END.length
        }
        return calls
    }

    private fun parseDSMLParameters(paramsBlock: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        val paramStartTag = "<|DSML|parameter name=\""
        val paramEndTag = "</|DSML|parameter>"
        
        var pIndex = 0
        while (true) {
            val sTagIdx = paramsBlock.indexOf(paramStartTag, pIndex)
            if (sTagIdx == -1) break
            
            val nStart = sTagIdx + paramStartTag.length
            val nEnd = paramsBlock.indexOf("\"", nStart)
            if (nEnd == -1) break
            
            val paramName = paramsBlock.substring(nStart, nEnd)
            
            // Fast-forward to the end of the opening parameter tag
            val contentStart = paramsBlock.indexOf(">", nEnd) + 1
            val contentEnd = paramsBlock.indexOf(paramEndTag, contentStart)
            if (contentEnd == -1) break
            
            val paramValue = paramsBlock.substring(contentStart, contentEnd).trim()
            args[paramName] = paramValue
            
            pIndex = contentEnd + paramEndTag.length
        }
        return args
    }

    fun reset() {
        buffer.setLength(0)
        isCapturing = false
    }
}
