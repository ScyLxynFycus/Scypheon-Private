package com.scypheon.sdk.core.agent.ooda

import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
import com.scypheon.sdk.core.agent.tool.Tool
import com.scypheon.sdk.core.agent.tool.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// --- Supporting Interfaces & Data Classes for DecideStep ---
data class ValidationResult(val isValid: Boolean, val sanitizedParams: Map<String, String>, val errors: List<String> = emptyList())

interface ToolSchemaValidator {
    fun validate(toolName: String, params: Map<String, String>): ValidationResult
}

class DefaultToolSchemaValidator @Inject constructor(
    private val toolRegistry: ToolRegistry
) : ToolSchemaValidator {
    override fun validate(toolName: String, params: Map<String, String>): ValidationResult {
        val tool = toolRegistry.resolve(toolName)
            ?: return ValidationResult(false, params, listOf("Tool '$toolName' not found"))
        
        val anyParams = params.mapValues { it.value as Any? }
        val toolValidation = tool.validate(anyParams)
        
        return if (toolValidation.isValid) {
            ValidationResult(true, params)
        } else {
            ValidationResult(false, params, toolValidation.errors.ifEmpty { listOf("Validation failed for $toolName") })
        }
    }
}

interface ToolMatcher {
    data class ToolScore(val tool: Tool, val score: Float)
    suspend fun scoreTools(query: String, candidates: List<Tool>): List<ToolScore>
}

interface ParameterExtractor {
    suspend fun extract(query: String, tool: Tool): Map<String, String>
}

// --- Core DecideStep Implementation ---

data class Decision(
    val toolName: String,
    val parameters: Map<String, String>,
    val confidence: Float,
    val rationale: DecisionRationale,
    val isFallback: Boolean = false
) {
    companion object {
        fun fallback(query: String, reason: String): Decision = Decision(
            toolName = "fallback_chat",
            parameters = mapOf("query" to query),
            confidence = 0.3f,
            rationale = DecisionRationale(
                matchScore = 0.0f,
                validationPassed = false,
                constraintApplied = null,
                fallbackReason = reason
            ),
            isFallback = true
        )
    }
}

data class DecisionRationale(
    val matchScore: Float,
    val validationPassed: Boolean,
    val constraintApplied: EnvironmentConstraint?,
    val fallbackReason: String?
)

data class DecisionConfig(
    val minMatchThreshold: Float = 0.65f,
    val medicalMinConfidence: Float = 0.80f,
    val enableStrictMedicalFallback: Boolean = true
)

/**
 * Step 3: DECIDE
 * Selects exactly one Tool from the chosen skill to execute.
 * [v1.5.2-UNIFIED] Decoupled from legacy definitions.
 */
@Singleton
class DecideStep @Inject constructor(
    private val toolMatcher: ToolMatcher,
    private val parameterExtractor: ParameterExtractor,
    private val schemaValidator: ToolSchemaValidator,
    private val toolRegistry: ToolRegistry,
    private val config: DecisionConfig
) {
    suspend fun execute(
        orientation: Orientation,
        environment: DeviceEnvironment
    ): Decision = withContext(Dispatchers.Default) {
        Timber.d("🧠 [OODA_DECIDE] Selecting tool for skill: ${orientation.selectedSkill.type}")

        val allTools = orientation.selectedSkill.getTools(toolRegistry)
        val constraintFiltered = filterByConstraints(allTools, environment)

        if (constraintFiltered.isEmpty()) {
            Timber.w("🚨 [OODA_DECIDE] No tools survive environment constraints.")
            return@withContext Decision.fallback(
                query = orientation.refinedQuery,
                reason = "All tools blocked by constraints."
            )
        }

        val scoredTools = toolMatcher.scoreTools(orientation.refinedQuery, constraintFiltered)
        val bestMatch = scoredTools.maxByOrNull { it.score }

        if (bestMatch == null || bestMatch.score < config.minMatchThreshold) {
            return@withContext Decision.fallback(
                query = orientation.refinedQuery,
                reason = "Low match confidence: ${bestMatch?.score ?: 0.0f}"
            )
        }

        val rawParams = parameterExtractor.extract(orientation.refinedQuery, bestMatch.tool)
        val validationResult = schemaValidator.validate(bestMatch.tool.name, rawParams)

        if (!validationResult.isValid) {
            if (config.enableStrictMedicalFallback && bestMatch.tool.isMedical) {
                return@withContext Decision.fallback(
                    query = orientation.refinedQuery,
                    reason = "Medical parameter validation failed."
                )
            }
        }

        val confidence = calculateConfidence(
            matchScore = bestMatch.score,
            validationScore = if (validationResult.isValid) 1.0f else 0.4f,
            isMedical = bestMatch.tool.isMedical
        )

        if (bestMatch.tool.isMedical && confidence < config.medicalMinConfidence) {
            return@withContext Decision.fallback(
                query = orientation.refinedQuery,
                reason = "Medical confidence below safety threshold."
            )
        }

        Decision(
            toolName = bestMatch.tool.name,
            parameters = validationResult.sanitizedParams,
            confidence = confidence,
            rationale = DecisionRationale(
                matchScore = bestMatch.score,
                validationPassed = validationResult.isValid,
                constraintApplied = orientation.environmentConstraint,
                fallbackReason = null
            ),
            isFallback = false
        )
    }

    private fun filterByConstraints(
        tools: List<Tool>,
        env: DeviceEnvironment
    ): List<Tool> {
        return tools.filter { tool ->
            when {
                env.batteryPercent < 15 && !env.isCharging -> tool.constraintProfile.powerCost <= 2
                env.thermalStatus == ThermalStatus.CRITICAL -> tool.constraintProfile.thermalImpact <= 1
                env.networkType == "none" -> !tool.constraintProfile.requiresNetwork
                else -> true
            }
        }
    }

    private fun calculateConfidence(matchScore: Float, validationScore: Float, isMedical: Boolean): Float {
        val base = (matchScore * 0.6f) + (validationScore * 0.4f)
        return if (isMedical) base.coerceIn(0.0f, 0.95f) else base.coerceIn(0.0f, 1.0f)
    }
}
