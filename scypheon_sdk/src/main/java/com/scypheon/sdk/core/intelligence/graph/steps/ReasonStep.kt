package com.scypheon.sdk.core.intelligence.graph.steps

import kotlinx.coroutines.Dispatchers
import com.scypheon.sdk.core.intelligence.graph.DomainClassifier
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-agnostic entity and sub-query extraction result.
 * Consumed by downstream ORRIGA steps for prompt construction and routing.
 */
data class ReasonOutput(
    val query: String,
    val traceId: String,
    val domain: ReasoningDomain,
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

/**
 * Domain-specific extraction strategy interface.
 */
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
    }

    /**
     * Performs domain-aware task decomposition.
     */
    suspend fun process(query: String, traceId: String): ReasonOutput =
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            Timber.i("[ORRIGA_REASON] Starting decomposition | Trace: $traceId | Query: ${query.take(50)}")

            return@withContext try {
                withTimeout(REASONING_TIMEOUT_MS) {
                    // 1. Classify domain
                    val domain = domainClassifier.classify(query)
                    val strategy = strategies[domain] ?: strategies[ReasoningDomain.GENERAL]
                        ?: throw IllegalStateException("No reasoning strategy available for domain: $domain")

                    // 2. Extract entities
                    val entities = strategy.extractEntities(query)

                    // 3. Generate sub-queries
                    val subQueries = strategy.generateSubQueries(entities, query)

                    val latency = System.currentTimeMillis() - startTime
                    Timber.i("[ORRIGA_REASON] Decomposition complete | Domain: $domain | Entities: ${entities.size}")

                    ReasonOutput(
                        query = query,
                        traceId = traceId,
                        domain = domain,
                        entities = entities,
                        subQueries = subQueries,
                        latencyMs = latency,
                        success = true
                    )
                }
            } catch (e: TimeoutCancellationException) {
                val latency = System.currentTimeMillis() - startTime
                Timber.w("[ORRIGA_REASON] Decomposition timed out | Trace: $traceId")
                ReasonOutput(query, traceId, ReasoningDomain.GENERAL, emptyList(), emptyList(), latency, false, "Timeout")
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                Timber.e(e, "[ORRIGA_REASON] Decomposition failed | Trace: $traceId")
                ReasonOutput(query, traceId, ReasoningDomain.GENERAL, emptyList(), emptyList(), latency, false, e.message)
            }
        }
}
