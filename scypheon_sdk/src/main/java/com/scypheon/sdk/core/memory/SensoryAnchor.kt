package com.scypheon.sdk.core.memory

import java.util.UUID

/**
 * SensoryAnchor: The unified data contract for all sensory inputs.
 * Ensures structured ingestion into the Knowledge Graph with Zero-Knowledge support.
 */
data class SensoryAnchor(
    val id: UUID = UUID.randomUUID(),
    val timestamp: Long = System.currentTimeMillis(),
    val modality: Modality,
    val entities: List<SensoryEntity>,
    val context: String, // e.g. "kitchen", "street", "conversation"
    val confidence: Float,
    val isEncrypted: Boolean = true
)

enum class Modality {
    VISION,
    AUDIO,
    KINETIC,
    TEXT
}

data class SensoryEntity(
    val type: String, // e.g. "person", "object", "event"
    val name: String,
    val attribute: String? = null // e.g. "happy", "red", "falling"
)

/**
 * Helper to convert a SensoryAnchor into Knowledge Triplets for GraphRAG.
 */
fun SensoryAnchor.toTriplets(): List<Triple<String, String, String>> {
    return entities.map { entity ->
        Triple(
            context.lowercase(), 
            "contains_${entity.type}".lowercase(), 
            entity.name.lowercase()
        )
    }
}
