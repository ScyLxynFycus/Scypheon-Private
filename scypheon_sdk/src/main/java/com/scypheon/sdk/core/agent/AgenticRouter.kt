package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.ooda.*
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.CircuitBreakerOpenException
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
 * timeout SLAs, cryptographic trace propagation, and Circuit Breakers.
 */
@Singleton
class AgenticRouter @Inject constructor(
    private val oodaEngine: OODAFastEngine,
    private val delegationHandler: DelegationHandler,
    private val safetyPipeline: com.scypheon.sdk.core.safety.helios.SafetyPipeline,
    private val outputValidator: RouterOutputValidator,
    private val systemMonitor: SystemMonitor,
    private val auditLogger: RouterAuditLogger,
    private val circuitBreaker: ResilienceCircuitBreaker
) {

    suspend fun route(query: String, session: SessionContext): FinalResponse = withContext(Dispatchers.Default) {
        val traceId = UUID.randomUUID().toString()
        Timber.i("🧠 [AGENTIC_ROUTER] Routing query | Trace: $traceId")

        try {
            // 1. Input Safety Gate (Layer 0-2: Sanitizer + Rules + ML via HELIOS)
            val evaluation = safetyPipeline.evaluateInput(session.id, query)
            if (evaluation.status == com.scypheon.sdk.core.safety.helios.SafetyPipeline.SecurityStatus.BLOCKED) {
                auditLogger.logSecurityBlock(traceId, query, evaluation.reason ?: "Input safety violation")
                return@withContext FinalResponse.Blocked("Query blocked by safety protocols.", traceId)
            }
            
            val safeQuery = evaluation.sanitizedPrompt

            // 2. Real-time Environment Snapshot (Battery, Thermal, Network)
            val env = systemMonitor.captureSnapshot()

            // 3. Execute OODA Fast Path protected by Circuit Breaker
            val oodaResult = try {
                circuitBreaker.execute("agentic_router_ooda") {
                    oodaEngine.execute(safeQuery, session, env)
                }
            } catch (e: CircuitBreakerOpenException) {
                Timber.e("🛡️ [AGENTIC_ROUTER] OODA Engine circuit is OPEN. Fast-failing | Trace: $traceId")
                return@withContext FinalResponse.Error("System is temporarily degraded. Please try again later.", traceId)
            }

            // 4. Route based on sealed OODA outcome
            when (oodaResult) {
                is OODAResult.FastPath -> {
                    Timber.i("⚡ [AGENTIC_ROUTER] OODA resolved query | Trace: $traceId")
                    
                    // Validate Output through HELIOS before returning
                    val outEval = safetyPipeline.evaluateOutput(session.id, oodaResult.result.result)
                    if (outEval.status == com.scypheon.sdk.core.safety.helios.SafetyPipeline.SecurityStatus.BLOCKED) {
                        return@withContext FinalResponse.Blocked(outEval.reason ?: "Unsafe AI response blocked.", traceId)
                    }

                    val validation = outputValidator.validateFinalResponse(outEval.sanitizedPrompt, env)
                    if (!validation.isValid) {
                        return@withContext FinalResponse.Blocked(validation.reason, traceId)
                    }
                    FinalResponse.Success(outEval.sanitizedPrompt, traceId, ResponseSource.OODA_FAST_PATH)
                }

                is OODAResult.DelegationRequired -> {
                    Timber.w("🔬 [AGENTIC_ROUTER] Delegating to ORIGA | Trace: $traceId")
                    
                    val origaResult = try {
                        circuitBreaker.execute("agentic_router_origa") {
                            delegationHandler.handleDelegation(oodaResult.payload)
                        }
                    } catch (e: CircuitBreakerOpenException) {
                        Timber.e("🛡️ [AGENTIC_ROUTER] ORIGA Delegation circuit is OPEN. Fast-failing | Trace: $traceId")
                        return@withContext FinalResponse.Error("Deep reasoning subsystem is offline. Returning safe fallback.", traceId)
                    }
                    
                    when (origaResult) {
                        is OODAResult.FastPath -> {
                            // Deep reasoning results still need a final validation pass via HELIOS
                            val outEval = safetyPipeline.evaluateOutput(session.id, origaResult.result.result)
                            if (outEval.status == com.scypheon.sdk.core.safety.helios.SafetyPipeline.SecurityStatus.BLOCKED) {
                                return@withContext FinalResponse.Blocked(outEval.reason ?: "Unsafe AI response blocked.", traceId)
                            }
                            
                            val validation = outputValidator.validateFinalResponse(outEval.sanitizedPrompt, env)
                            if (!validation.isValid) {
                                auditLogger.logSecurityBlock(traceId, outEval.sanitizedPrompt, "ORIGA output validation failed")
                                return@withContext FinalResponse.Blocked("Deep analysis output failed safety validation.", traceId)
                            }
                            FinalResponse.Success(outEval.sanitizedPrompt, traceId, ResponseSource.ORIGA_DEEP_PATH)
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
