package com.scypheon.sdk.core.humanitarian.psychology

import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ProactiveCompanionLogic: The "Nudge" for the Elderly Companion.
 * Periodically scans the Knowledge Graph to trigger meaningful reminiscence.
 */
@Singleton
class ProactiveCompanionLogic @Inject constructor(
    private val memoryManager: DualMemoryManager,
    private val gateway: NeuralGateway
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Scans for high-value emotional anchors (Family, Home, Career).
     * If found, triggers a proactive prompt to engage the user.
     */
    fun triggerProactiveReminiscence(speakCallback: (String) -> Unit) {
        scope.launch {
            Timber.i("🧠 [COMPANION] Initiating Proactive Memory Sweep...")
            
            // Search for "important" relationships in the Graph
            val familyFacts = memoryManager.querySubject("User")
                .filter { it.contains("cucu") || it.contains("anak") || it.contains("istri") || it.contains("suami") }
            
            if (familyFacts.isNotEmpty()) {
                val fact = familyFacts.shuffled().first()
                Timber.i("💖 [COMPANION] Found emotional anchor: $fact")
                
                val prompt = """
                    You are a gentle, proactive Elderly Companion. 
                    You know this fact about the user: "$fact".
                    Start a warm conversation asking the user to tell you more about this person or share a memory.
                    Be nostalgic and patient.
                """.trimIndent()
                
                gateway.routeRequest(prompt).collect { response ->
                    speakCallback(response)
                }
            } else {
                Timber.d("🧠 [COMPANION] No strong emotional anchors found for proactive nudge.")
            }
        }
    }
}
