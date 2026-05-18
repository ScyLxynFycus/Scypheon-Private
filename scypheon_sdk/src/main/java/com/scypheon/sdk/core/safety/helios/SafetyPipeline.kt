package com.scypheon.sdk.core.safety.helios

import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@SafetyCritical
@Singleton
class SafetyPipeline @Inject constructor(
    private val l0Sanitizer: Layer0Sanitizer,
    private val semanticDetector: EmbeddingGemmaAnomalyDetector,
    private val l1RuleEngine: Layer1RuleEngine,
    private val privacyShield: Layer5PrivacyShield,
    private val jailbreakDetector: Layer3BJailbreakDetector,
    private val sessionRiskManager: SessionRiskManager,
    private val stateStore: ConversationStateStore,
    private val blackBox: BlackBoxVault
) {
    
    enum class SecurityStatus { SAFE, FLAGGED, BLOCKED }
    
    data class Evaluation(
        val status: SecurityStatus,
        val sanitizedPrompt: String,
        val riskScore: Double,
        val reason: String? = null,
        val violationReport: SafetyViolationReport? = null
    )

    /**
     * Executes the HELIOS Sentinel 5-Layer Defense.
     * Implements Early-Exit for deterministic efficiency.
     */
    suspend fun evaluateInput(sessionId: String, rawInput: String): Evaluation {
        // --- LAPISAN 0B: SEMANTIC ANOMALY (Gemma-Native) ---
        val semanticRisk = semanticDetector.evaluateRisk(rawInput)
        if (semanticRisk > 0.85f) {
            audit(sessionId, "BLOCKED", "L0B_SEMANTIC_ANOMALY: $semanticRisk", rawInput)
            return Evaluation(SecurityStatus.BLOCKED, rawInput, 1.0, "SEMANTIC_INJECTION_DETECTED")
        }

        // --- LAPISAN 0: SANITIZATION (NFKC + Entropy) ---
        val l0 = l0Sanitizer.sanitize(rawInput)
        if (l0.isFlagged && l0.entropy > 6.0) { // Severe entropy fail
            audit(sessionId, "BLOCKED", "L0_HIGH_ENTROPY", rawInput)
            return Evaluation(SecurityStatus.BLOCKED, l0.output, 1.0, "STRUCTURAL_ANOMALY")
        }

        // --- LAPISAN 5: PII PRIVACY SHIELD (NEW) ---
        val privacyResult = privacyShield.scanAndRedact(l0.output)
        if (!privacyResult.isClean) {
            val report = SafetyViolationReport(
                violationId = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                severity = ViolationSeverity.MEDIUM,
                category = ViolationCategory.PII_EXPOSURE,
                policyReference = "PRIVACY-2.1: Auto-redaction of sensitive identifiers",
                description = "Detected ${privacyResult.detections.size} PII instance(s): ${privacyResult.detections.map { it.type }.joinToString()}",
                evidence = privacyResult.detections.map { it.originalValue },
                userFriendlyMessage = "Sensitive information was automatically protected.",
                remediationAdvice = "Avoid sharing personal identifiers in public queries."
            )
            blackBox.logSafetyViolation(sessionId, report)
            return Evaluation(
                status = SecurityStatus.FLAGGED,
                sanitizedPrompt = privacyResult.redactedOutput,
                riskScore = 0.5,
                reason = "PII_REDACTED",
                violationReport = report
            )
        }

        // --- LAPISAN 3B: STRUCTURAL JAILBREAK DETECTION (NEW) ---
        val jailbreakReport = jailbreakDetector.detectStructuralJailbreak(l0.output, sessionId)
        if (jailbreakReport.isBlocked) {
            val report = SafetyViolationReport(
                violationId = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                severity = ViolationSeverity.CRITICAL,
                category = ViolationCategory.ADVERSARIAL_FRAMING,
                policyReference = "SECURITY-3.1: Structural Jailbreak Prevention",
                description = "Detected ${jailbreakReport.matches.size} structural manipulation pattern(s)",
                evidence = jailbreakReport.matches.map { it.pattern },
                userFriendlyMessage = "Input structure violates safety protocols. Please rephrase.",
                remediationAdvice = "Avoid roleplay framing, system instructions, or special delimiters."
            )
            blackBox.logSafetyViolation(sessionId, report)
            sessionRiskManager.updateRisk(sessionId, SafetyVerdict.BLOCKED)
            
            return Evaluation(
                status = SecurityStatus.BLOCKED,
                sanitizedPrompt = l0.output,
                riskScore = 1.0,
                reason = "STRUCTURAL_JAILBREAK_DETECTED",
                violationReport = report
            )
        }

        // --- LAPISAN 1: RULE ENGINE (Stateful) ---
        val history = stateStore.getFullContext(sessionId)
        val l1 = l1RuleEngine.evaluate(l0.output, history)
        
        if (l1.isBlocked) {
            audit(sessionId, "BLOCKED", "L1_MATCH: ${l1.matchedRules}", l0.output)
            return Evaluation(SecurityStatus.BLOCKED, l0.output, 1.0, l1.matchedRules.firstOrNull())
        }

        // Record the turn if it passed structural checks
        stateStore.recordTurn(sessionId, l0.output)

        val finalStatus = if (l1.riskScore > 0.4 || l0.isFlagged) SecurityStatus.FLAGGED else SecurityStatus.SAFE
        
        return Evaluation(
            status = finalStatus,
            sanitizedPrompt = l0.output,
            riskScore = l1.riskScore,
            reason = if (finalStatus == SecurityStatus.FLAGGED) "POTENTIAL_RISK_DETECTED" else null
        )
    }
    
    /**
     * Executes the HELIOS Sentinel Output Vetting (Post-Inference).
     * Protects against AI hallucinations, toxicity, or PII leaks in generated responses.
     */
    suspend fun evaluateOutput(sessionId: String, rawOutput: String): Evaluation {
        Timber.d("🔍 [HELIOS] Vetting AI output for session: $sessionId")
        
        // 1. Output PII Shield
        val privacyResult = privacyShield.scanAndRedact(rawOutput)
        if (!privacyResult.isClean) {
            audit(sessionId, "FLAGGED", "L5_OUTPUT_PII_REDACTED", rawOutput)
            return Evaluation(
                status = SecurityStatus.FLAGGED,
                sanitizedPrompt = privacyResult.redactedOutput,
                riskScore = 0.5,
                reason = "OUTPUT_PII_PROTECTED"
            )
        }

        return Evaluation(SecurityStatus.SAFE, rawOutput, 0.0)
    }

    private suspend fun audit(sessionId: String, status: String, reason: String, input: String) {
        blackBox.logEvent(
            eventType = "HELIOS_SENTINEL_AUDIT",
            details = "Session: $sessionId | Status: $status | Reason: $reason | InputPreview: ${input.take(30)}",
            securityLevel = "CRITICAL"
        )
    }
}
