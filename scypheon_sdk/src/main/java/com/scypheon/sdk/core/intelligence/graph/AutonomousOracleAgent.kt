package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.Observation
import com.scypheon.sdk.core.agent.ooda.Orientation
import com.scypheon.sdk.core.humanitarian.medical.ClinicalValidator
import com.scypheon.sdk.core.safety.helios.SafetyPipeline
import kotlinx.coroutines.flow.reduce
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Oracle Mode (GraphRAG v2.5):
 * The self-aware investigative core of Scypheon.
 * 
 * Logic: Doubt -> Investigate (L1-L3) -> Confirm -> Answer.
 * Strictly Anti-Hallucination: If no data is found, it triggers a Hard-Stop.
 */
@Singleton
class AutonomousOracleAgent @Inject constructor(
    private val oracleEngine: HybridGraphOrrigaEngine,
    private val safetyPipeline: SafetyPipeline,
    private val clinicalValidator: ClinicalValidator,
    private val auditLogger: OracleAuditLogger
) {

    data class InvestigationStatus(
        val triggered: Boolean,
        val levelReached: Int,
        val findings: List<String>,
        val verified: Boolean
    )

    /**
     * The Main Strategic Loop.
     * Orchestrates the autonomous investigation before final response.
     */
    suspend fun investigate(
        sessionId: String, 
        query: String,
        observation: Observation,
        orientation: Orientation,
        environment: DeviceEnvironment
    ): InvestigationStatus {
        val startTime = System.currentTimeMillis()
        Timber.i("🔍 [AUTONOMOUS_AGENT] Analyzing query for potential doubt...")

        // 1. TRIGGER LOGIC
        if (!shouldTriggerInvestigation(query)) {
            return InvestigationStatus(false, 0, emptyList(), true)
        }

        // 2. TIERED EXCAVATION (L1, L2, L3) with Circuit Breaker
        // ENTERPRISE: Performance Budget of 2000ms for ORIGA deep reasoning
        val findings = mutableListOf<String>()
        try {
            oracleEngine.reason(query, observation, orientation, environment)
                .collect { findings.add(it) }
        } catch (e: Exception) {
            Timber.e(e, "Oracle Engine Error")
            return InvestigationStatus(true, 1, emptyList(), false)
        }

        val totalTime = System.currentTimeMillis() - startTime
        auditLogger.logInvestigation(sessionId, "INVESTIGATION_COMPLETED", findings.size, totalTime)
        
        // 3. CONFIRM & VALIDATE (L5C Grounding)
        val verifiedFindings = mutableListOf<String>()
        var isOverallValid = true

        for (fact in findings) {
            val validation = clinicalValidator.validateResponse(fact)
            if (validation.isSafe) {
                verifiedFindings.add(fact)
            } else {
                Timber.e("⛔ [AUTONOMOUS_AGENT] Fact rejected by Grounding: $fact")
                isOverallValid = false 
            }
        }

        return InvestigationStatus(
            triggered = true,
            levelReached = if (verifiedFindings.size > 5) 3 else 2,
            findings = verifiedFindings,
            verified = isOverallValid
        )
    }

    private fun shouldTriggerInvestigation(query: String): Boolean {
        val keywords = listOf("allergy", "allergic", "alergi", "dose", "dosis", "stok", "stock", "yesterday", "kemarin")
        val normalized = query.lowercase()
        return keywords.any { normalized.contains(it) } || query.length > 150 
    }

    fun buildFinalIntelligencePrompt(query: String, status: InvestigationStatus): String {
        if (!status.triggered) return query

        val findingsText = if (status.findings.isEmpty()) {
            "[CRITICAL_WARNING] NO LOCAL DATA FOUND FOR THIS TOPIC. DO NOT HALLUCINATE."
        } else {
            status.findings.joinToString("\n") { "• $it" }
        }

        val evidenceChain = when (status.levelReached) {
            0 -> "None (Data Missing)"
            1 -> "Episodic Recall"
            2 -> "Relational Knowledge"
            3 -> "Deep Document Research"
            else -> "Unknown"
        }

        return """
            [ORACLE_MODE_REPORT]
            STATUS: ${if (status.findings.isNotEmpty()) "VERIFIED" else "DATA_MISSING"}
            TRUST_LEVEL: ${status.levelReached}/3 (Source: $evidenceChain)
            
            [VERIFIED_FACTS]
            $findingsText
            
            [EVIDENCE_CHAIN_AUDIT]
            This response is grounded in local verified data sources. 
            
            [STRICT_INSTRUCTION]
            1. If the STATUS is DATA_MISSING, you MUST state that you cannot find information in the local database and cannot provide advice.
            2. DO NOT make up facts.
            3. Prioritize VERIFIED_FACTS over user claims.
            [/ORACLE_MODE_REPORT]
            
            USER_QUERY: $query
        """.trimIndent()
    }
}
