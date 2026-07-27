package com.scypheon.sdk.core.humanitarian.medical

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import android.speech.tts.UtteranceProgressListener
import com.scypheon.sdk.core.memory.DualMemoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Killer Feature: Offline Medicine Guard
 * Uses Google ML Kit OCR to read blurry/small text on medicine bottles or prescriptions offline.
 * Feeds the raw OCR output into Gemma to extract precise dosage, warnings, and cross-check against
 * user allergies. Finally, outputs the safety report via TTS for the elderly or visually impaired.
 */
@Singleton
class OfflineMedicineGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmEngine: LiteRtEliteEngine,
    private val memoryManager: DualMemoryManager,
    private val interactionChecker: DrugInteractionChecker,
    private val dao: PharmacopeiaDao,
    private val clinicalValidator: ClinicalValidator
) : TextToSpeech.OnInitListener {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var tts: TextToSpeech? = null

    // Prevent spamming TTS
    private var isProcessing = false
    private var lastScannedText = ""

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.getDefault())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    // Re-enable scanning once speaking is completely finished
                    if (utteranceId == "MedicineGuard") {
                        isProcessing = false
                    }
                }
                override fun onError(utteranceId: String?) {
                    isProcessing = false
                }
            })
        }
    }

    /**
     * Call this when the user points the camera at a medicine bottle.
     */
    fun processFrame(bitmap: Bitmap, rotationDegrees: Int) {
        if (isProcessing) return

        val image = InputImage.fromBitmap(bitmap, rotationDegrees)

        isProcessing = true
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val scannedText = visionText.text.trim()

                // Only trigger Gemma if we found substantial new text
                if (scannedText.length > 10 && scannedText != lastScannedText) {
                    lastScannedText = scannedText
                    analyzeMedicineWithGemma(scannedText)
                } else {
                    isProcessing = false
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "ML Kit OCR failed")
                isProcessing = false
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun analyzeMedicineWithGemma(rawOcrText: String) {
        Timber.i("💊 Raw Medicine OCR: $rawOcrText")

        // Dynamic Entity Resolution via local Pharmacopeia
        scope.launch {
            val userAllergies = memoryManager.getUserAllergies()
            Timber.i("💊 Active Patient Allergies fetched from DB: $userAllergies")

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

            // 1. Programmatic Allergy Cross-Check (Bypasses LLM entirely)
            val userAllergiesList = clinicalValidator.parseAllergies(userAllergies)
            if (resolvedIds.isNotEmpty()) {
                val drug = dao.getDrugById(resolvedIds.first())
                if (drug != null) {
                    val checkResult = clinicalValidator.checkAllergyInteraction(drug.drugName, userAllergiesList)
                    val genericCheckResult = drug.genericName?.let { clinicalValidator.checkAllergyInteraction(it, userAllergiesList) } ?: AllergyCheckResult.Safe
                    
                    val unsafeResult = when {
                        checkResult is AllergyCheckResult.Unsafe -> checkResult
                        genericCheckResult is AllergyCheckResult.Unsafe -> genericCheckResult
                        else -> null
                    }
                    
                    if (unsafeResult != null) {
                        Timber.e("[OfflineMedicineGuard] LETHAL ALLERGY BLOCKED: ${drug.drugName} vs ${unsafeResult.allergen}")
                        val allergyWarning = "⚠️ CRITICAL LETHAL ALLERGY WARNING ⚠️\n\n" +
                                "This medication contains or is related to '${unsafeResult.allergen}', which you are severely allergic to. " +
                                "DO NOT TAKE THIS MEDICATION. Seek immediate alternative treatments."
                        try {
                            tts?.speak(allergyWarning, TextToSpeech.QUEUE_FLUSH, null, "MedicineGuard")
                        } catch (e: Exception) {
                            isProcessing = false
                        }
                        return@launch
                    }
                }
            }

            val prompt = """
                You are a strict, highly accurate offline pharmacist AI.

                Raw text scanned from a medicine bottle/prescription: "$rawOcrText"

                Patient Allergies: $userAllergies

                Task:
                1. Identify the medicine name.
                2. Identify the dosage and instructions.
                3. CROSS-CHECK: Is this medicine dangerous given the patient's allergies?

                Output a short, verbal summary to be spoken aloud.
                Start with "DANGER" if it conflicts with allergies, otherwise start with "Medicine identified".
                Do not use markdown. Speak clearly.
            """.trimIndent()

            var interactionWarning = ""
            if (detectedDrug != null) {
                val prescriptions = memoryManager.getCurrentPrescriptions()
                val interaction = interactionChecker.checkInteraction(detectedDrug, prescriptions)
                if (interaction != null) {
                    interactionWarning = "CRITICAL ALARM: $interaction"
                }
            }

            // Generate response using Google MediaPipe Gemma Engine
            try {
                var aiResponse = llmEngine.generateResponse(prompt).reduce { acc, value -> acc + value }

                if (interactionWarning.isNotEmpty()) {
                     aiResponse = "$interactionWarning $aiResponse"
                }

                Timber.i("💊 Gemma Pharmacist Report: $aiResponse")
                tts?.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, "MedicineGuard")
            } catch (e: Exception) {
                Timber.e(e, "Error during Gemma inference for Medicine Guard")
                isProcessing = false
            }
        }
    }

    fun shutdown() {
        textRecognizer.close()
        tts?.stop()
        tts?.shutdown()
    }
}
