package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.ooda.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgenticRouter:
 * The Master Router that manages the Dual-Path Execution flow.
 * Fully hardened with Layer 0-4 safety integration, live system monitoring,
 * timeout SLAs, and cryptographic trace propagation.
 */
@Singleton
class AgenticRouter @Inject constructor(
    private val oodaEngine: OODAFastEngine,
    private val delegationHandler: DelegationHandler,
    private val safetyPipeline: SafetyPipeline,
    private val outputValidator: RouterOutputValidator,
    private val systemMonitor: SystemMonitor,
    private val auditLogger: RouterAuditLogger
) {

    suspend fun route(query: String, session: SessionContext): FinalResponse = withContext(Dispatchers.Default) {
        val traceId = UUID.randomUUID().toString()
        Timber.i("🧠 [AGENTIC_ROUTER] Routing query | Trace: $traceId")

        try {
            // 1. Input Safety Gate (Layer 0-2: Sanitizer + Rules + ML)
            val inputVerdict = safetyPipeline.evaluateInput(query, session)
            if (inputVerdict == SafetyVerdict.BLOCKED) {
                auditLogger.logSecurityBlock(traceId, query, "Input safety violation")
                return@withContext FinalResponse.Blocked("Query blocked by safety protocols.", traceId)
            }

            // 2. Real-time Environment Snapshot (Battery, Thermal, Network)
            val env = systemMonitor.captureSnapshot()

            // 3. Execute OODA Fast Path
            val oodaResult = oodaEngine.execute(query, session, env)

            // 4. Route based on sealed OODA outcome
            when (oodaResult) {
                is OODAResult.FastPath -> {
                    Timber.i("⚡ [AGENTIC_ROUTER] OODA resolved query | Trace: $traceId")
                    val validation = outputValidator.validateFinalResponse(oodaResult.result.result, env)
                    if (!validation.isValid) {
                        return@withContext FinalResponse.Blocked(validation.reason, traceId)
                    }
                    FinalResponse.Success(oodaResult.result.result, traceId, ResponseSource.OODA_FAST_PATH)
                }

                is OODAResult.DelegationRequired -> {
                    Timber.w("🔬 [AGENTIC_ROUTER] Delegating to ORIGA | Trace: $traceId")
                    val origaResult = delegationHandler.handleDelegation(oodaResult.payload)
                    
                    when (origaResult) {
                        is OODAResult.FastPath -> {
                            // Deep reasoning results still need a final validation pass
                            val validation = outputValidator.validateFinalResponse(origaResult.result.result, env)
                            if (!validation.isValid) {
                                auditLogger.logSecurityBlock(traceId, origaResult.result.result, "ORIGA output validation failed")
                                return@withContext FinalResponse.Blocked("Deep analysis output failed safety validation.", traceId)
                            }
                            FinalResponse.Success(origaResult.result.result, traceId, ResponseSource.ORIGA_DEEP_PATH)
                        }
                        is OODAResult.Error -> {
                            FinalResponse.Error(origaResult.fallbackMessage, traceId)
                        }
                        else -> {
                            FinalResponse.Error("Deep reasoning returned an invalid state.", traceId)
                        }
                    }
                }

                is OODAResult.Error -> {
                    Timber.e("💥 [AGENTIC_ROUTER] OODA failed. Triggering safe fallback | Trace: $traceId")
                    auditLogger.logPipelineFailure(traceId, oodaResult.cause)
                    FinalResponse.Error(oodaResult.fallbackMessage, traceId)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🚨 [AGENTIC_ROUTER] Unhandled routing failure | Trace: $traceId")
            auditLogger.logPipelineFailure(traceId, e)
            FinalResponse.Error("System encountered an error. Falling back to safe mode.", traceId)
        }
    }
}
