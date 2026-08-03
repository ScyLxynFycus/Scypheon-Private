package com.scypheon.sdk.core.grounding

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KnowledgeSeeder: Ensures the local grounding database is not empty.
 * This acts as a production-grade fallback if the prebuilt asset is missing.
 */
@Singleton
class KnowledgeSeeder @Inject constructor(
    private val dao: KnowledgeDao
) {
    suspend fun seedIfNeeded() {
        // Simple check: if search for a common term returns nothing, seed basic facts
        val existing = dao.search("paracetamol", 1)
        if (existing.isEmpty()) {
            Timber.i("[KNOWLEDGE_SEEDER] Database empty. Seeding critical humanitarian facts...")
            
            val criticalFacts = listOf(
                KnowledgeEntry("1", "medical", "paracetamol", "500-1000mg every 4-6 hours. Max 4000mg/day.", "WHO Guidelines", 0.95f, System.currentTimeMillis()),
                KnowledgeEntry("2", "medical", "ibuprofen", "200-400mg every 4-6 hours. Take with food.", "FDA Label", 0.95f, System.currentTimeMillis()),
                KnowledgeEntry("3", "resilience", "evacuation", "Follow local emergency protocols. Prioritize vulnerable.", "UN OCHA", 0.90f, System.currentTimeMillis()),
                KnowledgeEntry("4", "resilience", "earthquake", "Drop, Cover, and Hold on. Stay away from windows.", "Red Cross", 0.95f, System.currentTimeMillis()),
                KnowledgeEntry("5", "humanitarian", "logistics", "Prioritize cold chain for vaccines and insulin.", "WFP", 0.85f, System.currentTimeMillis())
            )
            
            criticalFacts.forEach { dao.insert(it) }
            Timber.i("[KNOWLEDGE_SEEDER] Seeding complete. ${criticalFacts.size} facts injected.")
        }
    }
}
