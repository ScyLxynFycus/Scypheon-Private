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
 * Enterprise Grade: Verifies full text responses without hallucination mocks.
 */
@Singleton
class RoomMedicalGroundingEngine @Inject constructor(
    private val pharmacopeiaDao: PharmacopeiaDao,
    private val knowledgeDao: KnowledgeDao,
    private val triageGateway: MedicalTriageGateway
) : MedicalGroundingEngine {

    override suspend fun verify(text: String, domain: String): GroundingResult = withContext(Dispatchers.IO) {
        try {
            val lowerText = text.lowercase()
            
            // 1. Dynamic Entity Extraction: Check for specific drugs mentioned in the text
            val tokens = lowerText.split(Regex("\\W+")).filter { it.length > 2 }
            
            if (domain == "medical") {
                val dbHits = pharmacopeiaDao.getDrugsByTokens(tokens)
                if (dbHits.isNotEmpty()) {
                    // Safety Gate: Hard check for lethal numerical hallucinations within the text
                    val numberMatch = Regex("(\\d+)\\s*(mg|g|ml)").find(lowerText)
                    if (numberMatch != null) {
                        val value = numberMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        val unit = numberMatch.groupValues[2]
                        
                        // If LLM hallucinates an absurd dosage in free text, block it mathematically
                        if ((unit == "mg" && value > 5000) || (unit == "g" && value > 5)) {
                            return@withContext GroundingResult(
                                confidence = 0.1f, // Fails the 0.65 threshold
                                sources = listOf("SAFETY_SYSTEM: Blocked lethal dosage hallucination (>5000mg/5g)."),
                                domain = domain,
                                exactMatch = false
                            )
                        }
                    }
                    
                    return@withContext GroundingResult(
                        confidence = 0.95f,
                        sources = dbHits.map { "${it.drugName}: ${it.dosage}" },
                        domain = domain,
                        exactMatch = true
                    )
                }
            }

            // 2. FTS Search for general context
            val cleanTerm = com.scypheon.sdk.core.humanitarian.medical.FtsSanitizer.sanitize(text).take(150)
            val kResults = if (cleanTerm.isNotBlank()) {
                knowledgeDao.search(cleanTerm, limit = 5)
            } else {
                emptyList()
            }
            
            if (kResults.isNotEmpty()) {
                val maxConfidence = kResults.maxOf { it.confidence }
                val threshold = maxConfidence * 0.8f
                val topSources = kResults
                    .filter { it.confidence >= threshold }
                    .map { "${it.source}: ${it.content}" }
                    .take(3)
                return@withContext GroundingResult(maxConfidence, topSources, domain, false)
            }

            // 3. Natural Conversation Passthrough (If not medical and no knowledge hit, assume safe chat)
            // To prevent blocking general greetings like "Hello"
            val isMedicalContext = lowerText.contains(Regex("(obat|dosis|mg|sakit|nyeri|paracetamol|medicine|dose|pain)"))
            if (!isMedicalContext) {
                return@withContext GroundingResult(0.85f, listOf("SYSTEM: General non-critical conversation."), domain, false)
            }

            GroundingResult(0.3f, emptyList(), domain, false) // Fails medical context without DB hits
        } catch (e: Exception) {
            GroundingResult(0.0f, emptyList(), domain, false)
        }
    }
}
