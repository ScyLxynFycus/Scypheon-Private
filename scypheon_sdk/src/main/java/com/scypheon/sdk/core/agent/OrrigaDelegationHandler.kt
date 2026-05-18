package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.ooda.*
import com.scypheon.sdk.core.intelligence.graph.HybridGraphOrrigaEngine
import com.scypheon.sdk.core.security.AuditLoggerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OrrigaDelegationHandler:
 * Manages the transition to the Deep Path (ORRIGA).
 */
@Singleton
class OrrigaDelegationHandler @Inject constructor(
    private val oracleEngine: HybridGraphOrrigaEngine,
    private val auditLogger: RouterAuditLogger 
) : DelegationHandler {

    companion object {
        private const val ORRIGA_SLA_MS = 8000L
    }

    override suspend fun handleDelegation(payload: DelegationPayload): OODAResult = withContext(Dispatchers.Default) {
        Timber.i("🔬 [ORRIGA_HANDLER] Processing delegation | Trace: ${payload.traceId}")

        return@withContext try {
            withTimeout(ORRIGA_SLA_MS) {
                val responseChunks = mutableListOf<String>()
                
                oracleEngine.reason(
                    query = payload.query,
                    observation = payload.observation,
                    orientation = payload.orientation,
                    environment = payload.observation.environmentSnapshot
                )
                .catch { e ->
                    Timber.e(e, "🚨 [ORRIGA_HANDLER] Stream failed | Trace: ${payload.traceId}")
                    throw e
                }
                .collect { chunk ->
                    responseChunks.add(chunk)
                }

                val finalResponse = responseChunks.joinToString("")
                auditLogger.logDeepReasoningSuccess(payload.traceId, payload.reason)
                
                OODAResult.FastPath(FastPathResult(
                    skillName = payload.orientation.selectedSkill.type.name,
                    toolName = "ORRIGA_RESOLVED",
                    result = finalResponse,
                    latencyMs = 0, 
                    validated = true,
                    auditTraceId = payload.traceId
                ))
            }
        } catch (e: Exception) {
            auditLogger.logPipelineFailure(payload.traceId, e)
            OODAResult.Error(
                fallbackMessage = "ORRIGA deep reasoning failed or timed out. Falling back to safe response.",
                traceId = payload.traceId,
                cause = e
            )
        }
    }
}
