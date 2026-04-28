package com.scypheon.sdk.core.swarm

import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import timber.log.Timber

/**
 * Enterprise Native Multi-Agent Swarm Orchestrator.
 * Handles delegating sub-tasks concurrently to specialized agents on background threads.
 */
class AgentOrchestrator(
    private val gateway: NeuralGateway
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeAgents = mutableListOf<BaseAgent>()

    fun registerAgent(agent: BaseAgent) {
        activeAgents.add(agent)
    }

    /**
     * Executes a complex task by fanning out the request to all capable agents concurrently,
     * and synthesizing their responses.
     */
    suspend fun swarmExecute(complexTask: String): String {
        Timber.i("🐝 Swarm Orchestrator: Delegating task: $complexTask")

        if (activeAgents.isEmpty()) {
            return gateway.routeRequest(complexTask).reduce { acc, value -> acc + value }
        }

        // Map-Reduce Style Concurrent Execution
        val agentResponses = activeAgents.map { agent ->
            scope.async {
                agent.executeTask(complexTask)
            }
        }.awaitAll()

        val synthesizedContext = agentResponses.joinToString("\n\n")

        val synthesisPrompt = """
            You are the Swarm Commander. You asked your sub-agents to solve this task: "$complexTask"

            Here are their reports:
            $synthesizedContext

            Synthesize these reports into one final, coherent, and highly accurate answer.
        """.trimIndent()

        Timber.i("🐝 Swarm Orchestrator: Synthesizing final response...")
        val draftResponse = gateway.routeRequest(synthesisPrompt).reduce { acc, value -> acc + value }

        // --- A.I. RESEARCH FEATURE: THE CRITIC NODE (Self-Reflection) ---
        Timber.w("🕵️ Critic Node: Auditing the Swarm's draft for hallucinations...")

        val criticPrompt = """
            You are the ultimate Critic Agent in a Medical/Humanitarian AI Swarm.
            Your job is to double-check the Commander's Draft for safety, truthfulness, and hallucinations.

            Original User Task: "$complexTask"
            Commander's Draft: "$draftResponse"

            1. If the Draft is safe, factually sound, and helpful, reply EXACTLY with: [APPROVED] <Draft>
            2. If the Draft contains dangerous hallucinations, fake medical advice, or contradicts safety rules, reply EXACTLY with: [REJECTED] <Correction>

            Output your audit now.
        """.trimIndent()

        val auditResult = gateway.routeRequest(criticPrompt).reduce { acc, value -> acc + value }

        return if (auditResult.startsWith("[REJECTED]", ignoreCase = true)) {
            Timber.e("🚨 Critic Node Intercepted a Swarm Hallucination! Replacing output.")
            auditResult.replace("[REJECTED]", "⚠️ (Dikoreksi oleh Sistem Keamanan A.I.):").trim()
        } else {
            Timber.i("✅ Critic Node Approved the draft.")
            auditResult.replace("[APPROVED]", "").trim()
        }
    }
}
