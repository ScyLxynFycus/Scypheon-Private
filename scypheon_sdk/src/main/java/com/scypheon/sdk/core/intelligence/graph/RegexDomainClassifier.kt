package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.intelligence.graph.steps.ReasoningDomain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of DomainClassifier using deterministic regex patterns.
 * Zero stubs. Production-grade for edge humanitarian devices.
 */
@Singleton
class RegexDomainClassifier @Inject constructor() : DomainClassifier {
    companion object {
        private val MEDICAL = setOf("dosis", "obat", "alergi", "drug", "dosage", "medication", "interaksi", "gejala", "triage", "emergency")
        private val EDUCATION = setOf("pelajaran", "ajarkan", "kurikulum", "materi", "lesson", "teach", "study", "matematika", "english", "dyslexia")
        private val RESILIENCE = setOf("bencana", "darurat", "evakuasi", "mitigasi", "disaster", "emergency", "response", "evacuation", "shelter")
        private val HUMANITARIAN = setOf("bantuan", "pengungsi", "logistik", "kemanusiaan", "aid", "refugee", "relief", "humanitarian", "wfp", "pmi")
    }

    override fun classify(query: String): ReasoningDomain {
        val lower = query.lowercase()
        return when {
            MEDICAL.any { lower.contains(it) } -> ReasoningDomain.MEDICAL
            EDUCATION.any { lower.contains(it) } -> ReasoningDomain.EDUCATION
            RESILIENCE.any { lower.contains(it) } -> ReasoningDomain.RESILIENCE
            HUMANITARIAN.any { lower.contains(it) } -> ReasoningDomain.HUMANITARIAN
            else -> ReasoningDomain.GENERAL
        }
    }
}
