package com.scypheon.sdk.core.grounding

/**
 * Authoritative interface for factual verification in the Scypheon SDK.
 * Ensures model outputs are cross-referenced with secure, offline-native databases.
 */
interface MedicalGroundingEngine {
    /**
     * Verifies a term or statement against the local knowledge base.
     * Returns a confidence score and verified sources.
     */
    suspend fun verify(term: String, domain: String = "medical"): GroundingResult
}

/**
 * Result of a factual verification attempt.
 */
data class GroundingResult(
    val confidence: Float,
    val sources: List<String>,
    val domain: String,
    val exactMatch: Boolean
)
