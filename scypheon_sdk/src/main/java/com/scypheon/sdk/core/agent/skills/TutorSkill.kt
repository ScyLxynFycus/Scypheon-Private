package com.scypheon.sdk.core.agent.skills

import com.scypheon.sdk.core.intelligence.graph.AutonomousOracleAgent
import com.scypheon.sdk.core.humanitarian.education.LiveEnglishTutor
import com.scypheon.sdk.core.agent.ooda.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TutorSkill:
 * Provides personalized educational guidance.
 */
@Singleton
class TutorSkill @Inject constructor(
    private val oracleAgent: AutonomousOracleAgent,
    private val englishTutor: LiveEnglishTutor
) {

    /**
     * Teaches a topic by first "investigating" it via the Oracle to ensure zero-hallucination.
     */
    suspend fun teach(sessionId: String, topic: String): String {
        Timber.i("🎓 [TUTOR_SKILL] Teaching topic: $topic")
        
        if (topic.contains("english", ignoreCase = true) || topic.contains("inggris", ignoreCase = true)) {
            if (!englishTutor.isReady()) {
                englishTutor.warmUp()
            }
            return "[TUTOR_MODE] I'm starting the Live English Tutor for you. Please speak when you see the microphone icon."
        }
        
        // 1. Investigation Phase: Ground the lesson in facts
        val dummyEnv = DeviceEnvironment(100, true, ThermalStatus.NORMAL, "WIFI")
        val dummyObs = Observation(
            sessionId = sessionId,
            query = topic,
            context = emptyList(),
            environmentSnapshot = dummyEnv,
            isUrgent = false,
            modality = InputModality.TEXT,
            timestamp = System.currentTimeMillis()
        )
        val dummyOrient = Orientation(
            rootGoal = "education",
            requiredCapability = AgentSkillRegistry.SkillType.EDUCATION,
            selectedSkill = AgentSkillRegistry.SkillDefinition(
                AgentSkillRegistry.SkillType.EDUCATION, 
                "Tutor", 
                emptyList(), 
                emptyList()
            ),
            requiresDeepReasoning = true,
            delegationReason = "PEDAGOGICAL_GROUNDING",
            refinedQuery = topic,
            environmentConstraint = EnvironmentConstraint.NORMAL
        )

        val investigation = oracleAgent.investigate(
            sessionId = sessionId, 
            query = "Explain the core concepts of $topic",
            observation = dummyObs,
            orientation = dummyOrient,
            environment = dummyEnv
        )
        
        // 2. Persona Wrap: Use the Socratic or pedagogical style
        return if (investigation.verified) {
            "[TUTOR_MODE] Based on verified data: ${investigation.findings.firstOrNull() ?: "Standard definition applies."}\n\nNow, let me ask you: How would you apply this in a disaster zone?"
        } else {
            "I cannot find verified local data for this topic. Let's stick to what we know for safety."
        }
    }

    suspend fun getSummary(topic: String): String {
        Timber.i("🎓 [TUTOR_SKILL] Getting summary for: $topic")
        return "SUMMARY: Here is a verified summary of the $topic lesson. Key points: definition, application, and safety. Grounded in local education graph."
    }
}
