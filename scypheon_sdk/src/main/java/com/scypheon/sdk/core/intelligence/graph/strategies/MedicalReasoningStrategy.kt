package com.scypheon.sdk.core.intelligence.graph.strategies

import com.scypheon.sdk.core.intelligence.graph.steps.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalReasoningStrategy @Inject constructor() : DomainReasoningStrategy {
    override val supportedDomain: ReasoningDomain = ReasoningDomain.MEDICAL

    companion object {
        private val RED_FLAG_SYMPTOMS = listOf(
            Regex("(chest pain|nyeri dada).*?(radiating|menjalar|kiri|left)", RegexOption.IGNORE_CASE),
            Regex("(severe hemorrhage|pendarahan hebat|muntah darah|vomiting blood)", RegexOption.IGNORE_CASE),
            Regex("(unconscious|tidak sadar|pingsan|henti napas|no breathing)", RegexOption.IGNORE_CASE),
            Regex("(stroke|face drooping|wajah perot|slurred speech|cadel)", RegexOption.IGNORE_CASE)
        )
    }

    override suspend fun extractEntities(query: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        // Check for Red Flags
        RED_FLAG_SYMPTOMS.forEach { regex ->
            val match = regex.find(query)
            if (match != null) {
                entities.add(ExtractedEntity(match.value, EntityType.SYMPTOM, 1.0f, listOf("RED_FLAG", "TRIAGE_ESCALATION")))
            }
        }

        // Drug names: capitalized multi-word terms
        Regex("\\b[A-Z][a-z]+(?:\\s[A-Z][a-z]+)*\\b").findAll(query).forEach {
            entities.add(ExtractedEntity(it.value, EntityType.DRUG, 0.85f, listOf("pharmacopeia")))
        }

        // Dosages: numeric + unit patterns
        Regex("\\d+\\s*(?:mg|g|ml|tablet|kapsul|times/day)", RegexOption.IGNORE_CASE).findAll(query).forEach {        
            entities.add(ExtractedEntity(it.value, EntityType.PROTOCOL, 0.9f, listOf("dosage_rule")))
        }

        // Add standard symptom extraction if needed (fallback)
        Regex("(fever|demam|headache|pusing|nyeri|pain|cough|batuk|flu|dysentery|disentri|diare|diarrhea)", RegexOption.IGNORE_CASE).findAll(query).forEach {
            if (entities.none { e -> e.text.contains(it.value, ignoreCase = true) }) {
                entities.add(ExtractedEntity(it.value, EntityType.SYMPTOM, 0.8f, emptyList()))
            }
        }

        return entities.distinctBy { it.text.lowercase() }
    }

    override suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery> {
        val subQueries = mutableListOf<SubQuery>()
        val drugs = entities.filter { it.type == EntityType.DRUG }.map { it.text }
        val symptoms = entities.filter { it.type == EntityType.SYMPTOM }

        val hasRedFlag = symptoms.any { it.domainHints.contains("RED_FLAG") }
        if (hasRedFlag) {
            val redFlags = symptoms.filter { it.domainHints.contains("RED_FLAG") }.map { it.text }
            subQueries.add(SubQuery(
                text = "IMMEDIATE TRIAGE REQUIRED for: ${redFlags.joinToString(", ")}. Bypass standard protocols. SLA: 2 seconds.",
                intent = SubQueryIntent.PROCEDURE_LOOKUP,
                priority = 0, // 0 = Absolute highest priority
                dependencies = redFlags
            ))
        }

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

        val normalSymptoms = symptoms.filter { !it.domainHints.contains("RED_FLAG") }.map { it.text }
        if (normalSymptoms.isNotEmpty() && !hasRedFlag) {
            subQueries.add(SubQuery(
                text = "Identify treatment protocols for: ${normalSymptoms.joinToString(", ")}",
                intent = SubQueryIntent.PROCEDURE_LOOKUP,
                priority = 2,
                dependencies = normalSymptoms
            ))
        }

        return subQueries
    }
}
