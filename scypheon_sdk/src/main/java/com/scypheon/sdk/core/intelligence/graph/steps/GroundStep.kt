package com.scypheon.sdk.core.intelligence.graph.steps

import com.scypheon.sdk.core.intelligence.graph.KnowledgeGuardImpl
import com.scypheon.sdk.core.intelligence.graph.ValidationOutcome
import com.scypheon.sdk.core.intelligence.graph.ValidationLevel
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

/**
 * Structured grounding result for pipeline consumption and audit.
 */
data class GroundingResult(
    val traceId: String,
    val validatedFacts: List<String>,
    val rejectedFacts: List<ValidationOutcome>,
    val latencyMs: Long,
    val success: Boolean,
    val failureReason: String? = null
)

@Singleton
class GroundStep @Inject constructor(
    private val knowledgeGuard: KnowledgeGuardImpl
) {
    companion object {
        private const val GROUNDING_TIMEOUT_MS = 3000L
    }

    /**
     * Validates factual integrity for a list of claims.
     * Returns structured result regardless of success/failure.
     */
    suspend fun process(facts: List<String>, traceId: String, level: ValidationLevel = ValidationLevel.INVESTIGATION): GroundingResult =
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            Timber.i("[ORRIGA_GROUND] Starting factual validation | Trace: $traceId | Facts: ${facts.size}")

            return@withContext try {
                withTimeout(GROUNDING_TIMEOUT_MS) {
                    // Parallel validation with structured concurrency
                    val outcomes = coroutineScope {
                        facts.map { fact ->
                            async { knowledgeGuard.validate(fact, level) }
                        }.awaitAll()
                    }

                    // Separate validated vs rejected facts
                    val validatedFacts = outcomes.filter { it.isValid }.map { it.fact }
                    val rejectedFacts = outcomes.filter { !it.isValid }

                    val latency = System.currentTimeMillis() - startTime
                    Timber.i("[ORRIGA_GROUND] Validation complete | Valid: ${validatedFacts.size} | Rejected: ${rejectedFacts.size} | Latency: ${latency}ms")

                    GroundingResult(
                        traceId = traceId,
                        validatedFacts = validatedFacts,
                        rejectedFacts = rejectedFacts,
                        latencyMs = latency,
                        success = true
                    )
                }
            } catch (e: TimeoutCancellationException) {
                val latency = System.currentTimeMillis() - startTime
                Timber.w("[ORRIGA_GROUND] Validation timed out | Trace: $traceId")
                GroundingResult(
                    traceId = traceId,
                    validatedFacts = emptyList(),
                    rejectedFacts = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = "Grounding exceeded ${GROUNDING_TIMEOUT_MS}ms timeout"
                )
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                Timber.e(e, "[ORRIGA_GROUND] Validation failed | Trace: $traceId")
                GroundingResult(
                    traceId = traceId,
                    validatedFacts = emptyList(),
                    rejectedFacts = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = e.message ?: "Unknown validation failure"
                )
            }
        }
}
