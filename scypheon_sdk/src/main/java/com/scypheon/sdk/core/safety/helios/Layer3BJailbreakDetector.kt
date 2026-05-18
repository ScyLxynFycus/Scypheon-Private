package com.scypheon.sdk.core.safety.helios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects structural jailbreak patterns: roleplay framing, instruction nesting,
 * delimiter abuse, and context poisoning. Deterministic regex/AST-like parsing.
 * Zero ML dependency for offline resilience.
 */
@Singleton
class Layer3BJailbreakDetector @Inject constructor() {
    companion object {
        // Roleplay/Persona injection patterns
        private val ROLEPLAY_PATTERNS = listOf(
            Regex("(anggap|berpura-pura|seolah-olah|act as|pretend you are|imagine you are|roleplay as)", RegexOption.IGNORE_CASE),
            Regex("(you are a (doctor|admin|hacker|developer|system) that|kamu adalah seorang)", RegexOption.IGNORE_CASE)
        )

        // Instruction nesting / prompt leakage patterns
        private val NESTING_PATTERNS = listOf(
            Regex("(ignore previous|bypass safety|disregard instructions|override rules)", RegexOption.IGNORE_CASE),
            Regex("(system prompt|developer mode|debug mode|unrestricted)", RegexOption.IGNORE_CASE)
        )

        // Delimiter/context poisoning patterns
        private val DELIMITER_PATTERNS = listOf(
            Regex("(<system>|<data>|<context>|<instruction>|\"\"\"|```|\\[SYSTEM\\]|\\[USER\\])", RegexOption.IGNORE_CASE),
            Regex("(start of prompt|end of prompt|begin generation|stop sequence)", RegexOption.IGNORE_CASE)
        )

        private const val MIN_MATCHES_FOR_BLOCK = 2
    }

    /**
     * Analyzes input for structural jailbreak patterns.
     * Returns structured report for audit/XAI consumption.
     */
    suspend fun detectStructuralJailbreak(input: String, sessionId: String): JailbreakReport = withContext(Dispatchers.Default) {
        val matches = mutableListOf<JailbreakMatch>()
        
        ROLEPLAY_PATTERNS.forEach { pattern ->
            pattern.findAll(input).forEach { match ->
                matches.add(JailbreakMatch(
                    type = "ROLEPLAY_FRAMING",
                    pattern = match.value,
                    confidence = 0.9f,
                    severity = ViolationSeverity.HIGH
                ))
            }
        }

        NESTING_PATTERNS.forEach { pattern ->
            pattern.findAll(input).forEach { match ->
                matches.add(JailbreakMatch(
                    type = "INSTRUCTION_NESTING",
                    pattern = match.value,
                    confidence = 0.85f,
                    severity = ViolationSeverity.CRITICAL
                ))
            }
        }

        DELIMITER_PATTERNS.forEach { pattern ->
            pattern.findAll(input).forEach { match ->
                matches.add(JailbreakMatch(
                    type = "DELIMITER_POISONING",
                    pattern = match.value,
                    confidence = 0.8f,
                    severity = ViolationSeverity.MEDIUM
                ))
            }
        }

        val totalScore = matches.size.toFloat()
        val isBlocked = matches.any { it.severity == ViolationSeverity.CRITICAL } || 
                        matches.count { it.confidence >= 0.8f } >= MIN_MATCHES_FOR_BLOCK

        JailbreakReport(
            sessionId = sessionId,
            inputLength = input.length,
            matches = matches,
            totalScore = totalScore,
            isBlocked = isBlocked,
            timestamp = System.currentTimeMillis()
        )
    }
}

data class JailbreakReport(
    val sessionId: String,
    val inputLength: Int,
    val matches: List<JailbreakMatch>,
    val totalScore: Float,
    val isBlocked: Boolean,
    val timestamp: Long
)

data class JailbreakMatch(
    val type: String,
    val pattern: String,
    val confidence: Float,
    val severity: ViolationSeverity
)
