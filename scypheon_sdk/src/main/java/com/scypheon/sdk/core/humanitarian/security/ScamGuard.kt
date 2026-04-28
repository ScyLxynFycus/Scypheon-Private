package com.scypheon.sdk.core.humanitarian.security

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Ultimate Killer Feature: Offline Voice Phishing (Scam) Protector for the Elderly.
 * Constantly analyzes incoming conversation transcripts (from Speech-to-Text) during a phone call.
 * Uses Gemma 4 / 3n to detect psychological manipulation, urgency, financial threats, or "family emergency" scams.
 */
class ScamGuard(
    private val context: Context,
    private val gateway: NeuralGateway,
    private val onScamDetected: (String) -> Unit
) {

    // Threshold to prevent overwhelming the LLM with every single word
    private var conversationBuffer = StringBuilder()
    private val BUFFER_THRESHOLD_WORDS = 8

    // Prevent spamming the alert
    private var isAlertActive = false

    fun processConversationTranscript(text: String) {
        if (isAlertActive) return // Already warned the user, don't spam

        conversationBuffer.append(text).append(" ")

        val words = conversationBuffer.toString().split("\\s+".toRegex())
        if (words.size >= BUFFER_THRESHOLD_WORDS) {
            val transcriptToAnalyze = conversationBuffer.toString()
            // Rather than totally clearing the buffer, keep the last few words to maintain context
            // across buffer boundaries
            val overlapWords = words.takeLast(3).joinToString(" ")
            conversationBuffer.clear()
            conversationBuffer.append(overlapWords).append(" ")
            analyzeTranscript(transcriptToAnalyze)
        }
    }

    // Independent scope to survive caller cancellation
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun analyzeTranscript(transcript: String) {
        // Fast-fail keyword check to save battery before waking up Gemma
        val scamKeywords = listOf("transfer", "uang", "kecelakaan", "polisi", "atm", "diblokir", "otp", "pin", "undian", "hadiah", "segera", "urgent", "password", "bank", "kredit")
        val lowerTranscript = transcript.lowercase()
        val hasSuspiciousKeyword = scamKeywords.any { lowerTranscript.contains(it) }

        if (!hasSuspiciousKeyword) {
            Timber.d("🛡️ ScamGuard: Transcript looks safe, ignoring LLM check.")
            return
        }

        scope.launch {
            val prompt = """
                You are a strict cybersecurity AI protecting an elderly person from phone scams.
                Analyze this transcript from a phone call: "$transcript"

                Does this sound like a scam? Look for psychological manipulation, false urgency, asking for money, OTPs, or claiming a family member is in jail/hospital.

                If it is a scam, reply ONLY with "DANGER:" followed by a short explanation of the trick.
                If it is safe, reply ONLY with "SAFE".
            """.trimIndent()

            Timber.i("🛡️ ScamGuard: Suspicious keywords found. Asking Gemma for reasoning...")
            val response = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }
            Timber.i("🛡️ ScamGuard Response: $response")

            if (response.startsWith("DANGER", ignoreCase = true)) {
                isAlertActive = true
                vibrateDanger()
                onScamDetected(response.replace("DANGER:", "").trim())

                // Reset alert after a long cooldown
                delay(15000)
                isAlertActive = false
            }
        }
    }

    private fun vibrateDanger() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Extremely aggressive long vibration for scam alert
                val timings = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000), -1)
            }
        } catch (e: Exception) {
            Timber.e("Failed to vibrate device: ${e.message}")
        }
    }
}
