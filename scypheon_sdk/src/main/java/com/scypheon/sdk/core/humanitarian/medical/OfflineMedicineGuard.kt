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

/**
 * Killer Feature: Offline Medicine Guard
 * Uses Google ML Kit OCR to read blurry/small text on medicine bottles or prescriptions offline.
 * Feeds the raw OCR output into Gemma to extract precise dosage, warnings, and cross-check against
 * user allergies. Finally, outputs the safety report via TTS for the elderly or visually impaired.
 */
class OfflineMedicineGuard(
    private val context: Context,
    private val llmEngine: LiteRtEliteEngine,
    private val memoryManager: DualMemoryManager
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
                val scannedText = visionText.text.replace("\n", " ").trim()

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

        val userAllergies = memoryManager.getUserAllergies()
        Timber.i("💊 Active Patient Allergies fetched from DB: $userAllergies")

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

        // Extract the likely drug name (In a real scenario, Gemma would extract this via structured JSON output,
        // but for simplicity we check if the raw text contains known drugs)
        val knownDrugs = listOf("aspirin", "ibuprofen", "warfarin", "simvastatin", "sildenafil")
        val detectedDrug = knownDrugs.find { rawOcrText.lowercase().contains(it) }

        var interactionWarning = ""
        if (detectedDrug != null) {
            // Hardcoding a mock 'current prescription' list for the hackathon demo
            val mockPrescriptions = listOf("ibuprofen", "vitamin c")
            val interaction = DrugInteractionChecker.checkInteraction(detectedDrug, mockPrescriptions)
            if (interaction != null) {
                interactionWarning = "CRITICAL ALARM: $interaction"
            }
        }

        // Generate response using Google MediaPipe Gemma Engine on a background thread to prevent UI freeze
        scope.launch {
            try {
                var aiResponse = llmEngine.generateResponse(prompt).reduce { acc, value -> acc + value }

                // Prepend hard pharmacological interactions if found, bypassing the LLM's hallucination potential
                if (interactionWarning.isNotEmpty()) {
                     aiResponse = "$interactionWarning $aiResponse"
                }

                Timber.i("💊 Gemma Pharmacist Report: $aiResponse")

                // Speak out loud (UtteranceProgressListener will reset isProcessing when done)
                tts?.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, "MedicineGuard")
            } catch (e: Exception) {
                Timber.e(e, "Error during Gemma inference for Medicine Guard")
                isProcessing = false // ensure we don't get stuck processing forever
            }
        }
    }

    fun shutdown() {
        textRecognizer.close()
        tts?.stop()
        tts?.shutdown()
    }
}
