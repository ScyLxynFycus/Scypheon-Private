package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.data.RoomConversationRepository
import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MemoryReflector: The Semantic Memory Extraction Engine.
 */
interface MemoryReflector {
    suspend fun reflect(sessionId: String): List<String>
}

@Singleton
class MemoryReflectorImpl @Inject constructor(
    private val graphDao: GraphDao,
    private val knowledgeGuard: KnowledgeGuardImpl,
    private val conversationRepository: RoomConversationRepository,
    private val neuralGateway: NeuralGateway
) : MemoryReflector {

    override suspend fun reflect(sessionId: String): List<String> = withContext(Dispatchers.IO) {
        Timber.i("[MEMORY_REFLECTOR] Initiating reflection | Session: $sessionId")

        return@withContext try {
            val turns = conversationRepository.getRecentTurns(sessionId, 5)
            if (turns.isEmpty()) return@withContext emptyList()

            val historyText = turns.joinToString("\n")
            val reflectPrompt = buildReflectPrompt(historyText)
            
            val resultBuilder = StringBuilder()
            neuralGateway.routeRequest(reflectPrompt).collect { resultBuilder.append(it) }
            val extractedFacts = resultBuilder.toString()

            parseAndVerifyFacts(extractedFacts, sessionId)
        } catch (e: Exception) {
            Timber.e(e, "[MEMORY_REFLECTOR] Extraction failed | Session: $sessionId")
            emptyList()
        }
    }

    private fun buildReflectPrompt(history: String): String {
        return """
            [TASK: SEMANTIC_EXTRACTION]
            Extract atomic facts from history. Format: ENTITY1 | RELATION | ENTITY2
            Focus on: allergies, drug preferences, educational progress, and critical safety needs.
            
            HISTORY:
            $history
            
            FACTS:
        """.trimIndent()
    }

    private suspend fun parseAndVerifyFacts(raw: String, sessionId: String): List<String> {
        val facts = mutableListOf<String>()
        raw.lines().forEach { line ->
            val parts = line.split("|").map { it.trim() }
            if (parts.size == 3) {
                val factContent = "${parts[0]} ${parts[1]} ${parts[2]}"
                val validation = knowledgeGuard.validate(factContent, ValidationLevel.REFLECTION)
                if (validation.isValid) {
                    facts.add(factContent)
                    updatePermanentGraph(factContent, parts[1])
                }
            }
        }
        return facts
    }

    private suspend fun updatePermanentGraph(content: String, relation: String) {
        try {
            val nodeId = "ref_${System.currentTimeMillis()}"
            // Updated to match GraphNode(id, label, type, metadata, importance)
            graphDao.insertNode(GraphNode(nodeId, content, "IDENTITY", "{}", 0.8f))
            // Updated to match GraphEdge(id, sourceId, targetId, relation, impactScore, source)
            // id=0 for autogeneration
            graphDao.insertEdge(GraphEdge(0, "user_main", nodeId, relation, 0.8f, "MEMORY_REFLECTION"))
        } catch (e: Exception) {
            Timber.e(e, "[MEMORY_REFLECTOR] Graph update failed")
        }
    }
}
