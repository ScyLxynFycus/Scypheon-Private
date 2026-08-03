package com.scypheon.sdk.core.agent

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContextManager: Manages the conversation window and token budget.
 * Implements "Auto-Compaction" from Claude Code to survive long humanitarian missions.
 */
@Singleton
class ContextManager @Inject constructor(
    private val summarizer: ConversationSummarizer
) {
    private val MAX_TOKENS = 4096 // For Gemma-2b/Llama-3-8b mobile optimization
    private val COMPACTION_THRESHOLD = 0.8 // Compact at 80% full

    /**
     * Inspects the message history and compacts if necessary.
     * Pillar 2: Dynamic Context Pruning (Ranked Fact Extraction)
     */
    suspend fun manage(messages: MutableList<Message>): Boolean {
        val currentTokens = estimateTokens(messages)
        
        if (currentTokens > MAX_TOKENS * COMPACTION_THRESHOLD) {
            Timber.i("🧹 [CONTEXT_MANAGER] Token threshold reached ($currentTokens). Compacting history...")
            
            // Pillar 2: Identify "Protected" history vs "Stale" history
            // We preserve: System prompt (0), critical thinking blocks, and the last 2 turns.
            val protectedIndices = mutableSetOf(0, messages.size - 1, messages.size - 2)
            
            val messagesToSummarize = mutableListOf<Message>()
            val newMessages = mutableListOf<Message>()
            
            // Keep the system prompt
            newMessages.add(messages[0])
            
            for (i in 1 until messages.size - 2) {
                val msg = messages[i]
                // Preserve "Thinking" blocks to maintain agentic state (Claude Code pattern)
                if (msg.isThinking || msg.isMeta) {
                    newMessages.add(msg)
                } else {
                    messagesToSummarize.add(msg)
                }
            }
            
            if (messagesToSummarize.isNotEmpty()) {
                val summary = summarizer.summarize(messagesToSummarize)
                newMessages.add(Message("system", "--- MEMORY_SUMMARY: $summary ---", isMeta = true))
                
                // Add the recent turns
                newMessages.add(messages[messages.size - 2])
                newMessages.add(messages[messages.size - 1])
                
                messages.clear()
                messages.addAll(newMessages)
                
                Timber.d("✅ [CONTEXT_MANAGER] Compaction complete (Pillar 2). New count: ${estimateTokens(messages)}")
                return true
            }
        }
        return false
    }

    private fun estimateTokens(messages: List<Message>): Int {
        // Simple heuristic: 1 token ~= 4 characters for clinical/humanitarian text
        return messages.sumOf { (it.content.length / 4) + 10 }
    }
}

interface ConversationSummarizer {
    suspend fun summarize(history: List<Message>): String
}

@Singleton
class DefaultSummarizer @Inject constructor() : ConversationSummarizer {
    override suspend fun summarize(history: List<Message>): String {
        // In a production app, this would call a separate lightweight summarization model.
        // For now, we use a structured heuristic.
        val topics = history.filter { it.role == "user" }.takeLast(3).joinToString(", ") { it.content }
        return "Previously discussed topics including: $topics. System was focusing on humanitarian triage."
    }
}
