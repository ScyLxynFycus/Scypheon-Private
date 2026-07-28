package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.telemetry.TelemetryDao
import com.scypheon.sdk.core.telemetry.TelemetryEvent
import com.scypheon.sdk.core.security.AuditChain
import java.util.UUID
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@SafetyCritical
@Singleton
class ClinicalValidator @Inject constructor(
    private val dao: PharmacopeiaDao,
    private val triageGateway: MedicalTriageGateway,
    private val semanticDetector: com.scypheon.sdk.core.safety.helios.EmbeddingGemmaAnomalyDetector,
    private val disclaimerManager: MedicalDisclaimerManager,
    private val telemetry: TelemetryDao,
    private val auditChain: AuditChain,
    private val memoryManager: dagger.Lazy<com.scypheon.sdk.core.memory.DualMemoryManager>
) {

    data class ValidationResult(
        val isSafe: Boolean,
        val detectedDrugs: List<String>,
        val alertMessage: String?,
        val confidenceScore: Float,
        val reasoning: String,
        val source: String? = null
    )

    /**
     * Evaluates the ENTIRE response in a 1-Pass execution. Highly efficient for battery and RAM.
     */
    suspend fun harden(response: String, userQuery: String, traceId: String): String {
        Timber.d("🧐 [CLINICAL VALIDATOR] Hardening full response | Trace: $traceId")
        
        val allergiesString = try {
            memoryManager.get().getUserAllergies()
        } catch (e: Exception) {
            Timber.w(e, "[ClinicalValidator] Failed to retrieve user allergies from memory")
            ""
        }
        val userAllergies = parseAllergies(allergiesString)
        
        val validation = validateResponse(responseText = response, userAllergies = userAllergies) // Check entire text at once
        
        if (!validation.isSafe) {
            logOverride(traceId, validation.detectedDrugs.joinToString(), "UNSAFE_MEDICAL_CONTENT", validation.reasoning)
            
            // FAIL-CLOSED: If there is even 1 fatal error, block the entire prescription to prevent harmful context
            return """
                🛑 [CLINICAL INTERCEPT] 
                The AI's generated response was blocked by Scypheon's Offline Validator.
                Reason: ${validation.alertMessage}
                
                Please consult a human clinician or rely on your standard emergency protocol.
            """.trimIndent()
        }
        
        // If safe, append grounding source at the end
        return if (validation.detectedDrugs.isNotEmpty()) {
            response + "\n\n✅ [Verified against Offline OpenFDA Database]"
        } else {
            response
        }
    }

    suspend fun validateResponse(
        responseText: String,
        userAllergies: List<String> = emptyList(),
        patientWeight: Float? = null,
        isPregnant: Boolean = false
    ): ValidationResult {
        
        // 1. Ekstraksi Token Batch (Ambil kata-kata unik saja)
        val normalized = responseText.lowercase()
        val tokens = normalized.split(Regex("[^a-z0-9.-]"))
            .map { it.trim('.', '-', ',') }
            .filter { it.length > 2 }
            .toSet() // Gunakan SET untuk membuang kata duplikat
            .toList()

        if (tokens.isEmpty()) return ValidationResult(true, emptyList(), null, 1.0f, "No tokens.")

        // 2. THE ZERO-LATENCY MAGIC: 1 kali hit ke Database untuk 100 kata!
        val detectedDrugs = dao.getDrugsByTokens(tokens)

        if (detectedDrugs.isEmpty()) {
            return ValidationResult(true, emptyList(), null, 1.0f, "No clinical entities detected.")
        }

        val drugNames = detectedDrugs.map { it.drugName }

        for (drug in detectedDrugs) {
            // Evaluasi Alergi
            if (userAllergies.any { it.lowercase() == drug.drugName.lowercase() || it.lowercase() == drug.genericName?.lowercase() }) {
                return ValidationResult(false, drugNames, "Lethal Allergy detected for ${drug.drugName}", 0f, "User allergic to ${drug.drugName}")
            }

            // Evaluasi High Risk
            if (drug.severity == "HIGH_ALERT" || drug.severity == "CRITICAL") {
                Timber.e("🚨 [CLINICAL] HIGH RISK DRUG DETECTED: ${drug.drugName}")
                return ValidationResult(
                    isSafe = false, 
                    detectedDrugs = drugNames, 
                    alertMessage = disclaimerManager.getHardWarning(drug.drugName, drug.severity), 
                    confidenceScore = 0f, 
                    reasoning = "High-risk drug requires human authorization.",
                    source = drug.source
                )
            }

            // Evaluasi Kehamilan
            if (isPregnant && drug.contraindications.contains("pregnancy", ignoreCase = true)) {
                return ValidationResult(
                    isSafe = false,
                    detectedDrugs = drugNames,
                    alertMessage = "DANGER: ${drug.drugName} is strictly contraindicated in pregnancy.",
                    confidenceScore = 0f,
                    reasoning = "Contraindicated for pregnancy.",
                    source = drug.source
                )
            }

            // Evaluasi Dosis (Mencari kekuatan dan frekuensi)
            val maxDaily = drug.maxDailyMg ?: 4000
            val strengthPattern = Regex("""(\d+(?:\.\d+)?)\s*(mg|g|milligrams?|grams?)""", RegexOption.IGNORE_CASE)
            
            val strengths = strengthPattern.findAll(normalized).mapNotNull { match ->
                val value = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val unit = match.groupValues[2].lowercase()
                when {
                    unit.startsWith("g") && !unit.startsWith("mg") -> value * 1000f
                    else -> value
                }
            }.toList()

            if (strengths.isNotEmpty()) {
                val frequencyMultiplier = extractFrequencyMultiplier(normalized)
                val cumulativeDoses = strengths.map { it * frequencyMultiplier }
                val maxCumulative = cumulativeDoses.maxOrNull() ?: 0f
                
                if (maxCumulative > maxDaily) {
                    Timber.w("[ClinicalValidator] Dosage validation failed: max=${maxDaily}mg, calculated=${maxCumulative}mg")
                    return ValidationResult(
                        false, drugNames,
                        "CRITICAL: Suggested daily dosage ($maxCumulative mg) exceeds safe daily limit ($maxDaily mg) for ${drug.drugName}.",
                        0.1f, "Cumulative dosage limit violation (calculated: $maxCumulative mg).", drug.source
                    )
                }
            }
        }

        // 3. Semantic Evaluation (Called ONLY ONCE at the end)
        val groundTruth = detectedDrugs.joinToString(" | ") { it.indications }
        val semanticSimilarity = semanticDetector.calculateSimilarity(responseText, groundTruth)
        
        if (semanticSimilarity < 0.35f) {
            return ValidationResult(false, drugNames, disclaimerManager.getHallucinationWarning(), semanticSimilarity, "Semantic hallucination detected.")
        }

        return ValidationResult(true, drugNames, null, 0.95f, "Validated against elite pharmacopeia.")
    }

    private suspend fun logOverride(traceId: String, drug: String, type: String, detail: String) {
        val payload = "[$type] Drug: $drug | Detail: $detail"
        telemetry.insert(TelemetryEvent(
            eventId = UUID.randomUUID().toString(),
            type = "CLINICAL_OVERRIDE",
            payload = "Trace: $traceId | $payload",
            timestamp = System.currentTimeMillis(),
            synced = false
        ))
        auditChain.logEvent("CLINICAL_OVERRIDE", "Trace: $traceId | $payload")
    }

    /**
     * Parses frequency statements to calculate doses per day.
     */
    private fun extractFrequencyMultiplier(text: String): Float {
        val normalized = text.lowercase()
        
        val pattern = Regex("""(once|twice|three|four|five|six|seven|eight|nine|ten|\d+)\s*(?:times?|x)\s*(?:a\s*day|daily|per\s*day)""")
        pattern.find(normalized)?.let { match ->
            val valueStr = match.groupValues[1]
            return when (valueStr) {
                "once", "one" -> 1f
                "twice", "two" -> 2f
                "three" -> 3f
                "four" -> 4f
                "five" -> 5f
                "six" -> 6f
                "seven" -> 7f
                "eight" -> 8f
                "nine" -> 9f
                "ten" -> 10f
                else -> valueStr.toFloatOrNull() ?: 1f
            }
        }
        
        Regex("""every\s*(\d+(?:\.\d+)?)\s*hours?""").find(normalized)?.let {
            val hours = it.groupValues[1].toFloatOrNull() ?: return@let
            if (hours > 0) return 24f / hours
        }
        
        if (normalized.contains("twice")) return 2f
        if (normalized.contains("once")) return 1f
        
        Regex("""(\d+)x\s*daily""").find(normalized)?.let {
            return it.groupValues[1].toFloatOrNull() ?: 1f
        }
        
        return 1f // Default: single dose
    }

    /**
     * Cross-checks a drug against a list of known allergies.
     */
    fun checkAllergyInteraction(drugName: String, userAllergies: List<String>): AllergyCheckResult {
        val normalizedDrug = drugName.lowercase()
        for (allergy in userAllergies) {
            val normalizedAllergy = allergy.lowercase().trim()
            if (normalizedAllergy.isNotEmpty() && (normalizedDrug.contains(normalizedAllergy) || normalizedAllergy.contains(normalizedDrug))) {
                return AllergyCheckResult.Unsafe(allergy)
            }
        }
        return AllergyCheckResult.Safe
    }

    /**
     * Robust parser for unstructured allergy strings from memory.
     */
    fun parseAllergies(allergiesString: String): List<String> {
        if (allergiesString.isBlank() || allergiesString.contains("No known", ignoreCase = true)) return emptyList()
        
        val allergens = mutableListOf<String>()
        val parts = allergiesString.split(Regex("[,;.]|\\band\\b", RegexOption.IGNORE_CASE))
        for (part in parts) {
            val trimmed = part.trim().lowercase()
            if (trimmed.isBlank() || trimmed == "none recorded" || trimmed == "no known allergies" || trimmed == "nka" || trimmed == "none") continue
            
            // Check patterns like: "user is allergic to peanuts" or "user is allergic to penicillin"
            val match = Regex("""(?:allergic to|alergi(?: terhadap| sama| dengan)?)\s+([a-z0-9\s-]+)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (match != null) {
                val allergen = match.groupValues[1].trim()
                if (allergen.isNotEmpty()) allergens.add(allergen)
            } else {
                val cleanedPart = trimmed.replace("graph deductions:", "").trim()
                if (cleanedPart.isNotEmpty() && !cleanedPart.contains("is allergic") && !cleanedPart.contains("alergi")) {
                    allergens.add(cleanedPart)
                }
            }
        }
        return allergens.distinct()
    }
}

sealed class AllergyCheckResult {
    object Safe : AllergyCheckResult()
    data class Unsafe(val allergen: String) : AllergyCheckResult()
}
