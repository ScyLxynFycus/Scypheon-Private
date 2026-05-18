package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.ooda.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sealed result type enabling clean Dual-Path routing (Fast vs Deep Reasoning)
 */
sealed class OODAResult {
    data class FastPath(val result: FastPathResult) : OODAResult()
    data class DelegationRequired(val payload: DelegationPayload) : OODAResult()
    data class Error(val fallbackMessage: String, val traceId: String, val cause: Throwable?) : OODAResult()
}

data class DelegationPayload(
    val traceId: String,
    val query: String,
    val observation: Observation,
    val orientation: Orientation,
    val reason: String
)

interface DelegationHandler {
    suspend fun handleDelegation(payload: DelegationPayload): OODAResult
}

@Singleton
class OODAFastEngine @Inject constructor(
    private val observe: ObserveStep,
    private val orient: OrientStep,
    private val decide: DecideStep,
    private val act: ActStep
) {
    companion object {
        private const val FAST_PATH_SLA_MS = 200L
    }

    suspend fun execute(
        query: String,
        session: SessionContext,
        environment: DeviceEnvironment
    ): OODAResult = withContext(Dispatchers.Default) {
        val traceId = UUID.randomUUID().toString()
        val pipelineStart = System.currentTimeMillis()
        Timber.i("⚡ [OODA_ENGINE] Starting Fast Path | Trace: $traceId")

        try {
            withTimeout(FAST_PATH_SLA_MS) {
                // 1. OBSERVE
                val obsStart = System.currentTimeMillis()
                val observation = observe.execute(query, session, environment)
                Timber.d("⏱️ [OODA] Observe: ${System.currentTimeMillis() - obsStart}ms")

                // 2. ORIENT
                val oriStart = System.currentTimeMillis()
                val orientation = orient.execute(observation, environment)
                Timber.d("⏱️ [OODA] Orient: ${System.currentTimeMillis() - oriStart}ms")

                // 🔄 Early delegation check (saves battery & latency)
                if (orientation.requiresDeepReasoning) {
                    Timber.i("🔄 [OODA_ENGINE] Handing off to ORIGA | Trace: $traceId")
                    return@withTimeout OODAResult.DelegationRequired(
                        DelegationPayload(
                            traceId = traceId,
                            query = query,
                            observation = observation,
                            orientation = orientation,
                            reason = orientation.delegationReason ?: "Complexity threshold exceeded"
                        )
                    )
                }

                // 3. DECIDE
                val decStart = System.currentTimeMillis()
                val decision = decide.execute(orientation, environment)
                Timber.d("⏱️ [OODA] Decide: ${System.currentTimeMillis() - decStart}ms")

                // ⏹️ Early exit on fallback decision (skip ActStep)
                if (decision.isFallback) {
                    Timber.i("⏹️ [OODA_ENGINE] Fast path fallback triggered | Trace: $traceId")
                    return@withTimeout OODAResult.FastPath(
                        FastPathResult(
                            skillName = orientation.selectedSkill.type.name,
                            toolName = "fallback_chat",
                            result = decision.parameters["query"] ?: query,
                            latencyMs = System.currentTimeMillis() - pipelineStart,
                            validated = true,
                            auditTraceId = traceId,
                            fallbackReason = decision.rationale.fallbackReason
                        )
                    )
                }

                // 4. ACT
                val actStart = System.currentTimeMillis()
                val actResult = act.execute(decision, session, environment, orientation)
                Timber.d("⏱️ [OODA] Act: ${System.currentTimeMillis() - actStart}ms")

                val totalLatency = System.currentTimeMillis() - pipelineStart
                Timber.i("🏁 [OODA_ENGINE] Fast Path completed in ${totalLatency}ms | Trace: $traceId")

                OODAResult.FastPath(actResult.copy(auditTraceId = traceId, latencyMs = totalLatency))
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("⏳ [OODA_ENGINE] Fast path SLA breached (${FAST_PATH_SLA_MS}ms) | Trace: $traceId")
            OODAResult.Error("Response delayed. Switching to safe mode.", traceId, e)
        } catch (e: Exception) {
            Timber.e(e, "💥 [OODA_ENGINE] Fast path failed | Trace: $traceId")
            OODAResult.Error("System encountered an error. Falling back to safe response.", traceId, e)
        }
    }
}
