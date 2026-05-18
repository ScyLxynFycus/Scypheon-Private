package com.scypheon.sdk.core.intelligence.graph.strategies

import com.scypheon.sdk.core.intelligence.graph.steps.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HumanitarianReasoningStrategy @Inject constructor() : DomainReasoningStrategy {
    override val supportedDomain: ReasoningDomain = ReasoningDomain.HUMANITARIAN

    override suspend fun extractEntities(query: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        // Aid categories
        val aidTypes = setOf("logistik", "makanan", "obat-obatan", "donasi", "food", "medical aid", "logistics")
        aidTypes.forEach { type ->
            if (query.contains(type, ignoreCase = true)) {
                entities.add(ExtractedEntity(type, EntityType.CONCEPT, 0.9f, listOf("aid_inventory")))
            }
        }

        // Organizations/Locations
        val orgs = setOf("PMI", "UNHCR", "WFP", "posko", "camp")
        orgs.forEach { org ->
            if (query.contains(org, ignoreCase = true)) {
                entities.add(ExtractedEntity(org, EntityType.LOCATION, 0.85f, listOf("org_contact")))
            }
        }

        return entities.distinctBy { it.text.lowercase() }
    }

    override suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery> {
        return entities.map { entity ->
            SubQuery(
                text = "Track logistics status for ${entity.text}",
                intent = SubQueryIntent.FACT_RETRIEVAL,
                priority = 2,
                dependencies = listOf(entity.text)
            )
        }
    }
}
