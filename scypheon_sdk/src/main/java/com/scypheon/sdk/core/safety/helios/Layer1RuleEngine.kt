package com.scypheon.sdk.core.safety.helios

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Layer1RuleEngine @Inject constructor(
    private val ruleDao: RuleDao
) {
    data class RuleMatch(
        val isBlocked: Boolean,
        val riskScore: Double,
        val matchedRules: List<String>
    )

    /**
     * Deterministic Rule Engine: Stateful & Concurrent pattern matching.
     */
    suspend fun evaluate(input: String, contextHistory: String = ""): RuleMatch {
        val rules = ruleDao.getRulesByLayer(1)
        val combinedInput = "$contextHistory\n--CURRENT--\n$input"
        
        var totalRisk = 0.0
        val matches = mutableListOf<String>()

        for (rule in rules) {
            val isMatch = if (rule.useRegex) {
                Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(combinedInput)
            } else {
                combinedInput.contains(rule.pattern, ignoreCase = true)
            }

            if (isMatch) {
                totalRisk += rule.weight
                matches.add(rule.name)
                Timber.w("🛡️ [HELIOS L1] Pattern Match: ${rule.name} (+${rule.weight})")
            }
        }

        val riskScore = totalRisk.coerceAtMost(1.0)
        return RuleMatch(
            isBlocked = riskScore >= 1.0,
            riskScore = riskScore,
            matchedRules = matches
        )
    }
}
