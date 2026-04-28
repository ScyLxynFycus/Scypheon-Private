package com.scypheon.sdk.core.humanitarian.education

import timber.log.Timber

/**
 * Enterprise Sub-System: Audio Sanitizer for Live English Tutor.
 * Pre-processes raw Speech-to-Text outputs before feeding them to the LLM.
 * Removes hesitation markers, normalizes punctuation, and standardizes spacing.
 */
object AudioSanitizer {

    private val FILLER_WORDS = listOf(
        "uh", "um", "hm", "hmm", "like", "you know", "basically", "actually", "literally",
        "kayaknya", "anu", "kayak", "terus", "jadi"
    )

    fun sanitize(input: String): String {
        var cleanText = input.trim()

        // Remove filler words using regex word boundaries (case-insensitive)
        val fillerPattern = FILLER_WORDS.joinToString("|") { "\\b$it\\b" }
        cleanText = cleanText.replace(Regex("(?i)($fillerPattern)"), "")

        // Normalize multiple spaces caused by removing words
        cleanText = cleanText.replace(Regex("\\s+"), " ").trim()

        // Ensure proper sentence termination for LLM parsers
        if (cleanText.isNotEmpty() && !cleanText.matches(Regex(".*[.?!]$"))) {
            cleanText += "."
        }

        Timber.d("🧹 AudioSanitizer: '$input' -> '$cleanText'")
        return cleanText
    }
}
