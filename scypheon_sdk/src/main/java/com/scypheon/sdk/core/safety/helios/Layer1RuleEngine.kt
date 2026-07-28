package com.scypheon.sdk.core.safety.helios

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class Layer1RuleEngine @Inject constructor(
    private val ruleDao: RuleDao
) {
    data class RuleMatch(
        val isBlocked: Boolean,
        val riskScore: Double,
        val matchedRules: List<String>
    )
    
    // Stateful Risk Tracker (Fix P1: Exponential Decay)
    private val sessionTurnRisks = ConcurrentHashMap<String, MutableMap<Int, Double>>()
    private val DECAY_FACTOR = 0.85 // Each turn loses 15% weight
    private val MAX_WINDOW = 20

    /**
     * Deterministic Rule Engine: Stateful & Concurrent pattern matching.
     */
    suspend fun evaluate(input: String, contextHistory: String = "", sessionId: String = "default"): RuleMatch {
        val rules = ruleDao.getRulesByLayer(1)
        val combinedInput = "$contextHistory\n--CURRENT--\n$input"
        
        var currentTurnRisk = 0.0
        val matches = mutableListOf<String>()

        // 🛡️ L1 Pre-Filter: Base64 Obfuscation Detector
        // Detects continuous Base64 strings longer than 20 characters and checks entropy
        val base64Pattern = Regex("([A-Za-z0-9+/]{20,}=*)")
        val base64Matches = base64Pattern.findAll(input)
        for (match in base64Matches) {
            if (calculateEntropy(match.value) > 4.5) {
                currentTurnRisk += 1.0 // Instant block
                matches.add("BASE64_OBFUSCATION_PATTERN")
                Timber.w("🛡️ [HELIOS L1] Pattern Match: BASE64_OBFUSCATION_PATTERN (+1.0) for match \${match.value.take(10)}...")
                break
            }
        }

        // 🛡️ L1 Pre-Filter: Roleplay Sentinel (Absolute)
        val absoluteRoleplayPattern = Regex("(ignore previous instructions|pretend you are a simulator|DAN mode)", RegexOption.IGNORE_CASE)
        if (absoluteRoleplayPattern.containsMatchIn(combinedInput)) {
            currentTurnRisk += 1.0
            matches.add("ROLEPLAY_ABSOLUTE")
            Timber.w("🛡️ [HELIOS L1] Pattern Match: ROLEPLAY_ABSOLUTE (+1.0)")
        }

        // 🛡️ L1 Pre-Filter: Roleplay Sentinel (Ambiguous Framing)
        val ambiguousRoleplayPattern = Regex("(academic analysis|research assistant|hypothetical scenario)", RegexOption.IGNORE_CASE)
        val ambiguousCount = ambiguousRoleplayPattern.findAll(combinedInput).count()
        if (ambiguousCount > 0) {
            val scoreToAdd = ambiguousCount * 0.3
            currentTurnRisk += scoreToAdd
            matches.add("ROLEPLAY_AMBIGUOUS_FRAMING")
            Timber.w("🛡️ [HELIOS L1] Pattern Match: ROLEPLAY_AMBIGUOUS_FRAMING (+$scoreToAdd)")
        }

        for (rule in rules) {
            val isMatch = if (rule.useRegex) {
                Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(combinedInput)
            } else {
                combinedInput.contains(rule.pattern, ignoreCase = true)
            }

            if (isMatch) {
                currentTurnRisk += rule.weight
                matches.add(rule.name)
                Timber.w("🛡️ [HELIOS L1] Pattern Match: ${rule.name} (+${rule.weight})")
            }
        }

        // Calculate decayed historical risk
        val historyMap = sessionTurnRisks.getOrPut(sessionId) { mutableMapOf() }
        val currentTurn = historyMap.size
        historyMap[currentTurn] = currentTurnRisk
        
        var totalDecayedRisk = 0.0
        for ((turn, risk) in historyMap) {
            if (currentTurn - turn <= MAX_WINDOW) {
                val age = currentTurn - turn
                totalDecayedRisk += risk * Math.pow(DECAY_FACTOR, age.toDouble())
            }
        }

        val finalRiskScore = totalDecayedRisk.coerceAtMost(1.0)
        
        return RuleMatch(
            isBlocked = finalRiskScore >= 1.0,
            riskScore = finalRiskScore,
            matchedRules = matches
        )
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
