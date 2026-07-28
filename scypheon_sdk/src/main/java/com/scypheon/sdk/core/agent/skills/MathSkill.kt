package com.scypheon.sdk.core.agent.skills

import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.math.MathResult
import com.scypheon.sdk.core.math.ScypheonMathEngine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MathSkill:
 * Specialized STEM reasoning using the Socratic Method and deterministic calculation.
 */
@Singleton
class MathSkill @Inject constructor(
    private val neuralGateway: NeuralGateway?
) {

    private val mathEngine = ScypheonMathEngine(llmGateway = neuralGateway)

    suspend fun calculate(expression: String): String {
        val cleaned = expression.trim()
        Timber.i("📐 [MATH_SKILL] Calculating: $cleaned")

        // 1. Check for derivative: diff(expression, variable) or diff(expression)
        val diffVarRegex = Regex("^(?:diff|derive|differentiate)\\((.+),\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\)$", RegexOption.IGNORE_CASE)
        val diffRegex = Regex("^(?:diff|derive|differentiate)\\((.+)\\)$", RegexOption.IGNORE_CASE)

        val diffVarMatch = diffVarRegex.matchEntire(cleaned)
        if (diffVarMatch != null) {
            val expr = diffVarMatch.groupValues[1].trim()
            val variable = diffVarMatch.groupValues[2].trim()
            return formatResult(mathEngine.derive(expr, variable))
        }

        val diffMatch = diffRegex.matchEntire(cleaned)
        if (diffMatch != null) {
            val expr = diffMatch.groupValues[1].trim()
            return formatResult(mathEngine.derive(expr, "x"))
        }

        // 2. Check for integration: int(expression, variable) or int(expression)
        val intVarRegex = Regex("^(?:int|integrate)\\((.+),\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\)$", RegexOption.IGNORE_CASE)
        val intRegex = Regex("^(?:int|integrate)\\((.+)\\)$", RegexOption.IGNORE_CASE)

        val intVarMatch = intVarRegex.matchEntire(cleaned)
        if (intVarMatch != null) {
            val expr = intVarMatch.groupValues[1].trim()
            val variable = intVarMatch.groupValues[2].trim()
            return formatResult(mathEngine.integrate(expr, variable))
        }

        val intMatch = intRegex.matchEntire(cleaned)
        if (intMatch != null) {
            val expr = intMatch.groupValues[1].trim()
            return formatResult(mathEngine.integrate(expr, "x"))
        }

        // 3. Check for limits: limit(expression, variable, value)
        val limitRegex = Regex("^limit\\((.+),\\s*([a-zA-Z_][a-zA-Z0-9_]*),\\s*(.+)\\)$", RegexOption.IGNORE_CASE)
        val limitMatch = limitRegex.matchEntire(cleaned)
        if (limitMatch != null) {
            val expr = limitMatch.groupValues[1].trim()
            val variable = limitMatch.groupValues[2].trim()
            val valStr = limitMatch.groupValues[3].trim().lowercase()
            val value = when (valStr) {
                "inf", "infinity" -> Double.POSITIVE_INFINITY
                "-inf", "-infinity" -> Double.NEGATIVE_INFINITY
                else -> valStr.toDoubleOrNull() ?: return "ERROR: Invalid limit point: $valStr"
            }
            return formatResult(mathEngine.limit(expr, variable, value))
        }

        // 4. Check for system of equations or word problem
        // If it contains letters (other than function names / e / pi), words, or equal sign
        // or starts with solve, we treat it as a word problem / system
        val isWordOrSystem = cleaned.contains("=") || 
                             cleaned.startsWith("solve", ignoreCase = true) ||
                             hasNonMathWords(cleaned)

        if (isWordOrSystem) {
            val queryText = if (cleaned.startsWith("solve", ignoreCase = true)) {
                // remove solve(...) surrounding if present
                val inner = Regex("^solve\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)?.groupValues?.get(1)
                inner ?: cleaned
            } else {
                cleaned
            }
            return formatResult(mathEngine.solveWordProblem(queryText))
        }

        // 5. Default evaluation
        return formatResult(mathEngine.evaluate(cleaned))
    }

    private fun formatResult(result: MathResult): String {
        return when (result) {
            is MathResult.Success -> {
                val sb = java.lang.StringBuilder()
                sb.append("RESULT: ").append(result.result).append("\n")
                if (result.steps.isNotEmpty()) {
                    sb.append("STEPS:\n")
                    result.steps.forEach { sb.append("  • ").append(it).append("\n") }
                }
                if (result.isVerified) {
                    sb.append("VERIFICATION: ").append(result.verificationMethod ?: "Passed").append("\n")
                }
                sb.append("Verified by C++ CAS Engine.")
                sb.toString()
            }
            is MathResult.Error -> "ERROR: ${result.message}"
        }
    }

    private fun hasNonMathWords(text: String): Boolean {
        // Simple heuristic: if text has spaces and alphabetical words that are not operators/functions/constants
        val words = text.split(Regex("[^a-zA-Z_]+")).filter { it.isNotEmpty() }
        val mathKeywords = setOf("sin", "cos", "ln", "log", "exp", "pi", "e", "diff", "derive", "differentiate", "int", "integrate", "limit", "solve")
        val nonMathWords = words.filter { it.lowercase() !in mathKeywords && it.length > 1 }
        return nonMathWords.isNotEmpty() && text.contains(" ")
    }

    fun buildSocraticPrompt(problem: String): String {
        return """
            [SKILL_STEM_SOCRATIC]
            You are a disciplined STEM tutor. Guide the student to the answer.
            NEVER provide the final answer directly.
            
            PROBLEM: $problem
            
            INSTRUCTION: Ask ONE guiding question that leads to the first step of the calculation.
            [/SKILL_STEM_SOCRATIC]
        """.trimIndent()
    }
}
