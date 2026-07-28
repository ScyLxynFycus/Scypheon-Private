package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.BaseTool
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.intelligence.graph.WebDiscoveryProvider
import timber.log.Timber
import javax.inject.Inject

class DuckDuckGoTool @Inject constructor(
    private val webProvider: WebDiscoveryProvider
) : BaseTool() {
    override val name: String = "discover_duckduckgo"
    override val description: String = "Searches DuckDuckGo for instant answers. Use for general facts, current snippets, or quick definitions."
    override val inputSchema: String = """
        {
            "type": "object",
            "properties": {
                "query": { "type": "string", "description": "The search query" }
            },
            "required": ["query"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val query = args["query"] as? String ?: return ToolResult.Error("Missing query", null, 0)
        val start = System.currentTimeMillis()
        
        if (!context.allowNetwork) {
            return ToolResult.Error("Offline Mode: DuckDuckGo search is disabled in settings.", null, 0)
        }
        
        return try {
            val summary = webProvider.discoverDuckDuckGo(query)
            if (summary != null) {
                ToolResult.Success(summary, latencyMs = System.currentTimeMillis() - start)
            } else {
                ToolResult.Error("No instant answer found on DuckDuckGo for: $query", null, System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            ToolResult.Error("DuckDuckGo search failed: ${e.message}", e, System.currentTimeMillis() - start)
        }
    }
}
