package com.scypheon.sdk.core.intelligence.graph.steps

import com.scypheon.sdk.core.intelligence.graph.MemoryReflector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Immutable result of the reflection step. Enables downstream routing,
 * latency tracking, and audit correlation.
 */
data class ReflectionResult(
    val sessionId: String,
    val traceId: String,
    val reflectedContext: List<String>,
    val latencyMs: Long,
    val success: Boolean,
    val failureReason: String? = null
)

@Singleton
class ReflectStep @Inject constructor(
    private val reflector: MemoryReflector
) {
    companion object {
        private const val REFLECTION_TIMEOUT_MS = 3000L
    }

    /**
     * Executes semantic memory reflection for the given session.
     * Returns structured result regardless of success/failure to maintain pipeline flow.
     */
    suspend fun process(sessionId: String, traceId: String): ReflectionResult =
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            Timber.i("[ORRIGA_REFLECT] Starting reflection | Session: $sessionId | Trace: $traceId")

            return@withContext try {
                withTimeout(REFLECTION_TIMEOUT_MS) {
                    val context = reflector.reflect(sessionId)
                    val latency = System.currentTimeMillis() - startTime
                    Timber.i("[ORRIGA_REFLECT] Reflection complete in ${latency}ms | Context entries: ${context.size}")
                    ReflectionResult(
                        sessionId = sessionId,
                        traceId = traceId,
                        reflectedContext = context,
                        latencyMs = latency,
                        success = true
                    )
                }
            } catch (e: TimeoutCancellationException) {
                val latency = System.currentTimeMillis() - startTime
                Timber.w("[ORRIGA_REFLECT] Reflection timed out | Session: $sessionId | Trace: $traceId")
                ReflectionResult(
                    sessionId = sessionId,
                    traceId = traceId,
                    reflectedContext = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = "Reflection exceeded ${REFLECTION_TIMEOUT_MS}ms timeout"
                )
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                Timber.e(e, "[ORRIGA_REFLECT] Reflection failed | Session: $sessionId | Trace: $traceId")
                ReflectionResult(
                    sessionId = sessionId,
                    traceId = traceId,
                    reflectedContext = emptyList(),
                    latencyMs = latency,
                    success = false,
                    failureReason = e.message ?: "Unknown reflection failure"
                )
            }
        }
}
