package com.scypheon.sdk.core.agent.ooda

import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
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

class DefaultToolSchemaValidator @Inject constructor() : ToolSchemaValidator {
    override fun validate(toolName: String, params: Map<String, String>): ValidationResult {
        return ValidationResult(true, params)
    }
}

interface ToolMatcher {
    data class ToolScore(val tool: AgentSkillRegistry.FastTool, val score: Float)
    suspend fun scoreTools(query: String, candidates: List<AgentSkillRegistry.FastTool>): List<ToolScore>
}

interface ParameterExtractor {
    suspend fun extract(query: String, tool: AgentSkillRegistry.FastTool): Map<String, String>
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
 * Selects exactly one FastTool from the chosen skill to execute.
 * Hardened with schema validation, dynamic confidence scoring, and strict medical fallback gates.
 */
@Singleton
class DecideStep @Inject constructor(
    private val toolMatcher: ToolMatcher,
    private val parameterExtractor: ParameterExtractor,
    private val schemaValidator: ToolSchemaValidator,
    private val config: DecisionConfig
) {
    suspend fun execute(
        orientation: Orientation,
        environment: DeviceEnvironment
    ): Decision = withContext(Dispatchers.Default) {
        Timber.d("🎯 [OODA_DECIDE] Selecting tool for skill: ${orientation.selectedSkill.type}")

        // 1. Filter tools by environment constraints
        val constraintFiltered = filterByConstraints(orientation.selectedSkill.fastTools, environment)
        
        if (constraintFiltered.isEmpty()) {
            Timber.w("🔋 [OODA_DECIDE] No tools survive environment constraints. Falling back.")
            return@withContext Decision.fallback(
                query = orientation.refinedQuery,
                reason = "All tools blocked by ${orientation.environmentConstraint}"
            )
        }

        // 2. Score & rank tools against query
        val scoredTools = toolMatcher.scoreTools(orientation.refinedQuery, constraintFiltered)
        val bestMatch = scoredTools.maxByOrNull { it.score }

        if (bestMatch == null || bestMatch.score < config.minMatchThreshold) {
            Timber.i("🎯 [OODA_DECIDE] Match score too low (${bestMatch?.score}). Falling back to safe chat.")
            return@withContext Decision.fallback(
                query = orientation.refinedQuery,
                reason = "Low match confidence: ${bestMatch?.score ?: 0.0f}"
            )
        }

        // 3. Extract parameters using specialized extractor
        val rawParams = parameterExtractor.extract(orientation.refinedQuery, bestMatch.tool)
        
        // 4. Validate against tool schema (critical for medical safety)
        val validationResult = schemaValidator.validate(bestMatch.tool.name, rawParams)
        if (!validationResult.isValid) {
            Timber.w("⚠️ [OODA_DECIDE] Schema validation failed: ${validationResult.errors}")
            if (config.enableStrictMedicalFallback && bestMatch.tool.isMedical) {
                return@withContext Decision.fallback(
                    query = orientation.refinedQuery,
                    reason = "Medical parameter validation failed: ${validationResult.errors}"
                )
            }
        }

        // 5. Calculate dynamic confidence
        val confidence = calculateConfidence(
            matchScore = bestMatch.score,
            validationScore = if (validationResult.isValid) 1.0f else 0.4f,
            isMedical = bestMatch.tool.isMedical
        )

        // 6. Medical safety gate: never execute low-confidence medical tools
        if (bestMatch.tool.isMedical && confidence < config.medicalMinConfidence) {
            Timber.w("🛑 [OODA_DECIDE] Medical confidence too low ($confidence). Forcing fallback.")
            return@withContext Decision.fallback(
                query = orientation.refinedQuery,
                reason = "Medical confidence below threshold ($confidence < ${config.medicalMinConfidence})"
            )
        }

        Timber.i("🎯 [OODA_DECIDE] Selected: ${bestMatch.tool.name} | Conf: $confidence | Params: ${validationResult.sanitizedParams}")

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
        tools: List<AgentSkillRegistry.FastTool>,
        env: DeviceEnvironment
    ): List<AgentSkillRegistry.FastTool> {
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
