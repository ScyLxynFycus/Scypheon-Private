package com.scypheon.sdk.core.intelligence.graph.strategies

import com.scypheon.sdk.core.intelligence.graph.steps.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralReasoningStrategy @Inject constructor() : DomainReasoningStrategy {
    override val supportedDomain: ReasoningDomain = ReasoningDomain.GENERAL

    override suspend fun extractEntities(query: String): List<ExtractedEntity> {
        // Fallback: extract capitalized terms
        return Regex("\\b[A-Z][a-z]+\\b").findAll(query).map {
            ExtractedEntity(it.value, EntityType.CONCEPT, 0.5f)
        }.toList()
    }

    override suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery> {
        return listOf(SubQuery(
            text = "Process general query: $query",
            intent = SubQueryIntent.FACT_RETRIEVAL,
            priority = 1
        ))
    }
}
