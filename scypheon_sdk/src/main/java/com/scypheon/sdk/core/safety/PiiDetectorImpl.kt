package com.scypheon.sdk.core.safety

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface contract for PII detection and redaction.
 */
interface PiiDetector {
    fun containsPii(text: String): Boolean
    fun redactPii(text: String): String
}

/**
 * Deterministic PII detection using regex patterns.
 * Zero ML dependency for edge/offline compatibility.
 * Enterprise Grade: Active Redaction Pipeline.
 */
@Singleton
class PiiDetectorImpl @Inject constructor() : PiiDetector {
    private val PII_PATTERNS = listOf(
        Regex("\\b\\d{16}\\b"), // NIK / Credit Card
        Regex("\\b(?:\\+62|0)\\d{9,12}\\b"), // Indonesian Phone
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), // Email
        Regex("\\b(?:Jl\\.?|Jalan|Gg\\.?|Gang)\\s+[A-Za-z0-9\\s\\-]+(?:No\\.?\\s*\\d+)?\\b", RegexOption.IGNORE_CASE), // Address
        Regex("(?:(?:rekening|rek\\.?|account|acct|transfer|tabungan|giro|no\\.?\\s*rek)\\s*[:\\-]?\\s*)(\\d{10,16})", RegexOption.IGNORE_CASE), // Bank account context
        Regex("\\bMRN[-\\s]?\\d{6,}\\b", RegexOption.IGNORE_CASE) // Medical record number
    )

    override fun containsPii(text: String): Boolean {
        return PII_PATTERNS.any { it.containsMatchIn(text) }
    }

    override fun redactPii(text: String): String {
        var redactedText = text
        for (pattern in PII_PATTERNS) {
            redactedText = pattern.replace(redactedText, "[REDACTED]")
        }
        return redactedText
    }
}
