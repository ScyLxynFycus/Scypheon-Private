package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.CircuitBreakerOpenException
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ToolHookEngine: Lifecycle hook system for agentic tool orchestration.
 *
 * Ported from the Claude Code hooks architecture.
 * Provides three hook points in the agentic pipeline:
 *
 * 1. **PreToolUse**  — Before a tool executes. Can block, modify input, or approve.
 * 2. **PostToolUse** — After a tool executes. Can inject context, audit results.
 * 3. **StopHook**    — After LLM finishes responding. Can force re-generation.
 *
 * Enterprise Resilience:
 * Each hook is protected by a [ResilienceCircuitBreaker]. If a specific hook fails
 * repeatedly or times out, its circuit opens to prevent it from degrading the system.
 * - Pre-hooks: Fail-safe (Deny) if circuit is open.
 * - Post/Stop hooks: Fail-open (Pass) if circuit is open, prioritizing availability.
 */
@Singleton
class ToolHookEngine @Inject constructor(
    private val blackBoxVault: BlackBoxVault,
    private val circuitBreaker: ResilienceCircuitBreaker
) {
    companion object {
        private const val PRE_TOOL_TIMEOUT_MS = 5000L
        private const val POST_TOOL_TIMEOUT_MS = 3000L
        private const val STOP_HOOK_TIMEOUT_MS = 2000L
    }

    // ── Hook Registry (CopyOnWriteArrayList for thread-safe iteration) ──

    private val preToolUseHooks = CopyOnWriteArrayList<PreToolUseHook>()
    private val postToolUseHooks = CopyOnWriteArrayList<PostToolUseHook>()
    private val stopHooks = CopyOnWriteArrayList<StopHook>()

    fun registerPreToolUse(hook: PreToolUseHook) { preToolUseHooks.addIfAbsent(hook) }
    fun registerPostToolUse(hook: PostToolUseHook) { postToolUseHooks.addIfAbsent(hook) }
    fun registerStopHook(hook: StopHook) { stopHooks.addIfAbsent(hook) }

    fun unregisterPreToolUse(hook: PreToolUseHook) { preToolUseHooks.remove(hook) }
    fun unregisterPostToolUse(hook: PostToolUseHook) { postToolUseHooks.remove(hook) }
    fun unregisterStopHook(hook: StopHook) { stopHooks.remove(hook) }

    fun clearAll() {
        preToolUseHooks.clear()
        postToolUseHooks.clear()
        stopHooks.clear()
        Timber.d("[HOOK] All hooks cleared")
    }

    // ── PreToolUse Execution ────────────────────────────────────────────

    suspend fun executePreToolUse(
        toolName: String,
        args: Map<String, Any?>,
        context: ExecutionContext
    ): PreToolUseResult {
        if (preToolUseHooks.isEmpty()) return PreToolUseResult.Approved(args)

        var currentArgs = args

        for (hook in preToolUseHooks) {
            if (!hook.matches(toolName)) continue

            try {
                val startMs = System.currentTimeMillis()
                val result = circuitBreaker.execute("hook_pre_${hook.name}") {
                    withTimeout(PRE_TOOL_TIMEOUT_MS) {
                        hook.evaluate(toolName, currentArgs, context)
                    }
                }
                val durationMs = System.currentTimeMillis() - startMs

                blackBoxVault.logEvent(
                    "HOOK_PRE_TOOL",
                    "Hook '${hook.name}' on tool '$toolName': ${result.javaClass.simpleName} (${durationMs}ms)"
                )

                when (result) {
                    is PreToolUseResult.Denied -> {
                        Timber.w("[HOOK] PreToolUse DENIED by '${hook.name}': ${result.reason}")
                        return result
                    }
                    is PreToolUseResult.Modified -> {
                        Timber.i("[HOOK] PreToolUse MODIFIED input by '${hook.name}'")
                        currentArgs = result.updatedArgs
                    }
                    is PreToolUseResult.Approved -> {
                        // Continue with next hook
                    }
                }
            } catch (e: CircuitBreakerOpenException) {
                Timber.e("[HOOK] PreToolUse hook '${hook.name}' circuit is OPEN. Failing safe -> deny.")
                return PreToolUseResult.Denied("Security hook degraded. Failing safe.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.e("[HOOK] PreToolUse hook '${hook.name}' timed out after ${PRE_TOOL_TIMEOUT_MS}ms. Failing safe -> deny.")
                circuitBreaker.recordFailure("hook_pre_${hook.name}", e)
                return PreToolUseResult.Denied("Hook '${hook.name}' timed out")
            } catch (e: Exception) {
                Timber.e(e, "[HOOK] PreToolUse hook '${hook.name}' threw exception. Failing safe -> deny.")
                circuitBreaker.recordFailure("hook_pre_${hook.name}", e)
                return PreToolUseResult.Denied("Hook error: ${e.message}")
            }
        }

        return PreToolUseResult.Approved(currentArgs)
    }

    // ── PostToolUse Execution ───────────────────────────────────────────

    suspend fun executePostToolUse(
        toolName: String,
        args: Map<String, Any?>,
        result: ToolResult,
        context: ExecutionContext
    ): PostToolUseResult {
        if (postToolUseHooks.isEmpty()) return PostToolUseResult(emptyList(), false)

        val additionalContexts = mutableListOf<String>()
        var needsReEvaluation = false

        for (hook in postToolUseHooks) {
            if (!hook.matches(toolName)) continue

            try {
                val hookResult = circuitBreaker.execute("hook_post_${hook.name}") {
                    withTimeout(POST_TOOL_TIMEOUT_MS) {
                        hook.evaluate(toolName, args, result, context)
                    }
                }

                if (hookResult.additionalContext != null) {
                    additionalContexts.add(hookResult.additionalContext)
                }
                if (hookResult.flagForReEvaluation) {
                    needsReEvaluation = true
                }

                blackBoxVault.logEvent(
                    "HOOK_POST_TOOL",
                    "Hook '${hook.name}' on tool '$toolName': context=${hookResult.additionalContext != null}, reeval=$needsReEvaluation"
                )
            } catch (e: CircuitBreakerOpenException) {
                Timber.w("[HOOK] PostToolUse hook '${hook.name}' circuit is OPEN. Failing open (skip).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.e("[HOOK] PostToolUse hook '${hook.name}' timed out. Continuing.")
                circuitBreaker.recordFailure("hook_post_${hook.name}", e)
            } catch (e: Exception) {
                Timber.e(e, "[HOOK] PostToolUse hook '${hook.name}' failed. Continuing.")
                circuitBreaker.recordFailure("hook_post_${hook.name}", e)
            }
        }

        return PostToolUseResult(additionalContexts, needsReEvaluation)
    }

    // ── Stop Hooks ──────────────────────────────────────────────────────

    suspend fun executeStopHooks(
        fullResponse: String,
        context: ExecutionContext
    ): StopHookResult {
        if (stopHooks.isEmpty()) return StopHookResult.Complete

        val blockingErrors = mutableListOf<String>()
        var preventCompletion = false
        var stopReason: String? = null

        for (hook in stopHooks) {
            try {
                val startMs = System.currentTimeMillis()
                val result = circuitBreaker.execute("hook_stop_${hook.name}") {
                    withTimeout(STOP_HOOK_TIMEOUT_MS) {
                        hook.evaluate(fullResponse, context)
                    }
                }
                val durationMs = System.currentTimeMillis() - startMs

                when (result) {
                    is StopHookDecision.ForceRetry -> {
                        Timber.w("[STOP_HOOK] '${hook.name}' forcing retry: ${result.blockingError}")
                        blockingErrors.add(result.blockingError)
                    }
                    is StopHookDecision.PreventCompletion -> {
                        Timber.w("[STOP_HOOK] '${hook.name}' preventing completion: ${result.reason}")
                        preventCompletion = true
                        stopReason = result.reason
                    }
                    is StopHookDecision.Pass -> {}
                }

                blackBoxVault.logEvent(
                    "HOOK_STOP",
                    "Hook '${hook.name}': ${result.javaClass.simpleName} (${durationMs}ms)"
                )
            } catch (e: CircuitBreakerOpenException) {
                Timber.w("[STOP_HOOK] Hook '${hook.name}' circuit is OPEN. Failing open (pass).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.e("[STOP_HOOK] Hook '${hook.name}' timed out. Failing open -> pass.")
                circuitBreaker.recordFailure("hook_stop_${hook.name}", e)
            } catch (e: Exception) {
                Timber.e(e, "[STOP_HOOK] Hook '${hook.name}' failed. Failing open -> pass.")
                circuitBreaker.recordFailure("hook_stop_${hook.name}", e)
            }
        }

        return when {
            preventCompletion -> StopHookResult.PreventCompletion(stopReason ?: "Blocked by stop hook")
            blockingErrors.isNotEmpty() -> StopHookResult.ForceContinuation(blockingErrors)
            else -> StopHookResult.Complete
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // CONTRACTS
    // ══════════════════════════════════════════════════════════════════

    interface PreToolUseHook {
        val name: String
        fun matches(toolName: String): Boolean
        suspend fun evaluate(toolName: String, args: Map<String, Any?>, context: ExecutionContext): PreToolUseResult
    }

    interface PostToolUseHook {
        val name: String
        fun matches(toolName: String): Boolean
        suspend fun evaluate(toolName: String, args: Map<String, Any?>, result: ToolResult, context: ExecutionContext): PostToolUseEvaluation
    }

    interface StopHook {
        val name: String
        suspend fun evaluate(fullResponse: String, context: ExecutionContext): StopHookDecision
    }

    // ── Result Types ────────────────────────────────────────────────────

    sealed class PreToolUseResult {
        data class Approved(val finalArgs: Map<String, Any?>) : PreToolUseResult()
        data class Modified(val updatedArgs: Map<String, Any?>, val reason: String) : PreToolUseResult()
        data class Denied(val reason: String) : PreToolUseResult()
    }

    data class PostToolUseEvaluation(
        val additionalContext: String? = null,
        val flagForReEvaluation: Boolean = false
    )

    data class PostToolUseResult(
        val additionalContexts: List<String>,
        val needsReEvaluation: Boolean
    )

    sealed class StopHookDecision {
        object Pass : StopHookDecision()
        data class ForceRetry(val blockingError: String) : StopHookDecision()
        data class PreventCompletion(val reason: String) : StopHookDecision()
    }

    sealed class StopHookResult {
        object Complete : StopHookResult()
        data class ForceContinuation(val blockingErrors: List<String>) : StopHookResult()
        data class PreventCompletion(val reason: String) : StopHookResult()
    }
}
