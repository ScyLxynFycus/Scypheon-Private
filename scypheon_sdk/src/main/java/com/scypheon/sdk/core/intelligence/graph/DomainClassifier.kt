package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.intelligence.graph.steps.ReasoningDomain

interface DomainClassifier {
    suspend fun classify(query: String): Map<ReasoningDomain, Float>
}
