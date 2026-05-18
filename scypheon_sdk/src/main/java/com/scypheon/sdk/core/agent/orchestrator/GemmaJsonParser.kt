package com.scypheon.sdk.core.agent.orchestrator

import com.scypheon.sdk.core.agent.tool.ToolCall
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaJsonParser @Inject constructor() {
    
    /**
     * Parses the LLM's raw reasoning output to extract structured tool calls.
     * Production implementation handles malformed JSON and partial generation.
     */
    fun parseToolCalls(rawOutput: String): List<ToolCall> {
        return try {
            val jsonStart = rawOutput.indexOf("[")
            val jsonEnd = rawOutput.lastIndexOf("]") + 1
            if (jsonStart == -1 || jsonEnd == 0) return emptyList()
            
            val jsonArray = JSONArray(rawOutput.substring(jsonStart, jsonEnd))
            val calls = mutableListOf<ToolCall>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                calls.add(ToolCall(
                    callId = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    toolName = obj.getString("tool"),
                    arguments = obj.getJSONObject("args").toStringMap()
                ))
            }
            calls
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        keys().forEach { key -> map[key] = get(key).toString() }
        return map
    }
}
