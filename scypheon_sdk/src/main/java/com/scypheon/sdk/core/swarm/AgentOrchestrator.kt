package com.scypheon.sdk.core.swarm

import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import timber.log.Timber

import com.scypheon.sdk.core.safety.SafetyOrchestrator
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

/**
 * Edge-Optimized Multi-Agent Swarm Orchestrator.
 * Uses Lazy injection to conserve RAM and robust parsing for self-reflection.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    // 💡 PENERAPAN Q1: Lazy Loading. NeuralGateway tidak di-load ke RAM sampai Swarm dipanggil!
    private val gatewayLazy: Lazy<NeuralGateway>,
    private val safetyOrchestratorLazy: Lazy<SafetyOrchestrator>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Gunakan Thread-Safe collection jika agen ditambah secara dinamis
    private val activeAgents = java.util.concurrent.CopyOnWriteArrayList<BaseAgent>()

    fun registerAgent(agent: BaseAgent) {
        activeAgents.add(agent)
    }

    suspend fun swarmExecute(complexTask: String, sessionId: String = "swarm-${System.currentTimeMillis()}"): String {
        // Panggil .get() on-demand
        return safetyOrchestratorLazy.get().secureInteraction(sessionId, complexTask) {
            internalSwarmExecute(complexTask)
        }
    }

    private suspend fun internalSwarmExecute(complexTask: String): String {
        Timber.i("🐝 Swarm Orchestrator: Initiating edge-optimized swarm...")
        val gateway = gatewayLazy.get()

        if (activeAgents.isEmpty()) {
            return gateway.routeRequest(complexTask).reduce { acc, value -> acc + value }
        }

        // ⚠️ ARCHITECT WARNING: Di Edge AI, ini akan berjalan secara SEKUENSIAL di mesin Llama.
        // Pastikan jumlah activeAgents maksimal 2 untuk mencegah UX Timeout (lebih dari 15 detik).
        val agentResponses = activeAgents.map { agent ->
            scope.async {
                // Potong output agen maksimal 300 karakter untuk mencegah Context Explosion!
                val rawResponse = agent.executeTask(complexTask)
                rawResponse.take(300) + "...[TRUNCATED]" 
            }
        }.awaitAll()

        val synthesizedContext = agentResponses.joinToString("\n\n")

        val synthesisPrompt = """
            [SYSTEM: Synthesize these brief agent reports into one final answer for the task: "$complexTask"]
            REPORTS:
            $synthesizedContext
        """.trimIndent()

        Timber.i("🐝 Swarm Orchestrator: Commander synthesizing...")
        val draftResponse = gateway.routeRequest(synthesisPrompt).reduce { acc, value -> acc + value }

        Timber.w("🕵️ Critic Node: Auditing for hallucinations...")

        val criticPrompt = """
            Task: "$complexTask"
            Draft: "$draftResponse"
            Are there any medical hallucinations or safety violations?
            Reply ONLY with [APPROVED] or [REJECTED] <reason>.
        """.trimIndent()

        val auditResult = gateway.routeRequest(criticPrompt).reduce { acc, value -> acc + value }

        // 🛑 CRITICAL FIX: Gunakan .contains, bukan .startsWith
        return if (auditResult.contains("[REJECTED]", ignoreCase = true)) {
            Timber.e("🚨 Critic Node Intercepted a Swarm Hallucination!")
            
            // Ekstrak alasan penolakan jika ada
            val reason = auditResult.substringAfter("[REJECTED]").trim()
            "⚠️ (Draft ditolak oleh Critic Node): $reason"
            
        } else {
            Timber.i("✅ Critic Node Approved.")
            draftResponse
        }
    }
}
