package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.intelligence.graph.steps.ReasoningDomain

/**
 * Interface for classifying the domain of a natural language query.
 * Used by the ORRIGA pipeline to route requests to specialized reasoning strategies.
 */
interface DomainClassifier {
    suspend fun classify(query: String): ReasoningDomain
}
