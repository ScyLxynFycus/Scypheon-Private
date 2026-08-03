package com.scypheon.sdk.core.safety

import com.scypheon.sdk.core.agent.ooda.SessionContext
import com.scypheon.sdk.core.agent.SafetyVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyPipelineImpl @Inject constructor(
    private val sanitizer: InputSanitizerImpl
) : com.scypheon.sdk.core.agent.SafetyPipeline {
    companion object {
        private val JAILBREAK_PATTERNS = setOf(
            "ignore previous", "system prompt", "override safety", "reveal secret",
            "bypass_security", "dan mode", "developer mode", "jailbreak", "unrestricted"
        )
        private val DELIMITER_INJECTION = Regex("<system>|<data>|<context>|\"\"\"|```xml|```json", RegexOption.IGNORE_CASE)
        private const val ENTROPY_THRESHOLD = 4.8
    }

    override suspend fun evaluateInput(query: String, session: SessionContext): SafetyVerdict = withContext(Dispatchers.Default) {
        val sanitized = sanitizer.sanitize(query)
        val text = sanitized.text

        if (text.isBlank()) return@withContext SafetyVerdict.BLOCKED
        if (calculateEntropy(text) > ENTROPY_THRESHOLD) return@withContext SafetyVerdict.BLOCKED

        val lower = text.lowercase()
        if (JAILBREAK_PATTERNS.any { lower.contains(it) }) return@withContext SafetyVerdict.BLOCKED
        if (DELIMITER_INJECTION.containsMatchIn(text)) return@withContext SafetyVerdict.FLAGGED

        SafetyVerdict.SAFE
    }

    private fun calculateEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0
        val length = input.length.toDouble()
        return input.groupingBy { it }.eachCount().values.sumOf { count ->
            val p = count / length
            if (p > 0.0) -p * kotlin.math.log2(p) else 0.0
        }
    }
}
