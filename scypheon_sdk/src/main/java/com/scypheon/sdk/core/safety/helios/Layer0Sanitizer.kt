package com.scypheon.sdk.core.safety.helios

import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.safety.SafetyConfig
import kotlin.math.log2
import com.scypheon.sdk.core.safety.helios.decoders.DecodedPayload

@Singleton
class Layer0Sanitizer @Inject constructor(
    private val config: SafetyConfig,
    private val normalizer: InputNormalizer,
    private val decoderChain: DecoderChain
) {
    companion object {
        private const val MAX_RECURSION_DEPTH = 3
        private const val DECODE_TIMEOUT_MS = 200L
    }
    
    data class SanitizationResult(
        val isSafe: Boolean,
        val threatScore: Float,
        val threatType: ThreatType?,
        val details: String,
        val sanitizedInput: String = "",
        val decodedPlaintexts: List<String> = emptyList(),
        val normalizedInput: NormalizedInput? = null
    ) {
        val isFlagged: Boolean get() = !isSafe
        val reason: String? get() = threatType?.name
    }
    
    enum class ThreatType {
        ENCODED_PAYLOAD,
        EXCESSIVE_ENTROPY,
        EXCESSIVE_REPETITION,
        SUSPICIOUS_CHARACTERS,
        MULTI_SIGNAL
    }
    
    suspend fun sanitize(input: String, depth: Int = 0): SanitizationResult {
        // Prevent infinite recursion
        if (depth >= MAX_RECURSION_DEPTH) {
            Timber.w("🛡️ [L0] Max recursion depth exceeded")
            return SanitizationResult(
                isSafe = false,
                threatScore = 1.0f,
                threatType = ThreatType.ENCODED_PAYLOAD,
                details = "Max recursion depth exceeded"
            )
        }
        
        // Stage 0: Pre-processing (Normalization)
        val normalized = normalizer.normalize(input)
        val workingInput = normalized.normalized
        val inputHash = hashString(workingInput)
        
        var threatScore = 0.0f
        val threats = mutableListOf<ThreatType>()
        val decodedPlaintextsAccumulator = mutableListOf<String>()
        
        // RLE repetition check
        if (workingInput.length >= config.minRleLength) {
            val rleRatio = calculateRleRatio(workingInput)
            if (rleRatio < config.rleRatioThreshold) {
                threatScore += 0.8f // Heavy penalty for repetition dilution
                threats.add(ThreatType.EXCESSIVE_REPETITION)
            }
        }
        
        // Suspicious character check (control chars mostly, ZWJ handled by normalizer)
        if (containsSuspiciousChars(workingInput)) {
            threatScore += 0.2f
            threats.add(ThreatType.SUSPICIOUS_CHARACTERS)
        }
        
        // Stage 1: Parallel Decoder Chain
        val decodedPayloads = decoderChain.decodeAll(workingInput, inputHash)
        
        for (payload in decodedPayloads) {
            decodedPlaintextsAccumulator.add(payload.decoded)
            
            // Recursive sanitization with timeout
            val decodedResult = withTimeoutOrNull(DECODE_TIMEOUT_MS) {
                sanitize(payload.decoded, depth + 1)
            }
            
            if (decodedResult == null) {
                Timber.w("🛡️ [L0] Decoded payload sanitization timeout")
                return SanitizationResult(
                    isSafe = false,
                    threatScore = 1.0f,
                    threatType = ThreatType.ENCODED_PAYLOAD,
                    details = "Decoded payload sanitization timeout",
                    normalizedInput = normalized
                )
            }
            
            decodedPlaintextsAccumulator.addAll(decodedResult.decodedPlaintexts)
            
            if (!decodedResult.isSafe) {
                Timber.w("🛡️ [L0] Encoded payload (${payload.encoding}) unsafe: ${decodedResult.details}")
                return SanitizationResult(
                    isSafe = false,
                    threatScore = decodedResult.threatScore,
                    threatType = ThreatType.ENCODED_PAYLOAD,
                    details = "Encoded payload (${payload.encoding}) contains threat: ${decodedResult.details}",
                    decodedPlaintexts = decodedPlaintextsAccumulator,
                    normalizedInput = normalized
                )
            }
        }
        
        threatScore = threatScore.coerceAtMost(1.0f)
        
        return SanitizationResult(
            isSafe = threatScore < 0.7f,
            threatScore = threatScore,
            threatType = when {
                threats.isEmpty() -> null
                threats.size == 1 -> threats.first()
                else -> ThreatType.MULTI_SIGNAL
            },
            details = if (threats.isEmpty()) "Clean" else threats.joinToString(),
            sanitizedInput = workingInput,
            decodedPlaintexts = decodedPlaintextsAccumulator,
            normalizedInput = normalized
        )
    }
    
    private fun hashString(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
    
    private fun calculateShannonEntropy(input: String): Float {
        if (input.isEmpty()) return 0.0f
        
        val charCounts = mutableMapOf<Char, Int>()
        for (char in input) {
            charCounts[char] = charCounts.getOrDefault(char, 0) + 1
        }
        
        val length = input.length.toFloat()
        var entropy = 0.0
        
        for (count in charCounts.values) {
            val probability = count / length
            if (probability > 0) {
                entropy -= probability * log2(probability.toDouble())
            }
        }
        
        return entropy.toFloat()
    }
    
    private fun containsSuspiciousChars(input: String): Boolean {
        return input.any { char ->
            char.code in 0x00..0x08 ||      // Control chars (excluding tab, lf, cr)
            char.code in 0x0E..0x1F ||      
            char.code in 0x7F..0x9F         
        }
    }

    private fun calculateRleRatio(input: String): Double {
        if (input.isEmpty()) return 1.0
        var runs = 0
        var prevChar: Char? = null
        for (char in input) {
            if (char != prevChar) {
                runs++
                prevChar = char
            }
        }
        return runs.toDouble() / input.length
    }

    private fun hasHighEntropyWindow(input: String): Boolean {
        val windowSize = config.slidingWindowSize
        if (input.length <= windowSize) {
            return calculateShannonEntropy(input) > config.highEntropyThreshold
        }
        for (i in 0..input.length - windowSize) {
            val window = input.substring(i, i + windowSize)
            if (calculateShannonEntropy(window) > config.highEntropyThreshold) {
                return true
            }
        }
        return false
    }
}
