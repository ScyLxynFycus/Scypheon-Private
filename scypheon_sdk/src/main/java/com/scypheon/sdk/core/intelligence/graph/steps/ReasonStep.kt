package com.scypheon.sdk.core.intelligence.graph.steps

import kotlinx.coroutines.Dispatchers
import com.scypheon.sdk.core.intelligence.graph.DomainClassifier
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class ReasonOutput(
    val query: String,
    val traceId: String,
    val domains: List<ReasoningDomain>,
    val domainScores: Map<ReasoningDomain, Float>,
    val entities: List<ExtractedEntity>,
    val subQueries: List<SubQuery>,
    val latencyMs: Long,
    val success: Boolean,
    val failureReason: String? = null
)

data class ExtractedEntity(
    val text: String,
    val type: EntityType,
    val confidence: Float,
    val domainHints: List<String> = emptyList()
)

data class SubQuery(
    val text: String,
    val intent: SubQueryIntent,
    val priority: Int,
    val dependencies: List<String> = emptyList()
)

enum class ReasoningDomain { MEDICAL, EDUCATION, RESILIENCE, HUMANITARIAN, GENERAL }
enum class EntityType { DRUG, SYMPTOM, PROTOCOL, LOCATION, RESOURCE, CONCEPT, PERSON, ORGANIZATION }
enum class SubQueryIntent { FACT_RETRIEVAL, INTERACTION_CHECK, PROCEDURE_LOOKUP, RESOURCE_MAPPING, CLARIFICATION }    

interface DomainReasoningStrategy {
    val supportedDomain: ReasoningDomain
    suspend fun extractEntities(query: String): List<ExtractedEntity>
    suspend fun generateSubQueries(entities: List<ExtractedEntity>, query: String): List<SubQuery>
}

@Singleton
class ReasonStep @Inject constructor(
    private val strategies: Map<ReasoningDomain, @JvmSuppressWildcards DomainReasoningStrategy>,
    private val domainClassifier: DomainClassifier
) {
    companion object {
        private const val REASONING_TIMEOUT_MS = 2000L
        private const val DOMAIN_FUSION_THRESHOLD = 0.6f
    }

    suspend fun process(query: String, traceId: String): ReasonOutput =
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            Timber.i("[ORRIGA_REASON] Starting decomposition | Trace: `$traceId | Query: `${query.take(50)}")

            return@withContext try {
                withTimeout(REASONING_TIMEOUT_MS) {
                    val domainScores = domainClassifier.classify(query)
                    val activeDomains = domainScores.filter { it.value >= DOMAIN_FUSION_THRESHOLD }.keys.toList()
                        .ifEmpty { listOf(domainScores.maxByOrNull { it.value }?.key ?: ReasoningDomain.GENERAL) }

                    val activeStrategies = activeDomains.mapNotNull { strategies[it] }

                    val entities = mutableListOf<ExtractedEntity>()
                    val subQueries = mutableListOf<SubQuery>()

                    if (activeStrategies.isEmpty()) {
                        // Fallback
                        val generalStrategy = strategies[ReasoningDomain.GENERAL]
                        if (generalStrategy != null) {
                            entities.addAll(generalStrategy.extractEntities(query))
                            subQueries.addAll(generalStrategy.generateSubQueries(entities, query))
                        }
                    } else {
                        // Fusion Execution
                        coroutineScope {
                            val results = activeStrategies.map { strategy ->
                                async {
                                    val strategyEntities = strategy.extractEntities(query)
                                    val strategySubQueries = strategy.generateSubQueries(strategyEntities, query)
                                    Pair(strategyEntities, strategySubQueries)
                                }
                            }.awaitAll()

                            results.forEach { (strEntities, strSubQueries) ->
                                entities.addAll(strEntities)
                                subQueries.addAll(strSubQueries)
                            }
                        }
                    }
                    
                    // Priority 0 means absolute highest priority, escalate!
                    val isTriageEscalation = subQueries.any { it.priority == 0 }
                    if (isTriageEscalation) {
                         Timber.e("圷 [ORRIGA_REASON] RED FLAG ESCALATION TRIGGERED! Bypass limits active.")
                    }

                    val latency = System.currentTimeMillis() - startTime
                    Timber.i("[ORRIGA_REASON] Decomposition complete in `${latency}ms | Domains: `$activeDomains")

                    ReasonOutput(
                        query = query,
                        traceId = traceId,
                        domains = activeDomains,
                        domainScores = domainScores,
                        entities = entities.distinctBy { it.text.lowercase() },
                        subQueries = subQueries,
                        latencyMs = latency,
                        success = true
                    )
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                Timber.e(e, "[ORRIGA_REASON] Decomposition failed | Trace: `$traceId")
                ReasonOutput(
                    query = query,
                    traceId = traceId,
                    domains = emptyList(),
                    domainScores = emptyMap(),
                    entities = emptyList(),
                    subQueries = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = e.message
                )
            }
        }
}
