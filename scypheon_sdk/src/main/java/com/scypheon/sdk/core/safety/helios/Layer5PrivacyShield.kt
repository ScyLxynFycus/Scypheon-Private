package com.scypheon.sdk.core.safety.helios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-grade PII detection and redaction for edge/offline environments.
 * Deterministic regex-based; zero ML dependency for resilience.
 */
@Singleton
class Layer5PrivacyShield @Inject constructor() {
    companion object {
        // Indonesian + Global PII patterns (deterministic, offline-safe)
        private val PII_PATTERNS = mapOf(
            "NIK_KTP" to Regex("\\b\\d{16}\\b"), // Indonesian ID
            "PHONE_ID" to Regex("\\b(?:\\+62|0)\\d{9,12}\\b"),
            "EMAIL" to Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            "ADDRESS_ID" to Regex("\\b(?:Jl\\.?|Jalan|Gg\\.?|Gang)\\s+[A-Za-z0-9\\s\\-]+(?:No\\.?\\s*\\d+)?\\b"),
            // [v1.5.3-SAR] HELIOS HARDENING: Context-aware bank account detection.
            // Old pattern \\b\\d{10,14}\\b matched ANY 10-14 digit number (timestamps, order IDs, etc.)
            // Now requires contextual prefix to reduce false positives.
            "BANK_ACCOUNT" to Regex("(?:(?:rekening|rek\\.?|account|acct|transfer|tabungan|giro|no\\.?\\s*rek)\\s*[:\\-]?\\s*)(\\d{10,16})", RegexOption.IGNORE_CASE),
            "MEDICAL_RECORD" to Regex("\\bMRN[-\\s]?\\d{6,}\\b") // Medical record number
        )

        private const val REDACTION_TOKEN = "[REDACTED_PII]"
    }

    /**
     * Scans input for PII and returns redacted text + detection report.
     * Thread-safe, coroutine-compatible, edge-optimized.
     */
    suspend fun scanAndRedact(input: String): PrivacyScanResult = withContext(Dispatchers.Default) {
        val detections = mutableListOf<PiiDetection>()

        PII_PATTERNS.forEach { (type, pattern) ->
            pattern.findAll(input).forEach { match ->
                detections.add(PiiDetection(
                    type = type,
                    originalValue = match.value,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    confidence = 1.0f // Deterministic match
                ))
            }
        }

        // Sort by startIndex ascending, then by length descending (endIndex descending) to resolve overlaps
        val sortedDetections = detections.sortedWith(
            compareBy<PiiDetection> { it.startIndex }
                .thenByDescending { it.endIndex }
        )

        val nonOverlappingDetections = mutableListOf<PiiDetection>()
        var lastEnd = 0
        for (det in sortedDetections) {
            if (det.startIndex >= lastEnd) {
                nonOverlappingDetections.add(det)
                lastEnd = det.endIndex
            }
        }

        // Sort descending by startIndex to apply replacements from right to left
        val finalDetections = nonOverlappingDetections.sortedByDescending { it.startIndex }

        var redacted = input
        for (det in finalDetections) {
            redacted = redacted.replaceRange(det.startIndex until det.endIndex, REDACTION_TOKEN)
        }

        PrivacyScanResult(
            originalInput = input,
            redactedOutput = redacted,
            detections = nonOverlappingDetections.sortedBy { it.startIndex },
            isClean = nonOverlappingDetections.isEmpty(),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Quick check for PII presence (no redaction) for pre-filtering.
     */
    fun containsPii(input: String): Boolean {
        return PII_PATTERNS.values.any { pattern -> pattern.containsMatchIn(input) }
    }
}

/**
 * Structured PII detection result for audit and XAI.
 */
data class PrivacyScanResult(
    val originalInput: String,
    val redactedOutput: String,
    val detections: List<PiiDetection>,
    val isClean: Boolean,
    val timestamp: Long
)

data class PiiDetection(
    val type: String,
    val originalValue: String,
    val startIndex: Int,
    val endIndex: Int,
    val confidence: Float
)
