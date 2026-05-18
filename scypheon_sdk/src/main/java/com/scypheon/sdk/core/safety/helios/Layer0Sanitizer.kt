package com.scypheon.sdk.core.safety.helios

import timber.log.Timber
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log2

@Singleton
class Layer0Sanitizer @Inject constructor() {

    data class SanitizedResult(
        val output: String,
        val entropy: Double,
        val isFlagged: Boolean,
        val reason: String? = null
    )

    /**
     * Deep Sanitization: NFKC Normalization + Entropy Check + Truncation.
     */
    fun sanitize(input: String): SanitizedResult {
        // 1. Unicode Normalization (NFKC) to neutralize homoglyphs
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "") // Remove zero-width chars
        
        // 2. Shannon Entropy Analysis
        val entropy = calculateEntropy(normalized)
        val isEntropyHigh = entropy > 4.5
        
        // 3. Length Truncation (Safety limit: 1500 chars for mobile edge)
        val finalOutput = if (normalized.length > 1500) {
            normalized.take(1500)
        } else {
            normalized
        }

        if (isEntropyHigh) {
            Timber.w("🛡️ [HELIOS L0] High entropy detected ($entropy). Potential obfuscated payload.")
        }

        return SanitizedResult(
            output = finalOutput,
            entropy = entropy,
            isFlagged = isEntropyHigh,
            reason = if (isEntropyHigh) "EXCESSIVE_ENTROPY" else null
        )
    }

    private fun calculateEntropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val counts = s.groupingBy { it }.eachCount()
        val len = s.length.toDouble()
        return counts.values.sumOf { count ->
            val p = count / len
            -(p * log2(p))
        }
    }
}
