package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalReranker @Inject constructor(
    private val llmEngine: LiteRtEliteEngine
) {
    /**
     * Reranks medical candidates using LLM reasoning.
     * Implements Phase 3: Cross-Encoder Reranker logic.
     */
    suspend fun rerank(query: String, candidates: List<Any>): Pair<Any, String>? = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext null
        if (candidates.size == 1) return@withContext (candidates[0] to "Direct match found in database.")

        val candidateListText = candidates.joinToString("\n") { candidate ->
            when (candidate) {
                is PharmacopeiaEntry -> "- ID: ${candidate.id} | Name: ${candidate.drugName} (${candidate.genericName}) | Indications: ${candidate.indications}"
                is FirstAidEntity -> "- ID: ${candidate.conditionName} | Protocol: ${candidate.conditionName} | Keywords: ${candidate.localSearchKeywords}"
                else -> "- $candidate"
            }
        }

        val rerankPrompt = """
            You are a clinical expert reranking system for humanitarian aid.
            USER SYMPTOMS: "$query"
            
            CANDIDATE LIST FROM DATABASE:
            $candidateListText
            
            TASK:
            1. Analyze the symptoms and compare them against the indications of each candidate.
            2. Determine which candidate is the SAFEST and MOST RELEVANT.
            3. Consider complications.
            
            OUTPUT FORMAT (MANDATORY):
            Reasoning: [Your step-by-step clinical reasoning]
            Selected ID: [The ID of the best match from the list above]
        """.trimIndent()

        try {
            val response = llmEngine.generateResponse(rerankPrompt).reduce { acc, value -> acc + value }
            Timber.i("🧠 Medical Reranker Reasoning:\n$response")

            val selectedId = response.substringAfter("Selected ID:").trim()
            val reasoning = response.substringBefore("Selected ID:").substringAfter("Reasoning:").trim()

            val bestMatch = candidates.find { 
                when (it) {
                    is PharmacopeiaEntry -> it.id == selectedId
                    is FirstAidEntity -> it.conditionName == selectedId
                    else -> false
                }
            } ?: candidates[0]

            return@withContext (bestMatch to reasoning)
        } catch (e: Exception) {
            Timber.e(e, "Medical Reranker failed.")
            return@withContext (candidates[0] to "LLM Reranker failed. Using top database hit.")
        }
    }
}
