package com.scypheon.sdk.core.safety.helios

/**
 * XAI-ready violation report for transparent safety decisions.
 * Consumed by MedicalExplainabilityEngine and audit trail.
 */
data class SafetyViolationReport(
    val violationId: String,
    val timestamp: Long,
    val severity: ViolationSeverity,
    val category: ViolationCategory,
    val policyReference: String,
    val description: String,
    val evidence: List<String>,
    val userFriendlyMessage: String,
    val remediationAdvice: String?
)

enum class ViolationSeverity { LOW, MEDIUM, HIGH, CRITICAL }

enum class ViolationCategory {
    MALICIOUS_INJECTION,
    UNVERIFIED_MEDICAL_ADVICE,
    PII_EXPOSURE,
    DANGEROUS_CONTENT,
    ROLEPLAY_JAILBREAK,
    PROMPT_LEAKAGE,
    ADVERSARIAL_FRAMING,
    DELIMITER_POISONING,
    ORIGA_DEGRADATION
}

/**
 * Helper to generate human-readable violation messages.
 */
object ViolationMessageGenerator {
    fun generate(report: SafetyViolationReport): String {
        return when (report.category) {
            ViolationCategory.PII_EXPOSURE ->
                "Sensitive data detected and redacted to protect user privacy. [Policy: ${report.policyReference}]"   
            ViolationCategory.MALICIOUS_INJECTION ->
                "Input contains patterns associated with prompt injection attempts. [Policy: ${report.policyReference}]"
            ViolationCategory.UNVERIFIED_MEDICAL_ADVICE ->
                "Medical claim could not be verified against trusted pharmacopeia. Consult a licensed professional. [Policy: ${report.policyReference}]"
            ViolationCategory.ROLEPLAY_JAILBREAK ->
                "Input attempts to bypass safety guidelines via roleplay framing. [Policy: ${report.policyReference}]"
            ViolationCategory.ADVERSARIAL_FRAMING ->
                "Input structure bypasses safety guidelines. [Policy: SECURITY-3.1]"
            ViolationCategory.DELIMITER_POISONING ->
                "Context delimiter abuse detected. [Policy: SECURITY-3.3]"
            ViolationCategory.ORIGA_DEGRADATION ->
                "Orchestrator graceful degradation. [Policy: RESILIENCE-1.0]"
            else -> report.description
        }
    }
}
