package com.scypheon.sdk.core.agent.ooda

import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
import com.scypheon.sdk.core.agent.tool.ToolRegistry
import com.scypheon.sdk.core.safety.InputSanitizer
import com.scypheon.sdk.core.safety.SanitizedInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class ComplexityThreshold { LOW, MEDIUM, HIGH, CRITICAL }
enum class EnvironmentConstraint { NORMAL, CRITICAL_LOW_POWER, THERMAL_THROTTLED }

data class IntentClassification(
    val rootGoal: String,
    val primaryCapability: AgentSkillRegistry.SkillType,
    val complexity: ComplexityThreshold
)

data class Orientation(
    val rootGoal: String,
    val requiredCapability: AgentSkillRegistry.SkillType,
    val selectedSkill: AgentSkillRegistry.SkillDefinition,
    val requiresDeepReasoning: Boolean,
    val delegationReason: String?,
    val refinedQuery: String,
    val environmentConstraint: EnvironmentConstraint
)

data class OrientationConfig(
    val lowBatteryThreshold: Int = 10,
    val thermalCriticalStatus: ThermalStatus = ThermalStatus.CRITICAL,
    val deepReasoningComplexityThreshold: ComplexityThreshold = ComplexityThreshold.HIGH
)

/**
 * Step 2: ORIENT
 * Translates the user's query into a specific skill from the AgentSkillRegistry.
 * [v1.5.2-UNIFIED] Now uses modern SkillDefinition structure.
 */
@Singleton
class OrientStep @Inject constructor(
    private val skillRegistry: AgentSkillRegistry,
    private val toolRegistry: ToolRegistry,
    private val sanitizer: InputSanitizer,
    private val config: OrientationConfig
) {
    companion object {
        private val MEDICAL_COMPLEX_REGEX = Regex(
            "(interaksi|kenapa|analisis|bagaimana cara mendirikan)",
            RegexOption.IGNORE_CASE
        )
        private val MEDICAL_FAST_REGEX = Regex(
            "(dosis|alergi|gejala|obat)",
            RegexOption.IGNORE_CASE
        )
        private val STEM_REGEX = Regex(
            "(hitung|rumus|matematika)",
            RegexOption.IGNORE_CASE
        )
        private val EDUCATION_REGEX = Regex(
            "(ringkasan|pelajaran|ajarkan)",
            RegexOption.IGNORE_CASE
        )
    }

    suspend fun execute(
        observation: Observation,
        environment: DeviceEnvironment
    ): Orientation = withContext(Dispatchers.Default) {
        Timber.d("🧠 [OODA_ORIENT] Orienting mission via Unified Skill Registry.")

        val sanitized = sanitizer.sanitize(observation.query)
        val cleanQuery = sanitized.text

        val intent = classifyIntentFast(cleanQuery, observation.context)
        val skill = resolveSkillSafely(intent.primaryCapability)

        // Requires deep reasoning if complexity is high OR if no fast tools are available for this skill
        val fastTools = skill.getTools(toolRegistry)
        val requiresDeepReasoning = intent.complexity.ordinal >= config.deepReasoningComplexityThreshold.ordinal ||
                fastTools.isEmpty()

        val constraint = assessEnvironmentConstraint(environment)

        Orientation(
            rootGoal = intent.rootGoal,
            requiredCapability = intent.primaryCapability,
            selectedSkill = skill,
            requiresDeepReasoning = requiresDeepReasoning,
            delegationReason = if (requiresDeepReasoning) {
                "Complexity=${intent.complexity}, FastTools=${fastTools.size}"
            } else null,
            refinedQuery = cleanQuery,
            environmentConstraint = constraint
        )
    }

    private fun classifyIntentFast(query: String, context: List<String>): IntentClassification {
        return when {
            MEDICAL_COMPLEX_REGEX.containsMatchIn(query) ->
                IntentClassification("multi_hop_investigation", AgentSkillRegistry.SkillType.MEDICAL, ComplexityThreshold.HIGH)
            MEDICAL_FAST_REGEX.containsMatchIn(query) ->
                IntentClassification("factual_medical_lookup", AgentSkillRegistry.SkillType.MEDICAL, ComplexityThreshold.LOW)
            STEM_REGEX.containsMatchIn(query) ->
                IntentClassification("calculation", AgentSkillRegistry.SkillType.STEM, ComplexityThreshold.LOW)
            EDUCATION_REGEX.containsMatchIn(query) ->
                IntentClassification("lesson_summary", AgentSkillRegistry.SkillType.EDUCATION, ComplexityThreshold.MEDIUM)
            else ->
                IntentClassification("general_chat", AgentSkillRegistry.SkillType.GENERAL, ComplexityThreshold.LOW)
        }
    }

    private fun resolveSkillSafely(capability: AgentSkillRegistry.SkillType): AgentSkillRegistry.SkillDefinition {
        return skillRegistry.getSkill(capability)
            ?: skillRegistry.getSkill(AgentSkillRegistry.SkillType.GENERAL)
            ?: AgentSkillRegistry.SkillDefinition(
                type = AgentSkillRegistry.SkillType.GENERAL,
                displayName = "General",
                systemMandate = "Help the user.",
                recommendedToolNames = emptyList()
            )
    }

    private fun assessEnvironmentConstraint(env: DeviceEnvironment): EnvironmentConstraint {
        return when {
            env.batteryPercent < config.lowBatteryThreshold && !env.isCharging -> EnvironmentConstraint.CRITICAL_LOW_POWER
            env.thermalStatus == config.thermalCriticalStatus -> EnvironmentConstraint.THERMAL_THROTTLED
            else -> EnvironmentConstraint.NORMAL
        }
    }
}
