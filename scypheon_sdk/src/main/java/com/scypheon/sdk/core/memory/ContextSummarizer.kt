package com.scypheon.sdk.core.memory

import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Enterprise Edge Max: The Infinite Memory Illusion.
 * Monitors session token capacity. If a session gets too long, it asks the LLM to summarize
 * the oldest messages and replaces them with a single [SUMMARY] block in the SQLite DB,
 * preventing Context Window OOM crashes while maintaining conversational continuity.
 */
class ContextSummarizer(
    private val memoryManager: DualMemoryManager,
    private val gateway: NeuralGateway
) {
    // Independent scope to survive caller cancellation (preventing incomplete summaries)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Max messages before triggering a summary (Proxy for token limits)
    private val MAX_CONTEXT_MESSAGES = 20
    private val SUMMARIZE_CHUNK_SIZE = 10

    fun checkAndSummarizeSessionAsync(sessionId: String) {
        scope.launch {
            try {
                val messages = memoryManager.getMessagesForSession(sessionId)

                if (messages.size > MAX_CONTEXT_MESSAGES) {
                    Timber.w("🧠 Context window limit approaching. Triggering Auto-Summarization for session: $sessionId")

                    // Extract the oldest block of messages to summarize (excluding the very first if it's already a summary)
                    val startIndex = if (messages.first().text.startsWith("[SUMMARY]")) 1 else 0
                    val messagesToSummarize = messages.subList(startIndex, startIndex + SUMMARIZE_CHUNK_SIZE)

                    val conversationBlock = messagesToSummarize.joinToString("\n") {
                        if (it.isUser) "User: ${it.text}" else "AI: ${it.text}"
                    }

                    val prompt = """
                        Summarize the following conversation in 3 concise sentences. Focus on the core facts and user intent.

                        Conversation:
                        $conversationBlock
                    """.trimIndent()

                    val summary = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }

                    Timber.i("🧠 Generated Summary: $summary")

                    // Replace the raw chunk in DB with the summary
                    memoryManager.replaceMessagesWithSummary(sessionId, SUMMARIZE_CHUNK_SIZE, "[SUMMARY] $summary")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to auto-summarize session")
            }
        }
    }
}
