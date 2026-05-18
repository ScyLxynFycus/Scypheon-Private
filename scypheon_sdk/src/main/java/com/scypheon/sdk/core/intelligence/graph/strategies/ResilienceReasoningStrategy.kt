package com.scypheon.sdk.core.intelligence.graph.strategies

import com.scypheon.sdk.core.intelligence.graph.steps.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResilienceReasoningStrategy @Inject constructor() : DomainReasoningStrategy {
    override val supportedDomain: ReasoningDomain = ReasoningDomain.RESILIENCE

    override suspend fun extractEntities(query: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        // Disaster types
        val disasters = setOf("gempa", "banjir", "kebakaran", "tsunami", "earthquake", "flood", "fire")
        disasters.forEach { type ->
            if (query.contains(type, ignoreCase = true)) {
                entities.add(ExtractedEntity(type, EntityType.CONCEPT, 0.95f, listOf("disaster_protocol")))
            }
        }

        // Critical resources
        val resources = setOf("air bersih", "listrik", "shelter", "tenda", "water", "power", "tent")
        resources.forEach { resource ->
            if (query.contains(resource, ignoreCase = true)) {
                entities.add(ExtractedEntity(resource, EntityType.LOCATION, 0.8f, listOf("resource_map")))
            }
        }

        return entities.distinctBy { it.text.lowercase() }
    }

    override suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery> {
        return entities.map { entity ->
            SubQuery(
                text = "Retrieve emergency mitigation protocol for ${entity.text}",
                intent = SubQueryIntent.FACT_RETRIEVAL,
                priority = 1,
                dependencies = listOf(entity.text)
            )
        }
    }
}
