package com.scypheon.sdk.core.agent.skills

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgentSkillRegistry:
 * The central hub that defines the capabilities (Skills) and their specific tools.
 * 
 * Tool Distinction:
 * - FastTools: Lightweight function calls used by OODA (< 200ms).
 * - DeepTools: Heavy, multi-hop investigation tools used by ORIGA.
 */
@Singleton
class AgentSkillRegistry @Inject constructor(
    val medicalSkill: MedicalSkill,
    val tutorSkill: TutorSkill,
    val mathSkill: MathSkill,
    val explainabilitySkill: ExplainabilitySkill,
    val accessibilitySkill: AccessibilitySkill
) {
    data class ToolConstraintProfile(val powerCost: Int, val thermalImpact: Int, val requiresNetwork: Boolean)
    /**
     * ParameterRule defines the validation logic for tool arguments.
     */
    data class ParameterRule(
        val required: Boolean, 
        val type: String, 
        val description: String = "",
        val defaultValue: String? = null
    )

    data class FastTool(
        val name: String, 
        val description: String,
        val keywords: List<String>,
        val isMedical: Boolean,
        val constraintProfile: ToolConstraintProfile,
        val parameterSchema: Map<String, ParameterRule> = emptyMap()
    )
    data class DeepTool(val name: String, val description: String)

    data class SkillDefinition(
        val type: SkillType,
        val description: String,
        val fastTools: List<FastTool>,
        val deepTools: List<DeepTool>
    )

    private val skills = listOf(
        SkillDefinition(
            SkillType.MEDICAL, 
            "Medical triage, drug safety, and health diagnostics.", 
            listOf(
                FastTool(
                    "get_drug_dosage", 
                    "Ambil dosis obat dari Farmakope lokal", 
                    listOf("dosis", "aturan pakai", "dosage", "obat"),
                    isMedical = true,
                    ToolConstraintProfile(powerCost = 1, thermalImpact = 1, requiresNetwork = false),
                    mapOf("drug" to ParameterRule(true, "String"), "ageGroup" to ParameterRule(false, "String", defaultValue = "adult"))
                ), 
                FastTool(
                    "check_interaction", 
                    "Cek interaksi obat dari SQL log",
                    listOf("alergi", "allergy", "interaksi"),
                    isMedical = true,
                    ToolConstraintProfile(powerCost = 1, thermalImpact = 1, requiresNetwork = false),
                    mapOf("drug" to ParameterRule(true, "String"))
                ),
                FastTool(
                    "get_first_aid", 
                    "Dapatkan panduan pertolongan pertama untuk gejala tertentu",
                    listOf("pertolongan", "first aid", "luka", "darurat"),
                    isMedical = true,
                    ToolConstraintProfile(powerCost = 1, thermalImpact = 1, requiresNetwork = false),
                    mapOf("symptom" to ParameterRule(true, "String"))
                )
            ),
            listOf(DeepTool("oracle_investigate", "Telusuri interaksi obat multi-hop"))
        ),
        SkillDefinition(
            SkillType.EDUCATION, 
            "General teaching, lesson planning, and academic mentorship.", 
            listOf(
                FastTool("get_lesson_summary", "Ambil ringkasan materi", listOf("ringkasan", "materi"), false, ToolConstraintProfile(2, 2, false), mapOf("topic" to ParameterRule(true, "String"))),
                FastTool("start_english_tutor", "Aktifkan tutor Bahasa Inggris interaktif", listOf("belajar", "inggris", "english"), false, ToolConstraintProfile(3, 2, false))
            ),
            listOf(DeepTool("build_curriculum_graph", "Bangun kurikulum socratic"))
        ),
        SkillDefinition(
            SkillType.STEM, 
            "Complex mathematical reasoning and Socratic STEM tutoring.", 
            listOf(FastTool("calculate_basic", "Kalkulasi matematis dasar", listOf("hitung", "kalkulasi"), false, ToolConstraintProfile(1, 1, false), mapOf("expression" to ParameterRule(true, "String")))),
            listOf(DeepTool("prove_theorem", "Penyelesaian persamaan kompleks"))
        ),
        SkillDefinition(
            SkillType.ACCESSIBILITY, 
            "Inclusive formatting and content simplification for dyslexia.", 
            listOf(FastTool("format_dyslexia", "Ubah teks jadi ramah disleksia", listOf("disleksia", "format"), false, ToolConstraintProfile(1, 1, false), mapOf("text" to ParameterRule(true, "String")))),
            listOf(DeepTool("analyze_readability", "Audit aksesibilitas dokumen"))
        ),
        SkillDefinition(
            SkillType.RESEARCH,
            "External knowledge discovery via Wikipedia, DuckDuckGo, and OpenFDA.",
            listOf(
                FastTool(
                    "discover_wikipedia",
                    "Cari ringkasan ensiklopedia dari Wikipedia",
                    listOf("wiki", "wikipedia", "apa itu", "siapa"),
                    false,
                    ToolConstraintProfile(2, 2, true),
                    mapOf("query" to ParameterRule(true, "String"))
                ),
                FastTool(
                    "discover_duckduckgo",
                    "Cari informasi instan dari DuckDuckGo",
                    listOf("cari", "search", "duckduckgo", "ddg"),
                    false,
                    ToolConstraintProfile(2, 2, true),
                    mapOf("query" to ParameterRule(true, "String"))
                ),
                FastTool(
                    "discover_openfda",
                    "Cari data keamanan obat resmi dari OpenFDA/WHO",
                    listOf("fda", "who", "keamanan obat", "drug safety"),
                    true,
                    ToolConstraintProfile(3, 2, true),
                    mapOf("drug" to ParameterRule(true, "String"))
                )
            ),
            listOf(DeepTool("web_crawl_fandom", "Telusuri wiki komunitas Fandom untuk detail mendalam"))
        )
    )

    enum class SkillType { MEDICAL, EDUCATION, STEM, ACCESSIBILITY, EXPLAINABILITY, GENERAL, RESEARCH, DISASTER }

    fun getSkill(type: SkillType): SkillDefinition? {
        return skills.find { it.type == type }
    }
}
