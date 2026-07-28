package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import com.scypheon.sdk.core.memory.DualMemoryManager
import kotlinx.coroutines.flow.reduce
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Sub-System: Medicine Analytic Engine.
 * Decoupled pharmacological analysis logic from UI/Vision sensors.
 */
@Singleton
class MedicineAnalyticEngine @Inject constructor(
    private val llmEngine: LiteRtEliteEngine,
    private val memoryManager: DualMemoryManager,
    private val interactionChecker: DrugInteractionChecker,
    private val dao: PharmacopeiaDao
) {
    suspend fun analyzeMedicine(rawOcrText: String): String {
        Timber.i("💊 Raw Medicine OCR: $rawOcrText")

        val userAllergies = memoryManager.getUserAllergies()
        val prompt = """
            You are a strict, highly accurate offline pharmacist AI.
            Raw text: "$rawOcrText"
            Patient Allergies: $userAllergies
            Task:
            1. Identify the medicine name.
            2. Identify the dosage and instructions.
            3. CROSS-CHECK: Is this medicine dangerous given the patient's allergies?
            Output a short, verbal summary to be spoken aloud.
            Start with "DANGER" if it conflicts with allergies, otherwise start with "Medicine identified".
        """.trimIndent()

        // Dynamic Entity Resolution via WHO Pharmacopeia
        val cleanOcr = FtsSanitizer.sanitize(rawOcrText)
        val resolvedIds = if (cleanOcr.isNotBlank()) {
            try {
                dao.resolveIds(cleanOcr)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve IDs from OCR text using FTS: $cleanOcr")
                emptyList()
            }
        } else {
            emptyList()
        }
        val detectedDrug = if (resolvedIds.isNotEmpty()) {
            dao.getDrugById(resolvedIds.first())?.genericName
        } else null

        var interactionWarning = ""
        if (detectedDrug != null) {
            val prescriptions = memoryManager.getCurrentPrescriptions()
            val interaction = interactionChecker.checkInteraction(detectedDrug, prescriptions)
            if (interaction != null) {
                interactionWarning = "CRITICAL ALARM: $interaction"
            }
        }

        return try {
            var aiResponse = llmEngine.generateResponse(prompt).reduce { acc, value -> acc + value }
            if (interactionWarning.isNotEmpty()) {
                aiResponse = "$interactionWarning $aiResponse"
            }
            aiResponse
        } catch (e: Exception) {
            Timber.e(e, "Gemma inference failed")
            "Error analyzing medicine offline."
        }
    }
}
