package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.telemetry.BlackBoxVault
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ToolHookEngine: Lifecycle hook system for agentic tool orchestration.
 * 
 * Ported from the Claude Code hooks architecture (src/utils/hooks.ts).
 * Provides three hook points in the agentic pipeline:
 * 
 * 1. PreToolUse  → Before a tool executes. Can block, modify input, or approve.
 * 2. PostToolUse → After a tool executes. Can inject context, audit results.
 * 3. StopHook    → After LLM finishes responding. Can force re-generation.
 *
 * This replaces the missing hooks gap identified in the v1.4.0-SAR audit.
 */
@Singleton
class ToolHookEngine @Inject constructor(
    private val blackBoxVault: BlackBoxVault
) {
    // ── Hook Registry ───────────────────────────────────────────────────

    private val preToolUseHooks = mutableListOf<PreToolUseHook>()
    private val postToolUseHooks = mutableListOf<PostToolUseHook>()
    private val stopHooks = mutableListOf<StopHook>()

    fun registerPreToolUse(hook: PreToolUseHook) { preToolUseHooks.add(hook) }
    fun registerPostToolUse(hook: PostToolUseHook) { postToolUseHooks.add(hook) }
    fun registerStopHook(hook: StopHook) { stopHooks.add(hook) }

    // ── PreToolUse Execution ────────────────────────────────────────────

    /**
     * Runs all PreToolUse hooks before a tool call.
     * Any hook can:
     * - BLOCK the call (returns Denied)
     * - MODIFY the input (returns Modified with new args)
     * - APPROVE the call (returns Approved)
     * 
     * If multiple hooks run, the strictest decision wins (Deny > Modify > Allow).
     */
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
                val result = hook.evaluate(toolName, currentArgs, context)
                val durationMs = System.currentTimeMillis() - startMs
                
                blackBoxVault.logEvent(
                    "HOOK_PRE_TOOL", 
                    "Hook '${hook.name}' on tool '$toolName': ${result.javaClass.simpleName} (${durationMs}ms)"
                )
                
                when (result) {
                    is PreToolUseResult.Denied -> {
                        Timber.w("🛡️ [HOOK] PreToolUse DENIED by '${hook.name}': ${result.reason}")
                        return result // Immediate block
                    }
                    is PreToolUseResult.Modified -> {
                        Timber.i("🔧 [HOOK] PreToolUse MODIFIED input by '${hook.name}'")
                        currentArgs = result.updatedArgs
                    }
                    is PreToolUseResult.Approved -> {
                        // Continue with next hook
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[HOOK] PreToolUse hook '${hook.name}' threw exception. Failing safe → deny.")
                return PreToolUseResult.Denied("Hook error: ${e.message}")
            }
        }
        
        return PreToolUseResult.Approved(currentArgs)
    }

    // ── PostToolUse Execution ───────────────────────────────────────────

    /**
     * Runs all PostToolUse hooks after a tool call completes.
     * Hooks can:
     * - INJECT additional context into the conversation
     * - AUDIT the result for safety/compliance
     * - FLAG the result as requiring re-evaluation
     */
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
                val hookResult = hook.evaluate(toolName, args, result, context)
                
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
            } catch (e: Exception) {
                Timber.e(e, "[HOOK] PostToolUse hook '${hook.name}' threw exception. Continuing.")
                // PostToolUse hooks are non-blocking — failures don't prevent results
            }
        }

        return PostToolUseResult(additionalContexts, needsReEvaluation)
    }

    // ── Stop Hooks ──────────────────────────────────────────────────────

    /**
     * Runs all Stop hooks after the LLM finishes responding (no more tool calls).
     * Mirrors Claude Code's stopHooks.ts — the final quality gate.
     * 
     * Hooks can:
     * - FORCE CONTINUATION: Inject a blocking error message that forces the LLM to re-generate
     * - PREVENT COMPLETION: Stop the loop early (e.g., safety violation detected)
     * - INJECT CONTEXT: Add system-level observations (e.g., clinical warnings)
     */
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
                val result = hook.evaluate(fullResponse, context)
                val durationMs = System.currentTimeMillis() - startMs
                
                when (result) {
                    is StopHookDecision.ForceRetry -> {
                        Timber.w("🔄 [STOP_HOOK] '${hook.name}' forcing retry: ${result.blockingError}")
                        blockingErrors.add(result.blockingError)
                    }
                    is StopHookDecision.PreventCompletion -> {
                        Timber.w("🛑 [STOP_HOOK] '${hook.name}' preventing completion: ${result.reason}")
                        preventCompletion = true
                        stopReason = result.reason
                    }
                    is StopHookDecision.Pass -> {
                        // Quality check passed
                    }
                }
                
                blackBoxVault.logEvent(
                    "HOOK_STOP", 
                    "Hook '${hook.name}': ${result.javaClass.simpleName} (${durationMs}ms)"
                )
            } catch (e: Exception) {
                Timber.e(e, "[STOP_HOOK] Hook '${hook.name}' threw exception. Failing open → pass.")
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
