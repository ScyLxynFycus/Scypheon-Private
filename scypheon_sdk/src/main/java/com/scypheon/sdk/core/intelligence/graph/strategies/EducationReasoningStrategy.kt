package com.scypheon.sdk.core.intelligence.graph.strategies

import com.scypheon.sdk.core.intelligence.graph.steps.*
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EducationReasoningStrategy @Inject constructor(
    @ApplicationContext private val context: Context
) : DomainReasoningStrategy {
    override val supportedDomain: ReasoningDomain = ReasoningDomain.EDUCATION

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("education_prefs", Context.MODE_PRIVATE)
    }

    override suspend fun extractEntities(query: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        val subjects = setOf("matematika", "biologi", "sejarah", "physics", "chemistry", "literature", "english")     
        subjects.forEach { subject ->
            if (query.contains(subject, ignoreCase = true)) {
                entities.add(ExtractedEntity(subject, EntityType.CONCEPT, 0.9f, listOf("curriculum")))
            }
        }

        Regex("\\b(SD|SMP|SMA|kelas\\s*\\d+|grade\\s*\\d+)\\b", RegexOption.IGNORE_CASE).findAll(query).forEach {     
            entities.add(ExtractedEntity(it.value, EntityType.PROTOCOL, 0.8f, listOf("level_filter")))
        }

        return entities.distinctBy { it.text.lowercase() }
    }

    override suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery> {
        val concepts = entities.filter { it.type == EntityType.CONCEPT }.map { it.text }
        val levels = entities.filter { it.type == EntityType.PROTOCOL }.map { it.text }
        val isDyslexic = prefs.getBoolean("dyslexia_flag", false)
        val gradeLevel = prefs.getString("grade_level", "SMA") ?: "SMA"
        
        val subQueries = concepts.map { concept ->
            SubQuery(
                text = "Retrieve learning objectives for $concept",
                intent = SubQueryIntent.FACT_RETRIEVAL,
                priority = 1,
                dependencies = listOf(concept) + levels
            )
        }.toMutableList()
        
        if (isDyslexic || gradeLevel.contains("SD", ignoreCase = true) || gradeLevel.contains("kelas 1", ignoreCase = true) || gradeLevel.contains("kelas 2", ignoreCase = true)) {
            subQueries.add(
                SubQuery(
                    text = "Apply Simplification & Readability formatting. Use short sentences, high-contrast markers, and avoid complex jargon.",
                    intent = SubQueryIntent.CLARIFICATION,
                    priority = 0,
                    dependencies = emptyList()
                )
            )
        }

        return subQueries
    }
}
