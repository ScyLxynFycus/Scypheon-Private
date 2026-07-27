package com.scypheon.sdk.core.safety

import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.telemetry.TelemetryDao
import com.scypheon.sdk.core.telemetry.TelemetryEvent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@SafetyCritical
@Singleton
class InputSafetyFilter @Inject constructor(
    private val telemetry: TelemetryDao
) {
    data class SafetyDecision(
        val isSafe: Boolean,
        val riskScore: Double,
        val blockedReason: String?,
        val suggestedRephrase: String? = null
    )

    data class SafetyRule(
        val name: String,
        val patterns: List<String>,
        val weight: Double,
        val layer: Int, // 1: Static, 2: Weighted, 3: Heuristic
        val useRegex: Boolean = false,
        val shadowMode: Boolean = false
    )

    private val rules = listOf(
        // Layer 1: Critical Gates (Fast Fail)
        SafetyRule(
            "INJECTION_CRITICAL",
            listOf(
                "(ignore|bypass|reset|override|delete|abaikan|lupakan)\\s+(all|previous|system|semua|instruksi|perintah)",
                "you\\s+are\\s+now\\s+a\\s+(hacker|god|villain|unrestricted|lepas)",
                "access\\s+root\\s+shell",
                "jailbreak",
                "dan\\s+abaikan\\s+perintah"
            ),
            1.0, 1, useRegex = true
        ),
        
        // Layer 2: Domain Risk (Weighted)
        SafetyRule("SENSITIVE_PHARMA", listOf("lethal dose", "synthesize poison", "illegal drug", "manufacture bomb", "buat racun", "dosis mematikan"), 0.8, 2),
        SafetyRule("SENSITIVE_MEDICAL_ADVICE", listOf("stop taking medication", "don't see a doctor", "berhenti minum obat", "jangan ke dokter"), 0.7, 2),
        
        SafetyRule("ROLEPLAY_FRAMING", listOf("pretend you are", "act as", "you are now"), 0.55, 3),
        // Layer 3: Obfuscation Protection
        SafetyRule(
            "OBFUSCATED_ATTACK",
            listOf(
                "i[._\\s]g[._\\s]n[._\\s]o[._\\s]r[._\\s]e",
                "a[._\\s]b[._\\s]a[._\\s]i[._\\s]k[._\\s]a[._\\s]n"
            ),
            1.0, 3, useRegex = true
        )
    )

    /**
     * Enterprise-grade safety evaluator with multi-layer heuristic analysis.
     */
    suspend fun evaluate(input: String): SafetyDecision {
        // 1. ANOMALY DETECTION (Morse, Base64, Low-Entropy Attacks)
        if (detectStructuralAnomaly(input)) {
            Timber.e("🚨 [SAFETY ANOMALY] Obfuscated injection attempt detected.")
            auditViolation(input, 1.0, "STRUCTURAL_ANOMALY")
            return SafetyDecision(
                isSafe = false, 
                riskScore = 1.0, 
                blockedReason = "Anomalous message structure detected. Please use natural language."
            )
        }

        val normalizedInput = input.lowercase().trim()
        val deobfuscatedInput = normalizedInput.replace(Regex("[\\s._-]"), "")
        
        var totalRisk = 0.0
        val matchedRules = mutableListOf<String>()

        for (rule in rules) {
            val isMatch = if (rule.useRegex) {
                rule.patterns.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(normalizedInput) } ||
                rule.patterns.any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(deobfuscatedInput) }
            } else {
                rule.patterns.any { normalizedInput.contains(it) } ||
                rule.patterns.any { deobfuscatedInput.contains(it.replace(" ", "")) }
            }

            if (isMatch) {
                if (rule.shadowMode) {
                    Timber.d("🔍 [SAFETY SHADOW] Match: ${rule.name}")
                    auditViolation(input, rule.weight, "${rule.name} (SHADOW)")
                    continue
                }

                if (rule.layer == 1) {
                    Timber.w("🚫 [SAFETY GATE L1] CRITICAL BLOCKED | Rule: ${rule.name}")
                    auditViolation(input, 1.0, rule.name)
                    return SafetyDecision(false, 1.0, "Security violation: ${rule.name}")
                }

                totalRisk += rule.weight
                matchedRules.add(rule.name)
            }
        }

        val riskScore = totalRisk.coerceAtMost(1.0)
        val isSafe = riskScore < 0.6 // Slightly higher threshold for weighted rules

        if (!isSafe) {
            Timber.w("🚫 [SAFETY GATE FINAL] BLOCKED | Score: $riskScore | Rules: $matchedRules")
            auditViolation(input, riskScore, matchedRules.joinToString(","))
        }

        return SafetyDecision(
            isSafe = isSafe,
            riskScore = riskScore,
            blockedReason = if (!isSafe) "Aggregated risk threshold exceeded: $matchedRules" else null,
            suggestedRephrase = if (!isSafe) "Please rephrase your query to focus on humanitarian or medical assistance." else null
        )
    }

    private fun detectStructuralAnomaly(input: String): Boolean {
        if (input.length < 20) return false
        
        // A. Morse Detection: High ratio of . and - with frequent spaces
        val morseChars = input.count { it == '.' || it == '-' || it == ' ' }
        if (morseChars.toFloat() / input.length > 0.9f && input.contains(".") && input.contains("-")) {
            return true
        }

        // B. Base64 Detection: High character diversity but restricted to B64 charset + padding
        val b64Pattern = Regex("^[A-Za-z0-9+/]+={0,2}$")
        if (input.length > 32 && b64Pattern.matches(input.replace("\\s".toRegex(), ""))) {
            // Check if it's actually natural language or just high entropy
            val distinctChars = input.filter { it.isLetterOrDigit() }.toSet().size
            if (distinctChars > 15) return true 
        }

        // C. Low Diversity Anomaly (Binary or Repetitive symbols)
        val distinct = input.replace("\\s".toRegex(), "").toSet().size
        if (input.length > 40 && distinct <= 4) return true

        return false
    }

    private suspend fun auditViolation(input: String, score: Double, reason: String) {
        telemetry.insert(TelemetryEvent(
            eventId = UUID.randomUUID().toString(),
            type = "SAFETY_VIOLATION",
            payload = "{\"score\":$score,\"reason\":\"$reason\",\"input_preview\":\"${input.take(50)}\"}",
            timestamp = System.currentTimeMillis(),
            synced = false
        ))
    }

    suspend fun isSafe(input: String): Boolean = evaluate(input).isSafe
}



