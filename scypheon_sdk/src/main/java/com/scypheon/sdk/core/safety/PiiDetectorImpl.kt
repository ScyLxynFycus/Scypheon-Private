package com.scypheon.sdk.core.safety

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface contract for PII detection.
 */
interface PiiDetector {
    fun containsPii(text: String): Boolean
}

/**
 * Deterministic PII detection using regex patterns.
 * Zero ML dependency for edge/offline compatibility.
 */
@Singleton
class PiiDetectorImpl @Inject constructor() : PiiDetector {
    companion object {
        private val PII_PATTERNS = listOf(
            Regex("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"), // Phone
            Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), // Email
            Regex("\\b\\d{16}\\b"), // Credit card
            Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"), // SSN/NIK
            Regex("\\b(Jl\\.?|Jalan|No\\.?)\\s+[A-Za-z0-9\\s]+\\b") // Address fragment
        )
    }

    override fun containsPii(text: String): Boolean {
        return PII_PATTERNS.any { it.containsMatchIn(text) }
    }
}
