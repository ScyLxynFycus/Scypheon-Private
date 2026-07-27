package com.scypheon.sdk.core.safety.helios

import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.CircuitBreakerOpenException
import kotlinx.coroutines.withTimeoutOrNull
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
    private val blackBox: BlackBoxVault,
    private val l4LeakDetector: Layer4LeakDetector,
    private val circuitBreaker: ResilienceCircuitBreaker
) {
    
    enum class SecurityStatus { SAFE, FLAGGED, BLOCKED }
    
    data class Evaluation(
        val status: SecurityStatus,
        val sanitizedPrompt: String,
        val riskScore: Double,
        val reason: String? = null,
        val violationReport: SafetyViolationReport? = null
    )

    private val LAYER_TIMEOUTS = mapOf(
        "L0_SANITIZATION" to 100L,
        "L3B_JAILBREAK" to 150L,
        "L1_RULE_ENGINE" to 100L,
        "L0D_DECODED_VETTING" to 200L,
        "L5_PII" to 200L,
        "L0B_SEMANTIC" to 1000L
    )

    /**
     * Protected Layer Executor. Returns null if layer fails or times out,
     * allowing graceful degradation of the pipeline.
     */
    private suspend fun <T> runLayerSafely(layerName: String, block: suspend () -> T): T? {
        val timeout = LAYER_TIMEOUTS[layerName] ?: 500L
        return try {
            circuitBreaker.execute("HELIOS_$layerName") {
                withTimeoutOrNull(timeout) {
                    block()
                } ?: run {
                    Timber.w("⏳ [HELIOS] Timeout in layer $layerName")
                    throw Exception("Timeout in $layerName") // Throw to open circuit
                }
            }
        } catch (e: CircuitBreakerOpenException) {
            Timber.w("🛑 [HELIOS] Circuit Breaker OPEN for $layerName. Skipping layer.")
            null
        } catch (e: Exception) {
            Timber.e(e, "💥 [HELIOS] Failure in layer $layerName. Graceful degradation applied.")
            null
        }
    }

    /**
     * Executes the HELIOS Sentinel 5-Layer Defense.
     * Implements Early-Exit for deterministic efficiency.
     * CRITICAL: STRICT FAIL-SECURE DOCTRINE (When in doubt, BLOCK)
     */
    suspend fun evaluateInput(sessionId: String, rawInput: String): Evaluation {
        
        // --- LAYER 0: SANITIZATION (Cheap, <2ms) ---
        val l0 = runLayerSafely("L0_SANITIZATION") {
            l0Sanitizer.sanitize(rawInput)
        } ?: run {
            Timber.w("🚨 [HELIOS] L0 Sanitization failed or timed out - FAIL-SECURE: blocking input")
            return Evaluation(SecurityStatus.BLOCKED, "", 1.0, "L0_SANITIZATION_FAILURE")
        }
        
        val entropy = calculateShannonEntropy(rawInput)
        
        val isBase64Like = entropy in 4.3f..5.5f && Regex("^[A-Za-z0-9+/=]+\$").matches(rawInput.replace("\\s+".toRegex(), ""))
        val isEncryptedLike = entropy > 6.5f
        
        if (!l0.isSafe && (isEncryptedLike || (isBase64Like && rawInput.length > 50))) { 
            audit(sessionId, "BLOCKED", "L0_HIGH_ENTROPY", rawInput)
            return Evaluation(SecurityStatus.BLOCKED, l0.sanitizedInput, 1.0, "STRUCTURAL_ANOMALY")
        }

        // --- LAYER 3B: STRUCTURAL JAILBREAK DETECTION (Cheap, <5ms) ---
        val jailbreakReport = runLayerSafely("L3B_JAILBREAK") {
            jailbreakDetector.detectStructuralJailbreak(l0.sanitizedInput, sessionId)
        } ?: run {
            Timber.w("🚨 [HELIOS] L3B Jailbreak failed - FAIL-SECURE: blocking input")
            return Evaluation(SecurityStatus.BLOCKED, l0.sanitizedInput, 1.0, "L3B_JAILBREAK_FAILURE")
        }
        
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
                sanitizedPrompt = l0.sanitizedInput,
                riskScore = 1.0,
                reason = "STRUCTURAL_JAILBREAK_DETECTED",
                violationReport = report
            )
        }

        // --- LAYER 1: RULE ENGINE (Stateful, <2ms) ---
        val history = stateStore.getFullContext(sessionId)
        val l1 = runLayerSafely("L1_RULE_ENGINE") {
            l1RuleEngine.evaluate(l0.sanitizedInput, history, sessionId)
        } ?: run {
            Timber.w("🚨 [HELIOS] L1 Rule Engine failed - FAIL-SECURE: blocking input")
            return Evaluation(SecurityStatus.BLOCKED, l0.sanitizedInput, 1.0, "L1_ENGINE_FAILURE")
        }
        
        if (l1.isBlocked) {
            audit(sessionId, "BLOCKED", "L1_MATCH: ${l1.matchedRules}", l0.sanitizedInput)
            return Evaluation(SecurityStatus.BLOCKED, l0.sanitizedInput, 1.0, l1.matchedRules.firstOrNull())
        }

        // --- LAYER 0D: DECODED PAYLOAD VETTING (Parallel decoders, ~10ms) ---
        val l0dFailed = runLayerSafely("L0D_DECODED_VETTING") {
            for (decodedText in l0.decodedPlaintexts) {
                val decodedJailbreak = jailbreakDetector.detectStructuralJailbreak(decodedText, sessionId)
                if (decodedJailbreak.isBlocked) return@runLayerSafely true

                val decodedL1 = l1RuleEngine.evaluate(decodedText, history, sessionId)
                if (decodedL1.isBlocked) return@runLayerSafely true
            }
            false
        } ?: true // FAIL-SECURE: Treat timeout/failure as a smuggled payload detection

        if (l0dFailed) {
            Timber.w("🚨 [HELIOS] L0D Vetting failed or detected threat - FAIL-SECURE: blocking input")
            return Evaluation(SecurityStatus.BLOCKED, l0.sanitizedInput, 1.0, "SMUGGLED_PAYLOAD_DETECTED_OR_L0D_FAILURE")
        }

        // --- LAYER 5: PII PRIVACY SHIELD (~10ms) ---
        val privacyResult = runLayerSafely("L5_PII") {
            privacyShield.scanAndRedact(l0.sanitizedInput)
        } ?: run {
            Timber.w("🚨 [HELIOS] L5 PII Shield failed - FAIL-SECURE: flag as PII exposure risk")
            return Evaluation(
                status = SecurityStatus.BLOCKED,
                sanitizedPrompt = "[REDACTED_DUE_TO_ENGINE_FAILURE]",
                riskScore = 1.0,
                reason = "L5_FAILURE"
            )
        }

        if (!privacyResult.isClean) {
            val report = SafetyViolationReport(
                violationId = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                severity = ViolationSeverity.MEDIUM,
                category = ViolationCategory.PII_EXPOSURE,
                policyReference = "PRIVACY-2.1: Context-Aware Redaction",
                description = "Detected PII exposure",
                evidence = privacyResult.detections.map { it.originalValue },
                userFriendlyMessage = "Sensitive information was detected.",
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

        // --- LAYER 0B: SEMANTIC ANOMALY (Expensive, ~100ms) ---
        val semanticRisk = runLayerSafely("L0B_SEMANTIC") {
            semanticDetector.evaluateRisk(privacyResult.redactedOutput)
        } ?: run {
            Timber.w("🚨 [HELIOS] L0B Semantic Engine failed - FAIL-SECURE: blocking input")
            return Evaluation(SecurityStatus.BLOCKED, privacyResult.redactedOutput, 1.0, "L0B_SEMANTIC_FAILURE")
        }
        
        if (semanticRisk > 0.85f) {
            audit(sessionId, "BLOCKED", "L0B_SEMANTIC_ANOMALY: $semanticRisk", rawInput)
            return Evaluation(SecurityStatus.BLOCKED, rawInput, 1.0, "SEMANTIC_INJECTION_DETECTED")
        }

        // Record the turn if it passed structural checks
        stateStore.recordTurn(sessionId, privacyResult.redactedOutput)

        val finalStatus = if (l1.riskScore > 0.4 || !l0.isSafe) SecurityStatus.FLAGGED else SecurityStatus.SAFE
        
        return Evaluation(
            status = finalStatus,
            sanitizedPrompt = privacyResult.redactedOutput,
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
        
        // 0. Canary Token System Leak Check (Multi-Canary Fix)
        if (CanaryTokenGenerator.detectLeak(sessionId, rawOutput)) {
            val report = SafetyViolationReport(
                violationId = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                severity = ViolationSeverity.CRITICAL,
                category = ViolationCategory.ADVERSARIAL_FRAMING,
                policyReference = "SECURITY-4.1: System Prompt Leak Prevention",
                description = "LLM output exposed the hidden Canary Token, indicating a system prompt leak or successful jailbreak.",
                evidence = listOf("CANARY_TOKEN_MATCH"),
                userFriendlyMessage = "System encountered a critical security constraint violation.",
                remediationAdvice = "Terminate session immediately."
            )
            blackBox.logSafetyViolation(sessionId, report)
            sessionRiskManager.updateRisk(sessionId, SafetyVerdict.BLOCKED)
            audit(sessionId, "BLOCKED", "L4_CANARY_TOKEN_LEAK", rawOutput)
            
            return Evaluation(
                status = SecurityStatus.BLOCKED,
                sanitizedPrompt = "",
                riskScore = 1.0,
                reason = "SYSTEM_PROMPT_LEAK_DETECTED",
                violationReport = report
            )
        }

        // 0B. Layer 4 Leak & Alignment Check
        val l4Result = l4LeakDetector.evaluateOutput(rawOutput)
        if (l4Result.isLeakedOrUnsafe) {
            val report = SafetyViolationReport(
                violationId = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                severity = ViolationSeverity.CRITICAL,
                category = ViolationCategory.ADVERSARIAL_FRAMING,
                policyReference = "SECURITY-4.2: Post-Inference Vetting Protocol",
                description = l4Result.reason ?: "Unsafe pattern detected in AI response.",
                evidence = listOf("L4_LEAK_DETECTOR_MATCH"),
                userFriendlyMessage = "System encountered a security restriction violation on generated response.",
                remediationAdvice = "Avoid inputs triggering prompt extraction or system commands."
            )
            blackBox.logSafetyViolation(sessionId, report)
            sessionRiskManager.updateRisk(sessionId, SafetyVerdict.BLOCKED)
            audit(sessionId, "BLOCKED", l4Result.reason ?: "L4_LEAK_DETECTOR", rawOutput)

            return Evaluation(
                status = SecurityStatus.BLOCKED,
                sanitizedPrompt = "",
                riskScore = 1.0,
                reason = l4Result.reason,
                violationReport = report
            )
        }
        
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

    private fun calculateShannonEntropy(input: String): Float {
        if (input.isEmpty()) return 0.0f
        
        val charCounts = mutableMapOf<Char, Int>()
        for (char in input) {
            charCounts[char] = charCounts.getOrDefault(char, 0) + 1
        }
        
        val length = input.length.toFloat()
        var entropy = 0.0
        
        for (count in charCounts.values) {
            val probability = count / length
            if (probability > 0) {
                entropy -= probability * kotlin.math.log2(probability.toDouble())
            }
        }
        
        return entropy.toFloat()
    }
}
