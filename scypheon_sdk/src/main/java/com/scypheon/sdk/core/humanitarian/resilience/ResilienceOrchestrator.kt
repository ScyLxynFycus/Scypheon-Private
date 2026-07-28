package com.scypheon.sdk.core.humanitarian.resilience

import com.scypheon.sdk.core.agent.ooda.*
import com.scypheon.sdk.core.humanitarian.accessibility.DeafEnvironmentGuardian
import com.scypheon.sdk.core.intelligence.graph.AutonomousOracleAgent
import com.scypheon.sdk.core.safety.helios.SafetyPipeline
import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * ResilienceOrchestrator:
 * The "Central Nervous System" for Global Resilience.
 */
@Singleton
class ResilienceOrchestrator @Inject constructor(
    private val guardian: DeafEnvironmentGuardian,
    private val oracleAgent: AutonomousOracleAgent,
    private val safetyPipeline: SafetyPipeline
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Starts monitoring environmental threats.
     */
    fun startMonitoring() {
        Timber.i("🧠 [RESILIENCE] Starting environmental threat monitoring...")

        guardian.setOnAlertTriggeredListener { threatType, message ->
            handleEnvironmentalThreat(threatType, message)
        }

        guardian.startListening()
    }

    private fun handleEnvironmentalThreat(type: String, message: String) {
        Timber.w("🚨 [RESILIENCE_ALERT] Threat Detected: $type ($message)")

        scope.launch {
            val emergencyQuery = "What is the emergency evacuation procedure for a $type threat?"

            val dummyEnv = DeviceEnvironment(100, true, ThermalStatus.NORMAL, "WIFI")
            val dummyObs = Observation(
                sessionId = "system_emergency",
                query = emergencyQuery,
                context = emptyList(),
                environmentSnapshot = dummyEnv,
                isUrgent = true,
                modality = InputModality.TEXT,
                timestamp = System.currentTimeMillis()
            )
            val dummyOrient = Orientation(
                rootGoal = "emergency_response",
                requiredCapability = AgentSkillRegistry.SkillType.GENERAL,
                selectedSkill = AgentSkillRegistry.SkillDefinition(
                    AgentSkillRegistry.SkillType.GENERAL,
                    "Emergency Response",
                    "Handle emergency threats.",
                    emptyList()
                ),
                requiresDeepReasoning = true,
                delegationReason = "ENVIRONMENTAL_THREAT",
                refinedQuery = emergencyQuery,
                environmentConstraint = EnvironmentConstraint.NORMAL
            )

            val investigation = oracleAgent.investigate(
                sessionId = "system_emergency",
                query = emergencyQuery,
                observation = dummyObs,
                orientation = dummyOrient,
                environment = dummyEnv
            )

            val response = oracleAgent.buildFinalIntelligencePrompt(emergencyQuery, investigation)
            Timber.i("🛡️ [RESILIENCE_RESPONSE] Emergency Protocol Generated: $response")
        }
    }

    fun stopMonitoring() {
        guardian.stopListening()
        scope.cancel()
    }
}
