package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.Observation
import com.scypheon.sdk.core.agent.ooda.Orientation
import com.scypheon.sdk.core.agent.ooda.AuditLogger
import com.scypheon.sdk.core.intelligence.graph.steps.*
import com.scypheon.sdk.core.safety.helios.ViolationCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class OrrigaConfig(
    val pipelineTimeoutMs: Long = 8000L,
    val enableAuditLogging: Boolean = true,
    val fallbackMessage: String = "[SYSTEM] Deep reasoning unavailable. Safe fallback activated."
)

data class ContextMetadata(
    val domain: String,
    val timestamp: Long
) {
    fun toFactString(): String = "[METADATA] domain=$domain, collected_at=$timestamp"
}

interface HybridGraphOrrigaEngine {
    fun reason(
        query: String,
        observation: Observation,
        orientation: Orientation,
        environment: DeviceEnvironment
    ): Flow<String>
}

@Singleton
class HybridGraphOrrigaEngineImpl @Inject constructor(
    private val reflectStep: ReflectStep,
    private val reasonStep: ReasonStep,
    private val investigateStep: InvestigateStep,
    private val groundStep: GroundStep,
    private val answerStep: AnswerStep,
    private val auditLogger: AuditLogger,
    private val config: OrrigaConfig
) : HybridGraphOrrigaEngine {

    override fun reason(
        query: String,
        observation: Observation,
        orientation: Orientation,
        environment: DeviceEnvironment
    ): Flow<String> = flow {
        val traceId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        val stepsCompleted = mutableListOf<String>()

        Timber.i("[ORRIGA_MAIN] Pipeline started | Trace: `$traceId | Query: `${query.take(100)}")

        try {
            if (config.enableAuditLogging) {
                auditLogger.logPipelineStart(traceId, "ORRIGA_DEEP_PATH")
            }

            val timeBudget = TimeBudget(config.pipelineTimeoutMs)

            withTimeout(config.pipelineTimeoutMs) {
                // 1. REFLECT: Semantic Memory Retrieval
                val reflection = reflectStep.process(observation.sessionId, traceId, timeBudget)
                if (reflection.isDegraded && config.enableAuditLogging) {
                    auditLogger.logDegradation(traceId, ViolationCategory.ORIGA_DEGRADATION.name, reflection.failureReason ?: "Unknown")
                }
                stepsCompleted.add("REFLECT")
                val historicalContext = if (reflection.success) reflection.reflectedContext else emptyList()

                // 2. REASON: Multi-domain Task Decomposition
                val reasonResult = reasonStep.process(query, traceId)
                stepsCompleted.add("REASON")
                val domainString = reasonResult.domains.joinToString("_") { it.name.lowercase() }

                // 3. INVESTIGATE: Knowledge Excavation (Parallel Grounding)
                val investigation = if (reasonResult.success) {
                    val invResult = investigateStep.process(
                        entities = reasonResult.entities.map { it.text },
                        traceId = traceId,
                        domain = domainString,
                        timeBudget = timeBudget
                    )
                    if (invResult.isDegraded && config.enableAuditLogging) {
                        auditLogger.logDegradation(traceId, ViolationCategory.ORIGA_DEGRADATION.name, invResult.failureReason ?: "Unknown")
                    }
                    invResult
                } else null
                stepsCompleted.add("INVESTIGATE")

                val investigationFacts = investigation?.facts ?: emptyList()

                val rawFacts = buildList {
                    addAll(investigationFacts)
                    addAll(historicalContext)
                    add(ContextMetadata(domain = domainString, timestamp = System.currentTimeMillis()).toFactString())
                }

                // 4. GROUND: Factual Integrity & Safety Filter
                val groundingResult = groundStep.process(
                    facts = rawFacts,
                    traceId = traceId,
                    level = ValidationLevel.INVESTIGATION
                )
                stepsCompleted.add("GROUND")

                val verifiedFacts = if (groundingResult.success) groundingResult.validatedFacts else emptyList()      

                // 5. ANSWER: Response Synthesis (Secure Streaming)
                val answerConfig = AnswerStreamConfig(traceId = traceId)
                
                // Track partial state for answer crash resilience
                var partialResponse = ""
                try {
                    answerStep.process(
                        query = query,
                        verifiedFacts = verifiedFacts,
                        observation = observation,
                        environment = environment,
                        config = answerConfig
                    ).collect { token ->
                        partialResponse += token
                        emit(token)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[ORRIGA_MAIN] ANSWER stream crashed. Recovering with partial state.")
                    if (config.enableAuditLogging) {
                        auditLogger.logDegradation(traceId, ViolationCategory.ORIGA_DEGRADATION.name, "ANSWER stream crashed")
                    }
                    emit("\n\n[SYSTEM RECOVERY] Partial response generated before interruption.")
                    // Further retry logic with partial state can be implemented here or handled by the caller.
                }
                stepsCompleted.add("ANSWER")
            }

            if (config.enableAuditLogging) {
                auditLogger.logPipelineCompletion(traceId, true, null, System.currentTimeMillis() - startTime)        
            }

        } catch (e: CancellationException) {
            Timber.w("[ORRIGA_MAIN] Pipeline cancelled | Trace: `$traceId")
            throw e
        } catch (e: Exception) {
            val failureStep = inferFailedStep(stepsCompleted)
            Timber.e(e, "[ORRIGA_MAIN] Pipeline failed at step: `$failureStep | Trace: `$traceId")

            if (config.enableAuditLogging) {
                auditLogger.logPipelineCompletion(traceId, false, e.message, System.currentTimeMillis() - startTime)  
            }

            emit(config.fallbackMessage)
        }
    }.flowOn(Dispatchers.IO)

    private fun inferFailedStep(completed: List<String>): String {
        val allSteps = listOf("REFLECT", "REASON", "INVESTIGATE", "GROUND", "ANSWER")
        return allSteps.firstOrNull { it !in completed } ?: "UNKNOWN"
    }
}

