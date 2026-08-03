package com.scypheon.sdk.core.grounding

import com.scypheon.sdk.core.humanitarian.medical.PharmacopeiaDao
import com.scypheon.sdk.core.humanitarian.medical.MedicalTriageGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of grounding using the central AppDatabase.
 * Bridges pharmacological data (PharmacopeiaDao) and general humanitarian facts (KnowledgeDao).
 */
@Singleton
class RoomMedicalGroundingEngine @Inject constructor(
    private val pharmacopeiaDao: PharmacopeiaDao,
    private val knowledgeDao: KnowledgeDao,
    private val triageGateway: MedicalTriageGateway
) : MedicalGroundingEngine {

    override suspend fun verify(term: String, domain: String): GroundingResult = withContext(Dispatchers.IO) {
        try {
            // 1. Route based on domain
            if (domain == "medical") {
                val exact = pharmacopeiaDao.getByDrugName(term.lowercase())
                if (exact != null) {
                    return@withContext GroundingResult(
                        confidence = 1.0f,
                        sources = listOf("${exact.source}: ${exact.dosage}"),
                        domain = domain,
                        exactMatch = true
                    )
                }
            }

            // 2. Try general knowledge base
            val kExact = knowledgeDao.getExact(term.lowercase(), domain)
            if (kExact != null) {
                return@withContext GroundingResult(1.0f, listOf(kExact), domain, true)
            }

            // 3. Fuzzy search in knowledge base
            val kResults = knowledgeDao.search(term, limit = 5)
            if (kResults.isNotEmpty()) {
                val avgConfidence = kResults.map { it.confidence }.average().toFloat()
                val sources = kResults.map { "${it.source}: ${it.content}" }
                return@withContext GroundingResult(avgConfidence, sources, domain, false)
            }

            // 4. Deterministic safety fallback
            val fallback = deterministicFallback(term, domain)
            if (fallback != null) return@withContext fallback

            GroundingResult(0.0f, emptyList(), domain, false)
        } catch (e: Exception) {
            GroundingResult(0.0f, emptyList(), domain, false)
        }
    }

    private fun deterministicFallback(term: String, domain: String): GroundingResult? {
        val lower = term.lowercase()
        return when {
            domain == "medical" && lower.contains("paracetamol") ->
                GroundingResult(0.95f, listOf("WHO: Standard adult 500-1000mg q4-6h. Max 4g/day."), domain, false)
            domain == "medical" && lower.contains("warfarin") ->
                GroundingResult(0.95f, listOf("FDA: High interaction risk. Avoid NSAIDs."), domain, false)
            domain == "resilience" && lower.contains("evacuation") ->
                GroundingResult(0.90f, listOf("UN: Follow local emergency protocols. Prioritize vulnerable."), domain, false)
            else -> null
        }
    }
}
