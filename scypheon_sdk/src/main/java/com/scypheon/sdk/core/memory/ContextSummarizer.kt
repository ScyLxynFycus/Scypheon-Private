package com.scypheon.sdk.core.memory

import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Edge Max: The Infinite Memory Illusion.
 * Monitors session token capacity. If a session gets too long, it asks the LLM to summarize
 * the oldest messages and replaces them with a single [SUMMARY] block in the SQLite DB,
 * preventing Context Window OOM crashes while maintaining conversational continuity.
 */
@Singleton
class ContextSummarizer @Inject constructor(
    private val memoryManager: DualMemoryManager,
    private val gateway: NeuralGateway
) {
    // Independent scope to survive caller cancellation (preventing incomplete summaries)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Max messages before triggering a summary (Proxy for ~2000 token limits)
    private val MAX_CONTEXT_MESSAGES = 12
    private val SUMMARIZE_CHUNK_SIZE = 6
    private val MAX_TOTAL_CHARS = 16000 // Solaris 4.5: Expanded limit for higher-bandwidth conversations
    private val EMERGENCY_REMAIN_MESSAGES = 3 // Minimum messages to keep if memory is critical

    fun checkAndSummarizeSessionAsync(sessionId: String) {
        scope.launch {
            try {
                val messages = memoryManager.getMessagesForSession(sessionId)

                val totalChars: Int = messages.sumOf { it.text.length }
                val am = memoryManager.getContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)

                val isHighPressure = messages.size > MAX_CONTEXT_MESSAGES || totalChars > MAX_TOTAL_CHARS || memInfo.lowMemory

                if (isHighPressure) {
                    Timber.w("🧠 Memory Pressure Detected (Size: ${messages.size}, Chars: $totalChars, LowRAM: ${memInfo.lowMemory}). Triggering Recursive Summarization.")

                    val hasExistingSummary = messages.first().text.startsWith("[SUMMARY]")
                    
                    // If total chars are huge, we need to prune more aggressively
                    val countToReplace = if (totalChars > MAX_TOTAL_CHARS * 1.5) {
                        (messages.size - EMERGENCY_REMAIN_MESSAGES).coerceAtLeast(1)
                    } else {
                        if (hasExistingSummary) SUMMARIZE_CHUNK_SIZE + 1 else SUMMARIZE_CHUNK_SIZE
                    }
                    
                    val safeCountToReplace = countToReplace.coerceAtMost(messages.size - 1)
                    
                    // Extract the block to summarize
                    val messagesToSummarize = messages.subList(0, safeCountToReplace)

                    val prompt = if (hasExistingSummary) {
                        """
                        Update the existing summary below by incorporating the new conversation details. Keep the output under 4 concise sentences.
                        
                        Existing Summary:
                        ${messages.first().text.removePrefix("[SUMMARY] ")}
                        
                        New Details to Add:
                        ${messagesToSummarize.drop(1).joinToString("\n") { if (it.isUser) "User: ${it.text}" else "AI: ${it.text}" }}
                        """.trimIndent()
                    } else {
                        """
                        Summarize the following conversation in 3 concise sentences. Focus on core facts and user intent.
                        
                        Conversation:
                        ${messagesToSummarize.joinToString("\n") { if (it.isUser) "User: ${it.text}" else "AI: ${it.text}" }}
                        """.trimIndent()
                    }

                    val summary = gateway.routeRequest(prompt).fold("") { acc, value -> acc + value }

                    if (summary.isNotBlank() && !summary.startsWith("Error")) {
                        Timber.i("🧠 Generated Recursive Summary: $summary")
                        
                        // [v1.1.5-SAR] The Bridge: Save to Short-Term (SQLite) for current session continuity
                        memoryManager.replaceMessagesWithSummary(sessionId, safeCountToReplace, "[SUMMARY] $summary")
                        
                        // [v1.1.5-SAR] The Bridge: Save to Long-Term (Vector DB) for CROSS-SESSION continuity
                        // This allows future sessions to "remember" this condensed context via Semantic Search.
                        memoryManager.saveSummaryToLongTerm(sessionId, summary)
                    } else {
                        Timber.w("❌ Summary generation failed or returned error.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to auto-summarize session")
            }
        }
    }
}
