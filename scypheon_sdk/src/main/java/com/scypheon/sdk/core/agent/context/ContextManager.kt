package com.scypheon.sdk.core.agent.context

import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.agent.tool.ToolResult
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@SafetyCritical
@Singleton
class ContextManager @Inject constructor() {
    
    private val segments = mutableListOf<ContextSegment>()
    private val maxTokenBudget = 2048

    fun push(content: String, priority: ContextPriority) {
        val segment = ContextSegment(
            id = UUID.randomUUID().toString(),
            text = content,
            priority = priority,
            tokens = estimateTokens(content),
            timestamp = System.currentTimeMillis()
        )
        segments.add(segment)
        enforceBudget()
    }

    fun ingestChatTurn(role: String, content: String) {
        push("[$role]: $content", ContextPriority.HIGH)
    }

    fun ingestToolResults(results: List<ToolResult>) {
        results.forEach { result ->
            push("TOOL_RESULT: $result", ContextPriority.CRITICAL)
        }
    }

    fun buildContextWindow(tokenBudget: Int): String {
        // Simple implementation: last X segments that fit in budget
        return segments.sortedBy { it.timestamp }
            .takeLastWhile { it.tokens <= tokenBudget }
            .joinToString("\n") { it.text }
    }

    private fun enforceBudget() {
        var currentTokens = segments.sumOf { it.tokens }
        if (currentTokens > maxTokenBudget) {
            val evictable = segments.filter { it.priority != ContextPriority.CRITICAL }
                .sortedBy { it.priority.ordinal }
            
            for (segment in evictable) {
                segments.remove(segment)
                currentTokens -= segment.tokens
                if (currentTokens <= maxTokenBudget) break
            }
        }
    }

    private fun estimateTokens(text: String): Int = text.length / 4

    fun getPrompt(): String = segments.sortedBy { it.timestamp }.joinToString("\n") { it.text }

    fun persist(sessionId: String) {
        // Production: Write to Room/AppDatabase if needed
    }
}
