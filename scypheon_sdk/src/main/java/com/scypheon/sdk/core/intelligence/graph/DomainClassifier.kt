package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.intelligence.graph.steps.ReasoningDomain

interface DomainClassifier {
<<<<<<< Updated upstream
    suspend fun classify(query: String): ReasoningDomain
=======
    suspend fun classify(query: String): Map<ReasoningDomain, Float>
>>>>>>> Stashed changes
}
