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
    private var maxTokenBudget = 8192 // Default, can be updated via config

    @Synchronized
    fun updateBudget(newBudget: Int) {
        if (newBudget > 0 && maxTokenBudget != newBudget) {
            maxTokenBudget = newBudget
            enforceBudget()
        }
    }

    @Synchronized
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
            val resultStr = when (result) {
                is ToolResult.Success -> result.data?.toString() ?: "Success"
                is ToolResult.Error -> "Error: ${result.reason}"
                is ToolResult.Fallback -> "Fallback: ${result.data}"
                is ToolResult.AwaitingApproval -> "Awaiting Approval: ${result.reason}"
            }
            push("TOOL_RESULT: $resultStr", ContextPriority.CRITICAL)
        }
    }

    /**
     * Safely builds the context window ensuring the SUM of tokens strictly adheres to the budget.
     * Retains chronological order while packing the most recent and critical context first.
     */
    @Synchronized
    fun buildContextWindow(tokenBudget: Int): String {
        val sortedSegments = segments.sortedByDescending { it.timestamp }
        var currentTokens = 0
        val selectedSegments = mutableListOf<ContextSegment>()

        // Always include CRITICAL segments first to prevent safety mechanism eviction
        val criticalSegments = sortedSegments.filter { it.priority == ContextPriority.CRITICAL }
        for (segment in criticalSegments) {
            if (currentTokens + segment.tokens <= tokenBudget) {
                selectedSegments.add(segment)
                currentTokens += segment.tokens
            }
        }

        // Fill remaining budget chronologically (newest first)
        for (segment in sortedSegments.filter { it.priority != ContextPriority.CRITICAL }) {
            if (currentTokens + segment.tokens <= tokenBudget) {
                selectedSegments.add(segment)
                currentTokens += segment.tokens
            }
            if (currentTokens >= tokenBudget) break
        }

        // Re-sort selected segments back to chronological order for the LLM
        return selectedSegments
            .sortedBy { it.timestamp }
            .joinToString("\n") { it.text }
    }

    @Synchronized
    private fun enforceBudget() {
        var currentTokens = segments.sumOf { it.tokens }
        if (currentTokens > maxTokenBudget) {
            // Evict lowest priority first, oldest first
            val evictable = segments.filter { it.priority != ContextPriority.CRITICAL }
                .sortedWith(compareBy({ it.priority.ordinal }, { it.timestamp }))
            
            val iterator = evictable.iterator()
            while (iterator.hasNext() && currentTokens > maxTokenBudget) {
                val segment = iterator.next()
                segments.remove(segment)
                currentTokens -= segment.tokens
            }
        }
    }

    /**
     * Enterprise token estimation. Uses 1 token = 3.5 chars ratio based on Cl100kBase / Tiktoken heuristics,
     * adding a 10% safety margin buffer to prevent OOV overflow.
     */
    private fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val baseEstimate = Math.ceil(text.length / 3.5).toInt()
        return (baseEstimate * 1.1).toInt() // 10% safety margin
    }

    @Synchronized
    fun getPrompt(): String = buildContextWindow(maxTokenBudget)

    fun persist(sessionId: String) {
        // Production: Implement DB serialization via abstract Persistence Layer
    }
}
