package com.scypheon.sdk.core.intelligence.graph.strategies

import com.scypheon.sdk.core.intelligence.graph.steps.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalReasoningStrategy @Inject constructor() : DomainReasoningStrategy {
    override val supportedDomain: ReasoningDomain = ReasoningDomain.MEDICAL

    override suspend fun extractEntities(query: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        // Drug names: capitalized multi-word terms
        Regex("\\b[A-Z][a-z]+(?:\\s[A-Z][a-z]+)*\\b").findAll(query).forEach {
            entities.add(ExtractedEntity(it.value, EntityType.DRUG, 0.85f, listOf("pharmacopeia")))
        }

        // Dosages: numeric + unit patterns
        Regex("\\d+\\s*(?:mg|g|ml|tablet|kapsul|times/day)", RegexOption.IGNORE_CASE).findAll(query).forEach {
            entities.add(ExtractedEntity(it.value, EntityType.PROTOCOL, 0.9f, listOf("dosage_rule")))
        }

        return entities.distinctBy { it.text.lowercase() }
    }

    override suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery> {
        val subQueries = mutableListOf<SubQuery>()
        val drugs = entities.filter { it.type == EntityType.DRUG }.map { it.text }

        drugs.forEach { drug ->
            subQueries.add(SubQuery(
                text = "Retrieve dosage protocol for $drug",
                intent = SubQueryIntent.FACT_RETRIEVAL,
                priority = 1,
                dependencies = listOf(drug)
            ))
        }

        if (drugs.size >= 2) {
            subQueries.add(SubQuery(
                text = "Check interaction between ${drugs[0]} and ${drugs[1]}",
                intent = SubQueryIntent.INTERACTION_CHECK,
                priority = 2,
                dependencies = drugs
            ))
        }

        return subQueries
    }
}
