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
    private val summarizer: ConversationSummarizer,
    private val smartTruncator: com.scypheon.sdk.core.agent.context.SmartTruncator
) {
    private val defaultMaxTokens = 4096 // For Gemma-2b/Llama-3-8b mobile optimization
    private val COMPACTION_THRESHOLD = 0.8 // Compact at 80% full

    /**
     * Inspects the message history and compacts if necessary.
     * Pillar 2: Dynamic Context Pruning (Ranked Fact Extraction)
     */
    suspend fun manage(messages: MutableList<Message>, maxTokensOverride: Int = defaultMaxTokens): Boolean {
        val currentTokens = estimateTokens(messages)
        val activeMaxTokens = if (maxTokensOverride > 0) maxTokensOverride else defaultMaxTokens
        
        if (currentTokens > activeMaxTokens * COMPACTION_THRESHOLD) {
            Timber.i("🧹 [CONTEXT_MANAGER] Token threshold reached ($currentTokens). Compacting history...")
            
            // Guard clause for early massive prompts
            if (messages.size <= 2) {
                Timber.w("⚠️ [CONTEXT_MANAGER] History too short for sliding window. A single massive message detected. Truncating payload...")
                val lastIndex = messages.size - 1
                if (lastIndex >= 0) {
                    val lastMsg = messages[lastIndex]
                    val safeMaxTokens = (activeMaxTokens * 0.85).toInt()
                    val truncationResult = smartTruncator.truncate(lastMsg.content, safeMaxTokens)
                    if (truncationResult.truncated) {
                        messages[lastIndex] = lastMsg.copy(content = truncationResult.text)
                        return true
                    }
                }
                return false
            }

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
        // Consistent heuristic: 1 token ~= 3.5 chars + 10% safety margin
        return messages.sumOf { 
            val base = Math.ceil(it.content.length / 3.5).toInt()
            (base * 1.1).toInt() + 10 
        }
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
