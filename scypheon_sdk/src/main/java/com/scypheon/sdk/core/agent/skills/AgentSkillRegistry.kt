package com.scypheon.sdk.core.agent.skills

import com.scypheon.sdk.core.agent.tool.Tool
import com.scypheon.sdk.core.agent.tool.ToolRegistry
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgentSkillRegistry (NEW):
 * The high-level 'Brain' of the Scypheon Agent. 
 * Manages domain-specific instructions (System Prompts) and capability orchestration.
 * 
 * Logic follows the Claude Code 'Skill' pattern:
 * - Skill = Specialized System Instruction + Tool Set Reference.
 * - Tool = Atomic action (managed by ToolRegistry).
 */
@Singleton
class AgentSkillRegistry @Inject constructor(
    private val toolRegistry: dagger.Lazy<ToolRegistry>,
    private val fusionRouter: MultiDomainFusionRouter,
    val medicalSkill: MedicalSkill,
    val tutorSkill: TutorSkill,
    val mathSkill: MathSkill,
    val disasterSkill: DisasterSkill,
    val explainabilitySkill: ExplainabilitySkill,
    val accessibilitySkill: AccessibilitySkill
) {
    enum class SkillType { MEDICAL, EDUCATION, STEM, ACCESSIBILITY, EXPLAINABILITY, GENERAL, RESEARCH, RESILIENCE, DISASTER }

    data class SkillDefinition(
        val type: SkillType,
        val displayName: String,
        val systemMandate: String,
        val recommendedToolNames: List<String>
    ) {
        /**
         * Helper to get the actual Tool objects for this skill.
         * Used by OODA/ORIGA engines for execution.
         */
        fun getTools(registry: ToolRegistry): List<Tool> {
            return recommendedToolNames.mapNotNull { registry.resolve(it) }
        }
    }

    private val skillDefinitions = mapOf(
        SkillType.MEDICAL to SkillDefinition(
            SkillType.MEDICAL,
            "Medical & Clinical Safety",
            """
            [SYSTEM_MANDATE: MEDICAL]
            You are operating in a clinical safety mode. Your primary responsibility is providing accurate, 
            verified medical triage and pharmacological data based on WHO and local pharmacopeia standards.
            - ALWAYS check for drug interactions if multiple medications are mentioned.
            - ALWAYS provide a medical disclaimer.
            - If symptoms are severe, prioritize immediate triage.
            """.trimIndent(),
            listOf("calculate_clinical_dosage", "check_interaction", "get_first_aid_protocol")
        ),
        SkillType.DISASTER to SkillDefinition(
            SkillType.DISASTER,
            "Disaster Response & SOS",
            """
            [SYSTEM_MANDATE: DISASTER]
            You are operating in an emergency response mode. Your primary mission is saving lives and ensuring safety.
            - BE CONCISE: Use short, clear instructions.
            - PRIORITY: If the user is in immediate danger, use the 'trigger_mesh_sos' tool immediately.
            - STABILITY: Encourage the user to stay calm and follow first-aid steps exactly.
            - OFFLINE: Remind the user that Scypheon works offline via Mesh if cellular is down.
            """.trimIndent(),
            listOf("trigger_mesh_sos", "get_first_aid_protocol")
        ),
        SkillType.EDUCATION to SkillDefinition(
            SkillType.EDUCATION,
            "Education & Pedagogy",
            """
            [SYSTEM_MANDATE: EDUCATION]
            You are a pedagogical expert. Focus on teaching and knowledge transfer.
            - Use the Socratic method: ask guiding questions rather than just giving answers.
            - Simplify complex concepts using analogies.
            - When providing summaries, focus on learning objectives.
            """.trimIndent(),
            listOf("get_lesson_summary", "start_english_tutor")
        ),
        SkillType.STEM to SkillDefinition(
            SkillType.STEM,
            "STEM & Mathematics",
            """
            [SYSTEM_MANDATE: STEM]
            You are a rigorous STEM tutor. Focus on first-principles reasoning.
            - Show your work step-by-step using <thought> tags.
            - Verify mathematical consistency before outputting.
            - Use the calculate_basic tool for any non-trivial math.
            """.trimIndent(),
            listOf("calculate_basic", "math_cheat_sheet")
        ),
        SkillType.RESEARCH to SkillDefinition(
            SkillType.RESEARCH,
            "Research & Discovery",
            """
            [SYSTEM_MANDATE: RESEARCH]
            You are a fact-finding agent. Your mission is to discover objective ground truth.
            - Do NOT guess or hallucinate facts. 
            - Immediately use search tools if you are unsure of real-time data or historical facts.
            - Ground your responses in the citations provided by tools.
            """.trimIndent(),
            listOf("discover_wikipedia", "discover_duckduckgo", "web_fetch")
        ),
        SkillType.ACCESSIBILITY to SkillDefinition(
            SkillType.ACCESSIBILITY,
            "Accessibility & Inclusion",
            """
            [SYSTEM_MANDATE: ACCESSIBILITY]
            Focus on making information accessible to everyone.
            - Simplify language for cognitive disabilities.
            - Format content for dyslexia (clear headers, spacing).
            """.trimIndent(),
            listOf("format_dyslexia")
        ),
        SkillType.GENERAL to SkillDefinition(
            SkillType.GENERAL,
            "General Assistance",
            "Help the user with general tasks and conversation.",
            emptyList()
        )
    )

<<<<<<< Updated upstream
    enum class SkillType { MEDICAL, EDUCATION, STEM, ACCESSIBILITY, EXPLAINABILITY, GENERAL, RESEARCH, DISASTER }

=======
>>>>>>> Stashed changes
    fun getSkill(type: SkillType): SkillDefinition? {
        return skillDefinitions[type]
    }

    /**
     * Helper to resolve tools for a skill type.
     */
    fun getToolsForSkill(type: SkillType): List<Tool> {
        val def = getSkill(type) ?: return emptyList()
        return def.getTools(toolRegistry.get())
    }
}
