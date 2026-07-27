package com.scypheon.sdk.core.intelligence.graph.steps

import com.scypheon.sdk.core.grounding.MedicalGroundingEngine
import com.scypheon.sdk.core.intelligence.graph.TimeBudget
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class InvestigationResult(
    val traceId: String,
    val facts: List<String>,
    val latencyMs: Long,
    val success: Boolean,
    val failureReason: String? = null,
    val groundingDetails: Map<String, Float> = emptyMap(),
    val isDegraded: Boolean = false
)

@Singleton
class InvestigateStep @Inject constructor(
    private val groundingEngine: MedicalGroundingEngine,
    private val circuitBreaker: ResilienceCircuitBreaker
) {
    companion object {
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f
    }

    suspend fun process(entities: List<String>, traceId: String, domain: String = "medical", timeBudget: TimeBudget): InvestigationResult =   
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            Timber.i("[ORRIGA_INVESTIGATE] Starting grounding | Trace: `$traceId | Entities: `${entities.size}")        

            if (timeBudget.isExpired()) {
                Timber.w("[ORRIGA_INVESTIGATE] Time budget expired before start | Trace: `$traceId")
                return@withContext InvestigationResult(
                    traceId = traceId,
                    facts = emptyList(),
                    latencyMs = 0,
                    success = false,
                    failureReason = "Time budget expired (< 500ms remaining)",
                    isDegraded = true
                )
            }

            return@withContext try {
                val budgetMs = timeBudget.remaining()
                circuitBreaker.execute("database_investigate") {
                    withTimeout(budgetMs) {
                        val results = coroutineScope {
                            entities.map { entity ->
                                async {
                                    val result = groundingEngine.verify(entity, domain)
                                    entity to result
                                }
                            }.awaitAll()
                        }

                        val facts = mutableListOf<String>()
                        val groundingDetails = mutableMapOf<String, Float>()

                        results.forEach { (entity, result) ->
                            groundingDetails[entity] = result.confidence
                            if (result.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                                result.sources.forEach { source ->
                                    facts.add("[$domain] `$entity: `$source")
                                }
                            }
                        }

                        val latency = System.currentTimeMillis() - startTime
                        Timber.i("[ORRIGA_INVESTIGATE] Grounding complete | Facts: `${facts.size} | Latency: `${latency}ms")

                        InvestigationResult(
                            traceId = traceId,
                            facts = facts,
                            latencyMs = latency,
                            success = true,
                            groundingDetails = groundingDetails
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val latency = System.currentTimeMillis() - startTime
                Timber.w("[ORRIGA_INVESTIGATE] Investigation timed out | Trace: `$traceId")
                InvestigationResult(
                    traceId = traceId,
                    facts = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = "Investigation exceeded budget",
                    isDegraded = true
                )
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                Timber.e(e, "[ORRIGA_INVESTIGATE] Investigation failed | Trace: `$traceId")
                InvestigationResult(
                    traceId = traceId,
                    facts = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = e.message ?: "Unknown investigation failure",
                    isDegraded = true
                )
            }
        }
}
