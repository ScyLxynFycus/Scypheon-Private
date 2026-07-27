package com.scypheon.sdk.core.safety

/**
 * InputSanitizer: Enterprise-grade input normalization.
 * Prevents prompt injection, structural anomalies, and hidden character attacks.
 */
interface InputSanitizer {
    suspend fun sanitize(query: String): SanitizedInput
}

data class SanitizedInput(
    val text: String,
    val wasModified: Boolean
)
