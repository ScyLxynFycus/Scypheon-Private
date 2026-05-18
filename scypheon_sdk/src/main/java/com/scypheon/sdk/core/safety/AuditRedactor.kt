package com.scypheon.sdk.core.safety

/**
 * Enterprise PII Redaction Interceptor.
 * Ensures that sensitive patient data (SSN, Email, Phone, IDs) is never stored in cleartext 
 * within the audit trails, maintaining HIPAA/GDPR compliance while preserving explainability.
 */
object AuditRedactor {
    private val PII_PATTERNS = listOf(
        Regex("""\b\d{3}[-.]?\d{2}[-.]?\d{4}\b"""), // SSN
        Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"""), // Email
        Regex("""\b\d{10,15}\b"""), // Phone
        Regex("""\b(?:MRN|ID|PATIENT)\s*[:=]?\s*[A-Z0-9-]+\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Sanitizes the input string by replacing identified PII patterns with [REDACTED].
     */
    fun sanitize(input: String): String {
        var sanitized = input
        PII_PATTERNS.forEach { pattern ->
            sanitized = pattern.replace(sanitized, "[REDACTED]")
        }
        return sanitized
    }
}
