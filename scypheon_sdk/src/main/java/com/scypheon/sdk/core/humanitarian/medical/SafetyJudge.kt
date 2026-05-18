package com.scypheon.sdk.core.humanitarian.medical

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.scypheon.sdk.core.annotations.SafetyCritical
import javax.inject.Inject

sealed class MedicalResult {
    data class Interaction(val severity: Severity) : MedicalResult()
    data class DrugInfo(val contraindications: String) : MedicalResult()
}

data class ToolObservation(val result: MedicalResult)

sealed class JudgeVerdict {
    data object Passed : JudgeVerdict()
    data class Violation(val reason: String, val correctionPrompt: String) : JudgeVerdict()
}

@SafetyCritical
class SafetyJudge @Inject constructor(private val dao: PharmacopeiaDao) {
    suspend fun evaluate(input: String, observations: List<ToolObservation>): JudgeVerdict = withContext(Dispatchers.IO) {
        val normalized = input.lowercase()
        
        // Rule 1: Critical interaction must not be downplayed
        val criticalInteractions = observations.mapNotNull { it.result as? MedicalResult.Interaction }
            .filter { it.severity in setOf(Severity.FATAL, Severity.MAJOR) }
        if (criticalInteractions.isNotEmpty()) {
            return@withContext JudgeVerdict.Violation(
                "Critical interaction detected. LLM must prioritize warning.",
                "CORRECTION: You missed a FATAL/MAJOR interaction. Warn immediately. Advise stopping both medications."
            )
        }

        // Rule 2: Contraindication match
        val drugInfos = observations.mapNotNull { it.result as? MedicalResult.DrugInfo }
        for (info in drugInfos) {
            val conditions = info.contraindications.lowercase().split(",").map { it.trim() }
            if (conditions.any { normalized.contains(it) && it.length > 3 }) {
                return@withContext JudgeVerdict.Violation(
                    "High-risk contraindication matches user condition.",
                    "CORRECTION: User condition matches MAJOR/FATAL contraindication. Explicitly warn against usage."
                )
            }
        }

        JudgeVerdict.Passed
    }
}
