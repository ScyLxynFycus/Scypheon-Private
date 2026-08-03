package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.BaseTool
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.intelligence.graph.WebDiscoveryProvider
import timber.log.Timber
import javax.inject.Inject

class WikipediaTool @Inject constructor(
    private val webProvider: WebDiscoveryProvider
) : BaseTool() {
    override val name: String = "discover_wikipedia"
    override val description: String = "Fetches a text-only summary from Wikipedia. Use for general knowledge, biographies, or terminology."
    override val inputSchema: String = """
        {
            "type": "object",
            "properties": {
                "query": { "type": "string", "description": "The search term (e.g. 'Quantum Computing')" }
            },
            "required": ["query"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val query = args["query"] as? String ?: return ToolResult.Error("Missing query", null, 0)
        val start = System.currentTimeMillis()
        
        if (!context.allowNetwork) {
            return ToolResult.Error("Offline Mode: Wikipedia search is disabled in settings.", null, 0)
        }
        
        return try {
            val summary = webProvider.discoverWikipedia(query)
            if (summary != null) {
                ToolResult.Success(summary, System.currentTimeMillis() - start)
            } else {
                ToolResult.Error("Wikipedia page not found for: $query", null, System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            ToolResult.Error("Wikipedia search failed: ${e.message}", e, System.currentTimeMillis() - start)
        }
    }
}
