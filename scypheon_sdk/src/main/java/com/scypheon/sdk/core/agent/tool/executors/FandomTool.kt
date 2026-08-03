package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.BaseTool
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.intelligence.graph.WebDiscoveryProvider
import timber.log.Timber
import javax.inject.Inject

class FandomTool @Inject constructor(
    private val webProvider: WebDiscoveryProvider
) : BaseTool() {
    override val name: String = "web_crawl_fandom"
    override val description: String = "Fetches a summary from a specific Fandom community wiki. Use for niche pop-culture, gaming, or specialized community knowledge."
    override val inputSchema: String = """
        {
            "type": "object",
            "properties": {
                "wikiName": { "type": "string", "description": "The subdomain of the wiki (e.g. 'starwars', 'minecraft')" },
                "pageName": { "type": "string", "description": "The title of the page to search" }
            },
            "required": ["wikiName", "pageName"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val wiki = args["wikiName"] as? String ?: return ToolResult.Error("Missing wiki name", null, 0)
        val page = args["pageName"] as? String ?: return ToolResult.Error("Missing page name", null, 0)
        val start = System.currentTimeMillis()
        
        return try {
            val summary = webProvider.discoverFandom(wiki, page)
            if (summary != null) {
                ToolResult.Success(summary, System.currentTimeMillis() - start)
            } else {
                ToolResult.Error("Fandom page '$page' not found on '$wiki' wiki.", null, System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            ToolResult.Error("Fandom crawl failed: ${e.message}", e, System.currentTimeMillis() - start)
        }
    }
}
