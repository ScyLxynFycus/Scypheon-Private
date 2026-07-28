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
            Timber.i("[KNOWLEDGE_SEEDER] Database empty. Seeding critical humanitarian and educational facts...")
            
            val criticalFacts = listOf(
                KnowledgeEntry("1", "medical", "paracetamol", "500-1000mg every 4-6 hours. Max 4000mg/day.", "WHO Guidelines", 0.95f, System.currentTimeMillis()),
                KnowledgeEntry("2", "medical", "ibuprofen", "200-400mg every 4-6 hours. Take with food.", "FDA Label", 0.95f, System.currentTimeMillis()),
                KnowledgeEntry("3", "resilience", "evacuation", "Follow local emergency protocols. Prioritize vulnerable.", "UN OCHA", 0.90f, System.currentTimeMillis()),
                KnowledgeEntry("4", "resilience", "earthquake", "Drop, Cover, and Hold on. Stay away from windows.", "Red Cross", 0.95f, System.currentTimeMillis()),
                KnowledgeEntry("5", "humanitarian", "logistics", "Prioritize cold chain for vaccines and insulin.", "WFP", 0.85f, System.currentTimeMillis())
            )
            
            val educationFacts = getEducationSeedData()
            val allFacts = criticalFacts + educationFacts
            
            allFacts.forEach { dao.insert(it) }
            Timber.i("[KNOWLEDGE_SEEDER] Seeding complete. ${allFacts.size} facts injected.")
        }
    }

    private fun getEducationSeedData(): List<KnowledgeEntry> {
        return listOf(
            KnowledgeEntry(
                id = "math_pythagoras",
                domain = "math",
                term = "pythagoras",
                content = """
                    {
                      "concept": "Pythagorean Theorem",
                      "latex": "a^2 + b^2 = c^2",
                      "explanation": "In a right-angled triangle, the square of the hypotenuse is equal to the sum of the squares of the other two sides.",
                      "canvasInstructions": [
                        {"type": "LINE", "startX": 0.1, "startY": 0.8, "endX": 0.1, "endY": 0.2, "colorHex": "#FFFFFF"},
                        {"type": "LINE", "startX": 0.1, "startY": 0.8, "endX": 0.7, "endY": 0.8, "colorHex": "#FFFFFF"},
                        {"type": "LINE", "startX": 0.1, "startY": 0.2, "endX": 0.7, "endY": 0.8, "colorHex": "#FF0000"},
                        {"type": "TEXT", "startX": 0.05, "startY": 0.5, "colorHex": "#00FF00"},
                        {"type": "TEXT", "startX": 0.4, "startY": 0.85, "colorHex": "#00FF00"}
                      ]
                    }
                """.trimIndent(),
                source = "Euclidean Geometry",
                confidence = 1.0f,
                lastUpdated = System.currentTimeMillis()
            ),
            KnowledgeEntry(
                id = "chem_carbon",
                domain = "chemistry",
                term = "carbon",
                content = """
                    {
                      "symbol": "C",
                      "atomicNumber": 6,
                      "atomicMass": 12.011,
                      "group": 14,
                      "period": 2,
                      "category": "Polyatomic nonmetal",
                      "electronConfiguration": "1s2 2s2 2p2"
                    }
                """.trimIndent(),
                source = "IUPAC Periodic Table",
                confidence = 1.0f,
                lastUpdated = System.currentTimeMillis()
            ),
            KnowledgeEntry(
                id = "phys_speed_of_light",
                domain = "physics",
                term = "speed of light",
                content = """
                    {
                      "constant": "Speed of Light in Vacuum",
                      "symbol": "c",
                      "value": 299792458,
                      "unit": "m/s",
                      "uncertainty": "exact",
                      "latex": "c = 2.99792458 \\times 10^8 \\text{ m/s}"
                    }
                """.trimIndent(),
                source = "CODATA 2018",
                confidence = 1.0f,
                lastUpdated = System.currentTimeMillis()
            ),
            KnowledgeEntry(
                id = "bio_homo_sapiens",
                domain = "biology",
                term = "human",
                content = """
                    {
                      "scientificName": "Homo sapiens",
                      "kingdom": "Animalia",
                      "phylum": "Chordata",
                      "class": "Mammalia",
                      "order": "Primates",
                      "family": "Hominidae",
                      "genus": "Homo",
                      "species": "sapiens"
                    }
                """.trimIndent(),
                source = "Integrated Taxonomic Information System",
                confidence = 1.0f,
                lastUpdated = System.currentTimeMillis()
            ),
            KnowledgeEntry(
                id = "hist_wwii",
                domain = "history",
                term = "WWII",
                content = """
                    {
                      "timeline": "September 1, 1939: Germany invades Poland, starting WWII in Europe. December 7, 1941: Pearl Harbor attacked. June 6, 1944: D-Day. May 8, 1945: VE Day. September 2, 1945: Japan signs formal surrender."
                    }
                """.trimIndent(),
                source = "National WWII Museum",
                confidence = 1.0f,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}
