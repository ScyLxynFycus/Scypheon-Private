package com.scypheon.sdk.core.intelligence.graph.steps

import com.scypheon.sdk.core.intelligence.graph.MemoryReflector
import com.scypheon.sdk.core.intelligence.graph.TimeBudget
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class ReflectionResult(
    val sessionId: String,
    val traceId: String,
    val reflectedContext: List<String>,
    val latencyMs: Long,
    val success: Boolean,
    val failureReason: String? = null,
    val isDegraded: Boolean = false
)

@Singleton
class ReflectStep @Inject constructor(
    private val reflector: MemoryReflector,
    private val circuitBreaker: ResilienceCircuitBreaker
) {

    suspend fun process(sessionId: String, traceId: String, timeBudget: TimeBudget): ReflectionResult =
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            Timber.i("[ORRIGA_REFLECT] Starting reflection | Session: `$sessionId | Trace: `$traceId")

            if (timeBudget.isExpired()) {
                Timber.w("[ORRIGA_REFLECT] Time budget expired before start | Trace: `$traceId")
                return@withContext ReflectionResult(
                    sessionId = sessionId,
                    traceId = traceId,
                    reflectedContext = emptyList(),
                    latencyMs = 0,
                    success = false,
                    failureReason = "Time budget expired (< 500ms remaining)",
                    isDegraded = true
                )
            }

            return@withContext try {
                circuitBreaker.execute("database_reflect") {
                    withTimeout(timeBudget.remaining()) {
                        val context = reflector.reflect(sessionId)
                        val latency = System.currentTimeMillis() - startTime
                        Timber.i("[ORRIGA_REFLECT] Reflection complete in `${latency}ms | Context entries: `${context.size}")
                        ReflectionResult(
                            sessionId = sessionId,
                            traceId = traceId,
                            reflectedContext = context,
                            latencyMs = latency,
                            success = true
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val latency = System.currentTimeMillis() - startTime
                Timber.w("[ORRIGA_REFLECT] Reflection timed out | Session: `$sessionId | Trace: `$traceId")
                ReflectionResult(
                    sessionId = sessionId,
                    traceId = traceId,
                    reflectedContext = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = "Reflection exceeded time budget",
                    isDegraded = true
                )
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                Timber.e(e, "[ORRIGA_REFLECT] Reflection failed | Session: `$sessionId | Trace: `$traceId")
                ReflectionResult(
                    sessionId = sessionId,
                    traceId = traceId,
                    reflectedContext = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = e.message ?: "Unknown reflection failure",
                    isDegraded = true
                )
            }
        }
}
