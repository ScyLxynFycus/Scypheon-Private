package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.intelligence.graph.steps.ReasoningDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegexDomainClassifier @Inject constructor() : DomainClassifier {
    companion object {
        private val MEDICAL = setOf("dosis", "obat", "alergi", "drug", "dosage", "medication", "interaksi", "gejala", "triage", "emergency", "dysentery", "disentri")
        private val EDUCATION = setOf("pelajaran", "ajarkan", "kurikulum", "materi", "lesson", "teach", "study", "matematika", "english", "dyslexia")    
        private val RESILIENCE = setOf("bencana", "darurat", "evakuasi", "mitigasi", "disaster", "emergency", "response", "evacuation", "shelter", "floodwater", "purification", "banjir")
        private val HUMANITARIAN = setOf("bantuan", "pengungsi", "logistik", "kemanusiaan", "aid", "refugee", "relief", "humanitarian", "wfp", "pmi")    
    }

<<<<<<< Updated upstream
    override suspend fun classify(query: String): ReasoningDomain {
=======
    override suspend fun classify(query: String): Map<ReasoningDomain, Float> {
>>>>>>> Stashed changes
        val lower = query.lowercase()
        val scores = mutableMapOf<ReasoningDomain, Float>()

        val medHits = MEDICAL.count { lower.contains(it) }
        if (medHits > 0) scores[ReasoningDomain.MEDICAL] = if (medHits > 1) 0.8f else 0.65f

        val eduHits = EDUCATION.count { lower.contains(it) }
        if (eduHits > 0) scores[ReasoningDomain.EDUCATION] = if (eduHits > 1) 0.8f else 0.65f

        val resHits = RESILIENCE.count { lower.contains(it) }
        if (resHits > 0) scores[ReasoningDomain.RESILIENCE] = if (resHits > 1) 0.8f else 0.65f

        val humHits = HUMANITARIAN.count { lower.contains(it) }
        if (humHits > 0) scores[ReasoningDomain.HUMANITARIAN] = if (humHits > 1) 0.8f else 0.65f

        if (scores.isEmpty()) {
            scores[ReasoningDomain.GENERAL] = 1.0f
        }

        return scores
    }
}
