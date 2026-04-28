package com.scypheon.sdk.core.humanitarian.education

import timber.log.Timber

/**
 * Enterprise Sub-System: Pronunciation Scorer.
 * Evaluates how close the student's raw STT output matches the expected target sentence
 * using a basic Levenshtein distance (edit distance) string similarity algorithm.
 */
object PronunciationScorer {

    /**
     * Calculates a basic pronunciation score (0 to 100) based on string similarity.
     */
    fun evaluateScore(expectedText: String, actualSttText: String): Int {
        if (expectedText.isEmpty() || actualSttText.isEmpty()) return 0

        val cleanExpected = expectedText.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        val cleanActual = actualSttText.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

        val distance = levenshteinDistance(cleanExpected, cleanActual)
        val maxLength = maxOf(cleanExpected.length, cleanActual.length)

        if (maxLength == 0) return 100

        val similarity = 1.0 - (distance.toDouble() / maxLength)
        val score = (similarity * 100).toInt().coerceIn(0, 100)

        Timber.i("🎯 Pronunciation Score: $score/100 (Expected: '$cleanExpected', Actual: '$cleanActual')")
        return score
    }

    /**
     * Standard Levenshtein Distance Algorithm
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[a.length][b.length]
    }
}
