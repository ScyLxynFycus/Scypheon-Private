package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.telemetry.TelemetryDao
import com.scypheon.sdk.core.security.AuditChainDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class Severity { FATAL, MAJOR, MODERATE, MINOR, NONE }

sealed class TriageResult {
    data class CriticalInteraction(val drugA: String, val drugB: String, val severity: Severity, val mechanism: String = "", val effect: String = "") : TriageResult()
    data class NoResults(val message: String) : TriageResult()
    data class Emergency(val firstAid: FirstAidEntity? = null, val reason: String = "EMERGENCY") : TriageResult()
    data class Warning(val reason: String) : TriageResult()
    data object Safe : TriageResult()
    data class General(val rawInput: String, val detectedDrugIds: List<String>, val dataExpired: Boolean = false) : TriageResult()
}

@SafetyCritical
@Singleton
class MedicalTriageGateway @Inject constructor(
    private val dao: PharmacopeiaDao,
    private val telemetry: TelemetryDao,
    private val auditChain: AuditChainDao
) {

    suspend fun triage(input: String, patientWeight: Float? = null): TriageResult = withContext(Dispatchers.IO) {
        val normalized = input.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\s]"), " ")
        
        // 1. PHASE 1: EMERGENCY DETECTION
        val criticalKeywords = listOf("chest pain", "difficulty breathing", "shock", "bleeding", "unconscious", "seizure", "stroke", "poison")
        if (criticalKeywords.any { normalized.contains(it) }) {
            val protocol = dao.getFirstAidProtocol(normalized)
            if (protocol != null) {
                return@withContext TriageResult.Emergency(protocol, reason = "Critical symptoms matched.")
            }
        }

        // 2. PHASE 2: ENTITY RESOLUTION
        val tokens = normalized.split("\\s+".toRegex()).filter { it.length > 2 }
        val drugIds = mutableSetOf<String>()
        for (token in tokens) {
            val drug = dao.getByDrugName(token)
            if (drug != null) {
                drugIds.add(drug.id)
            }
        }
        
        val drugs = drugIds.mapNotNull { dao.getDrugById(it) }

        // 3. PHASE 3: INTERACTION SCAN
        if (drugs.size >= 2) {
            for (i in drugs.indices) {
                for (j in i + 1 until drugs.size) {
                    val interactionDetail = dao.getInteraction(drugs[i].drugName, drugs[j].drugName)
                    if (interactionDetail != null) {
                        return@withContext TriageResult.CriticalInteraction(
                            drugA = drugs[i].drugName, drugB = drugs[j].drugName,
                            severity = Severity.MAJOR, effect = interactionDetail
                        )
                    }
                }
            }
        }

        if (drugs.isEmpty()) {
            return@withContext TriageResult.NoResults("No validated medical entities found.")
        }

        return@withContext TriageResult.General(input, drugIds.toList())
    }

    /**
     * Final audit of a response to catch any dangerous interactions introduced during synthesis.
     */
    suspend fun auditFinalResponse(traceId: String, text: String): TriageResult {
        Timber.d("🔍 [AUDIT] Final medical audit for Trace: $traceId")
        return triage(text)
    }

    suspend fun validateDosage(drugId: String, amountMg: Float, weightKg: Float?): TriageResult = withContext(Dispatchers.IO) {
        val drug = dao.getDrugById(drugId) ?: return@withContext TriageResult.NoResults("Drug not found in pharmacopeia.")
        
        val maxLimit = if (weightKg != null && (drug.maxMgPerKg ?: 0f) > 0) {
            (drug.maxMgPerKg ?: 0f) * weightKg 
        } else {
            (drug.maxDailyMg ?: 0).toFloat()
        }
        
        return@withContext if (maxLimit > 0 && amountMg > maxLimit) {
            TriageResult.Warning("Dosage $amountMg mg exceeds safe limit of $maxLimit mg for ${drug.drugName}.")
        } else {
            TriageResult.Safe
        }
    }

    suspend fun getFirstAidProtocol(query: String): FirstAidEntity? {
        return dao.getFirstAidProtocol(query)
    }
}

object SafetyOverrideEngine {
    fun generateCriticalAlert(triage: TriageResult.CriticalInteraction): String = """
        ⛔ KRITICAL MEDICAL ALERT (OFFLINE)
        Interaksi Berbahaya: ${triage.drugA} + ${triage.drugB}
        Tingkat Bahaya: ${triage.severity}
    """.trimIndent()

    fun generateEmergencyAlert(protocol: FirstAidEntity? = null): String = """
        🚨 DARURAT: ${protocol?.conditionName?.uppercase() ?: "GEJALA KRITIS"} TERDETEKSI
    """.trimIndent()
}
