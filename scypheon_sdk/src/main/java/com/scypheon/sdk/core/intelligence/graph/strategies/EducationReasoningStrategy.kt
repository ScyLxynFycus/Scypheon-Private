package com.scypheon.sdk.core.intelligence.graph.strategies

import com.scypheon.sdk.core.intelligence.graph.steps.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EducationReasoningStrategy @Inject constructor() : DomainReasoningStrategy {
    override val supportedDomain: ReasoningDomain = ReasoningDomain.EDUCATION

    override suspend fun extractEntities(query: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        // Subjects/concepts
        val subjects = setOf("matematika", "biologi", "sejarah", "physics", "chemistry", "literature", "english")
        subjects.forEach { subject ->
            if (query.contains(subject, ignoreCase = true)) {
                entities.add(ExtractedEntity(subject, EntityType.CONCEPT, 0.9f, listOf("curriculum")))
            }
        }

        // Grade levels
        Regex("\\b(SD|SMP|SMA|kelas\\s*\\d+|grade\\s*\\d+)\\b", RegexOption.IGNORE_CASE).findAll(query).forEach {
            entities.add(ExtractedEntity(it.value, EntityType.PROTOCOL, 0.8f, listOf("level_filter")))
        }

        return entities.distinctBy { it.text.lowercase() }
    }

    override suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery> {
        val concepts = entities.filter { it.type == EntityType.CONCEPT }.map { it.text }
        val levels = entities.filter { it.type == EntityType.PROTOCOL }.map { it.text }

        return concepts.map { concept ->
            SubQuery(
                text = "Retrieve learning objectives for $concept",
                intent = SubQueryIntent.FACT_RETRIEVAL,
                priority = 1,
                dependencies = listOf(concept) + levels
            )
        }
    }
}
