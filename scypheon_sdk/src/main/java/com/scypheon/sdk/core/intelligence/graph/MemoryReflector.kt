package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.data.RoomConversationRepository
import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.memory.MemoryDao
import com.scypheon.sdk.core.memory.MemoryEntry
import com.scypheon.sdk.core.memory.MemoryTier

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
    private val neuralGateway: NeuralGateway,
    private val memoryDao: MemoryDao
) : MemoryReflector {

    private val lastReflectionTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override suspend fun reflect(sessionId: String): List<String> = withContext(Dispatchers.IO) {
        Timber.i("[MEMORY_REFLECTOR] Initiating reflection check | Session: $sessionId")

        return@withContext try {
            val allTurns = conversationRepository.getRecentTurns(sessionId, 10)
            if (allTurns.size < 8) {
                Timber.d("[MEMORY_REFLECTOR] Skipping reflection: turn count (${allTurns.size}) < 8")
                return@withContext emptyList()
            }

            val lastTurn = allTurns.lastOrNull() ?: ""
            val hasTrigger = lastTurn.contains("remember", ignoreCase = true) ||
                             lastTurn.contains("previously", ignoreCase = true) ||
                             lastTurn.contains("yesterday", ignoreCase = true) ||
                             lastTurn.contains("kemarin", ignoreCase = true) ||
                             lastTurn.contains("recall", ignoreCase = true) ||
                             lastTurn.contains("dulu", ignoreCase = true)

            val lastReflect = lastReflectionTimes[sessionId] ?: 0L
            val timeSinceLast = System.currentTimeMillis() - lastReflect
            
            // Limit reflection to once every 5 minutes unless a trigger keyword is present
            val shouldReflect = hasTrigger || timeSinceLast >= 300000L
            
            if (!shouldReflect) {
                Timber.d("[MEMORY_REFLECTOR] Skipping reflection: rate limited (${timeSinceLast / 1000}s since last)")
                return@withContext emptyList()
            }

            Timber.i("[MEMORY_REFLECTOR] Performing background reflection...")
            val turns = allTurns.takeLast(5)
            val historyText = turns.joinToString("\n")
            val reflectPrompt = buildReflectPrompt(historyText)
            
            val resultBuilder = StringBuilder()
            neuralGateway.routeRequest(reflectPrompt).collect { resultBuilder.append(it) }
            val extractedFacts = resultBuilder.toString()

            lastReflectionTimes[sessionId] = System.currentTimeMillis()
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
                    saveEpisodicMemory(sessionId, factContent, parts[1])
                }
            }
        }
        return facts
    }

    private suspend fun saveEpisodicMemory(sessionId: String, fact: String, relation: String) = withContext(Dispatchers.IO) {
        val contentHash = sha256(fact)

        // 1. THE DEDUP GATE
        if (memoryDao.existsByHash(contentHash, sessionId)) {
            Timber.d("[Memory] Skipped exact duplicate (hash match): ${fact.take(30)}...")
            return@withContext
        }

        // 2. Write to Hierarchical Memory (New System)
        val entry = MemoryEntry(
            sessionId = sessionId,
            content = fact,
            contentHash = contentHash,
            tier = MemoryTier.EPISODIC
        )
        memoryDao.insertOrIgnore(entry)

        // 3. Write to Legacy Graph
        try {
            val nodeId = "ref_${System.currentTimeMillis()}"
            graphDao.insertNode(GraphNode(nodeId, fact, "IDENTITY", "{}", 0.8f))
            graphDao.insertEdge(GraphEdge(0, "user_main", nodeId, relation, 0.8f, "MEMORY_REFLECTION"))
        } catch (e: Exception) {
            Timber.w(e, "[Memory] Legacy graph write failed, but hierarchical memory saved.")
        }
        
        Timber.i("[Memory] Saved new episodic memory: ${fact.take(30)}...")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
