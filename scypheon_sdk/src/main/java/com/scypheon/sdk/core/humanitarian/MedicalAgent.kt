package com.scypheon.sdk.core.humanitarian

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import com.scypheon.sdk.core.utils.LocaleHelper
import com.scypheon.sdk.core.utils.PromptLocalizer
import timber.log.Timber

/**
 * MedicalAgent: Safety-CRITICAL Medical Assistant
 *
 * Philosophy: "INI RANAH NYAWA. GAK BOLEH MAIN-MAIN."
 *
 * ALL allergy/drug checks are DETERMINISTIC code, NOT AI.
 * LLM is ONLY used for explanations with mandatory disclaimers.
 *
 * Safety Layers:
 * 1. Deterministic Database (SQLite drug info)
 * 2. Regex Parsing (dosage extraction)
 * 3. Red Banner Alert (visual + haptic + audio)
 */
@Suppress("unused") // Enterprise: Full medical safety API surface
class MedicalAgent(
    private val context: Context,
    private val userProfile: UserHealthProfile,
    private val drugDatabase: DrugDatabase
) {
    companion object {
        private const val TAG = "MedicalAgent"
        private const val DISCLAIMER = "⚠️ DISCLAIMER: This is NOT medical advice. Always consult a healthcare professional. Informasi ini BUKAN merupakan nasihat medis. Selalu konsultasikan dengan tenaga kesehatan profesional."
    }

    private var tts: TextToSpeech? = null
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Use app's locale for TTS
                tts?.language = LocaleHelper.getLocalizedTtsLocale(context)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ══════════════════════════════════════════════════════════════════════

    data class UserHealthProfile(
        val userId: String,
        val allergies: List<String>,           // ["Penicillin", "Aspirin", "Sulfa"]
        val conditions: List<String>,          // ["Diabetes", "Hypertension"]
        val currentMedications: List<String>,  // ["Metformin", "Amlodipine"]
        val bloodType: String?,
        val emergencyContact: String?
    )

    data class MedicineInfo(
        val name: String,
        val genericName: String?,
        val dosage: DosageInfo?,
        val ingredients: List<String>,
        val warnings: List<String>,
        val interactions: List<DrugInteraction>
    )

    data class DosageInfo(
        val frequency: Int?,      // 3 (times per day)
        val amount: Int?,         // 1 (tablet/capsule)
        val strength: Int?,       // 500 (mg)
        val unit: String?,        // "mg", "ml", "g"
        val timing: String?       // "after meals", "before sleep"
    )

    data class DrugInteraction(
        val drug1: String,
        val drug2: String,
        val severity: Severity,
        val description: String
    )

    enum class Severity { LOW, MODERATE, HIGH, CRITICAL }

    sealed class AllergyResult {
        data object Safe : AllergyResult()
        data class Danger(
            val allergen: String,
            val severity: Severity,
            val message: String
        ) : AllergyResult()
        data class InteractionWarning(
            val interactions: List<DrugInteraction>
        ) : AllergyResult()
    }

    // ══════════════════════════════════════════════════════════════════════
    // ALLERGY CHECK (DETERMINISTIC - NO AI!)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Check if drug is safe for user.
     * THIS IS PURE IF/ELSE LOGIC. NO AI INTERPRETATION.
     */
    fun checkAllergy(drugName: String, ingredients: List<String>): AllergyResult {
        Timber.d("$TAG: Checking allergies for $drugName")

        // 1. Check direct drug allergy
        for (allergy in userProfile.allergies) {
            if (drugName.contains(allergy, ignoreCase = true)) {
                return AllergyResult.Danger(
                    allergen = allergy,
                    severity = Severity.CRITICAL,
                    message = "⛔ DANGER! You are ALLERGIC to $allergy. DO NOT take this medication!"
                )
            }
        }

        // 2. Check ingredient allergies
        for (ingredient in ingredients) {
            for (allergy in userProfile.allergies) {
                if (ingredient.contains(allergy, ignoreCase = true) ||
                    allergy.contains(ingredient, ignoreCase = true)) {
                    return AllergyResult.Danger(
                        allergen = allergy,
                        severity = Severity.CRITICAL,
                        message = "⛔ DANGER! This medication contains $ingredient. You are ALLERGIC to $allergy!"
                    )
                }
            }
        }

        // 3. Check drug interactions with current medications
        val interactions = mutableListOf<DrugInteraction>()
        for (currentMed in userProfile.currentMedications) {
            val interaction = drugDatabase.checkInteraction(drugName, currentMed)
            if (interaction != null) {
                interactions.add(interaction)
            }
        }

        if (interactions.isNotEmpty()) {
            val critical = interactions.any { it.severity == Severity.CRITICAL || it.severity == Severity.HIGH }
            if (critical) {
                return AllergyResult.InteractionWarning(interactions)
            }
        }

        return AllergyResult.Safe
    }

    // ══════════════════════════════════════════════════════════════════════
    // DOSAGE PARSING (REGEX - NO AI!)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Parse dosage from OCR text using regex patterns.
     * No AI interpretation - pure pattern matching.
     */
    fun parseDosage(ocrText: String): DosageInfo {
        val text = ocrText.lowercase()

        // Pattern: "3 x 1 tablet" or "3x1"
        val frequencyPattern = Regex("""(\d+)\s*[x×]\s*(\d+)""")
        val frequencyMatch = frequencyPattern.find(text)

        val frequency = frequencyMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val amount = frequencyMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

        // Pattern: "500 mg" or "500mg" or "10ml"
        val strengthPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(mg|ml|g|mcg|iu)""", RegexOption.IGNORE_CASE)
        val strengthMatch = strengthPattern.find(text)

        val strength = strengthMatch?.groupValues?.getOrNull(1)?.replace(",", ".")?.toFloatOrNull()?.toInt()
        val unit = strengthMatch?.groupValues?.getOrNull(2)?.lowercase()

        // Timing patterns
        val timing = when {
            text.contains("after meals") || text.contains("setelah makan") -> "After meals"
            text.contains("before meals") || text.contains("sebelum makan") -> "Before meals"
            text.contains("before sleep") || text.contains("sebelum tidur") -> "Before sleep"
            (text.contains("morning") && text.contains("night")) || (text.contains("pagi") && text.contains("malam")) -> "Morning and night"
            text.contains("empty stomach") || text.contains("saat perut kosong") -> "On an empty stomach"
            else -> null
        }

        return DosageInfo(
            frequency = frequency,
            amount = amount,
            strength = strength,
            unit = unit,
            timing = timing
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // RED BANNER ALERT SYSTEM
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Show full-screen danger alert with haptic and audio.
     */
    fun showRedBanner(message: String, severity: Severity) {
        Timber.e("$TAG: RED BANNER - $message")

        // 1. Launch full-screen danger Activity
        val intent = Intent(context, DangerAlertActivity::class.java).apply {
            putExtra("message", message)
            putExtra("severity", severity.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)

        // 2. Haptic SOS Pattern
        // ENTERPRISE: minSdk 28 guarantees O, so always use VibrationEffect
        vibrator?.let { vib ->
            // SOS: ... --- ...
            val pattern = longArrayOf(
                0,    // start
                100, 100, 100, 100, 100, 300,  // S: ...
                300, 100, 300, 100, 300, 300,  // O: ---
                100, 100, 100, 100, 100, 0     // S: ...
            )
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }

        // 3. Speak warning
        tts?.speak("DANGER! $message", TextToSpeech.QUEUE_FLUSH, null, "danger_alert")
    }

    // ══════════════════════════════════════════════════════════════════════
    // MEDICINE LABEL READER
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Read and analyze medicine label from OCR text.
     */
    fun analyzeMedicineLabel(ocrText: String): MedicineAnalysis {
        val dosage = parseDosage(ocrText)

        // Extract drug name (usually first line or after "KOMPOSISI:")
        val drugName = extractDrugName(ocrText)

        // Extract ingredients
        val ingredients = extractIngredients(ocrText)

        // Check allergies (DETERMINISTIC)
        val allergyCheck = if (drugName != null) {
            checkAllergy(drugName, ingredients)
        } else {
            AllergyResult.Safe
        }

        return MedicineAnalysis(
            drugName = drugName,
            dosage = dosage,
            ingredients = ingredients,
            allergyResult = allergyCheck,
            disclaimer = DISCLAIMER
        )
    }

    data class MedicineAnalysis(
        val drugName: String?,
        val dosage: DosageInfo,
        val ingredients: List<String>,
        val allergyResult: AllergyResult,
        val disclaimer: String
    )

    private fun extractDrugName(text: String): String? {
        // Common patterns for drug names
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        // First line is often the brand name
        return lines.firstOrNull()?.let {
            it.replace(Regex("""[^\w\s]"""), "").trim().takeIf { name -> name.length in 3..50 }
        }
    }

    private fun extractIngredients(text: String): List<String> {
        val lower = text.lowercase()

        // Find composition section
        val compositionStart = listOf("composition:", "active ingredients:", "contains:", "komposisi:", "tiap tablet:")
            .mapNotNull { lower.indexOf(it).takeIf { idx -> idx >= 0 } }
            .minOrNull() ?: return emptyList()

        val compositionEnd = listOf("indication:", "dosage:", "directions:", "indikasi:", "dosis:")
            .mapNotNull { lower.indexOf(it, compositionStart).takeIf { idx -> idx >= 0 } }
            .minOrNull() ?: (compositionStart + 200)

        val compositionText = text.substring(compositionStart, compositionEnd.coerceAtMost(text.length))

        // Split by common delimiters
        return compositionText
            .split(",", ";", "\n")
            .map { it.trim().replace(Regex("""[\d.,]+\s*(mg|ml|g|mcg)"""), "").trim() }
            .filter { it.length in 3..30 }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HEALTH CONSULTATION (LLM with DISCLAIMER)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Provide health information with MANDATORY disclaimer.
     * LLM EXPLAINS, but NEVER diagnoses.
     * Hardened for OFFLINE REASONING using local offline drug database.
     */
    fun buildConsultationPrompt(query: String): String {
        val allergies = userProfile.allergies.joinToString(", ").ifEmpty { getNoDataText() }
        val conditions = userProfile.conditions.joinToString(", ").ifEmpty { getNoDataText() }
        val medications = userProfile.currentMedications.joinToString(", ").ifEmpty { getNoDataText() }

        // Offline Reasoning Injection: Fetch local drug recommendations based on query
        val localRecommendations = drugDatabase.getRecommendationsForIllness(query)
        val contextData = if (localRecommendations.isNotEmpty()) {
            val recText = localRecommendations.joinToString("\n") {
                "- ${it.genericName} (${it.brandNames.joinToString()}): ${it.usage}"
            }
            "\n[OFFLINE LOCAL DRUG DATABASE MATCHES]\nBased on the user's query, these are standard over-the-counter options available locally:\n$recText\nUse these local options as context when explaining standard treatments.\n"
        } else {
            ""
        }

        val basePrompt = PromptLocalizer.getMedicalConsultantPrompt(
            context = context,
            query = query,
            allergies = allergies,
            conditions = conditions,
            medications = medications
        )

        return basePrompt + contextData
    }

    private fun getNoDataText(): String {
        return "None"
    }

    fun cleanup() {
        tts?.shutdown()
    }
}
