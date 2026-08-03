package com.scypheon.sdk.core.agent.skills

import com.scypheon.sdk.core.humanitarian.medical.MedicalTriageGateway
import com.scypheon.sdk.core.humanitarian.medical.TriageResult
import com.scypheon.sdk.core.humanitarian.medical.DrugInteractionChecker
import com.scypheon.sdk.core.humanitarian.medical.PharmacopeiaDao
import com.scypheon.sdk.core.grounding.MedicalGroundingEngine
import com.scypheon.sdk.core.memory.DualMemoryManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MedicalSkill:
 * The flagship capability of Scypheon.
 * Central authority for all pharmacological and triage logic.
 */
@Singleton
class MedicalSkill @Inject constructor(
    private val triageGateway: MedicalTriageGateway,
    private val groundingEngine: MedicalGroundingEngine,
    private val dao: PharmacopeiaDao,
    private val interactionChecker: DrugInteractionChecker,
    private val memoryManager: DualMemoryManager
) {

    suspend fun getDosage(drug: String, ageGroup: String): String {
        val drugIds = dao.resolveIds(drug)
        val entity = drugIds.firstOrNull()?.let { dao.getDrugById(it) }

        return if (entity != null) {
            // Using unified dosage field from PharmacopeiaEntry
            "[VERIFIED_DOSAGE] $drug: ${entity.dosage}"
        } else {
            "Drug '$drug' not found in local pharmacopeia. Consult a professional."
        }
    }

    suspend fun checkInteraction(drug: String): String {
        val prescriptions = memoryManager.getCurrentPrescriptions()
        val result = interactionChecker.checkInteraction(drug, prescriptions)
        return result ?: "No known interactions found for $drug with current prescriptions."
    }

    suspend fun getFirstAid(symptom: String): String {
        val protocol = triageGateway.getFirstAidProtocol(symptom)
        return protocol?.instructionsEn ?: "Standard first aid: Monitor vitals and seek help."
    }

    suspend fun performTriage(query: String): String {
        val result = triageGateway.triage(query)
        
        return when (result) {
            is TriageResult.Emergency -> "🚨 [EMERGENCY] ${result.reason}"
            is TriageResult.CriticalInteraction -> "⛔ [CRITICAL_INTERACTION] ${result.drugA} + ${result.drugB} is dangerous!"
            is TriageResult.General -> "[MEDICAL_MODE] Analysis: Verified medical context found."
            else -> "No critical medical data found."
        }
    }
}
