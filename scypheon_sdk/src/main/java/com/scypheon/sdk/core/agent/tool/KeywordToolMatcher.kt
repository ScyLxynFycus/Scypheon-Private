package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.agent.ooda.ToolMatcher
import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordToolMatcher @Inject constructor() : ToolMatcher {
    override suspend fun scoreTools(query: String, candidates: List<AgentSkillRegistry.FastTool>): List<ToolMatcher.ToolScore> = withContext(Dispatchers.Default) {
        val queryTokens = query.lowercase().split(Regex("\\s+")).toSet()
        candidates.map { tool ->
            val toolTokens = tool.keywords.flatMap { it.lowercase().split(Regex("\\s+")) }.toSet()
            val intersection = queryTokens.intersect(toolTokens).size
            val union = queryTokens.union(toolTokens).size
            val jaccard = if (union == 0) 0f else intersection.toFloat() / union.toFloat()
            ToolMatcher.ToolScore(tool, jaccard)
        }.sortedByDescending { it.score }
    }
}
