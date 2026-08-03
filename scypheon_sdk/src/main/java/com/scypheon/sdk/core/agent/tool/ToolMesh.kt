package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ToolMesh: Production-grade tool execution mesh.
 * Manages concurrency, timeouts, permissions, hooks, and circuit breaking.
 * Modeled after the "Claude Code" tool orchestration system (toolOrchestration.ts).
 *
 * [v1.5.0-SAR] Now integrates ToolHookEngine for PreToolUse/PostToolUse lifecycle hooks.
 */
@Singleton
class ToolMesh @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val circuitBreaker: ResilienceCircuitBreaker,
    private val authGateway: com.scypheon.sdk.core.safety.helios.ToolAuthorizationGateway,
    private val hookEngine: ToolHookEngine
) {
    
    private val executionSemaphore = Semaphore(3)

    /**
     * Dispatches a list of tool calls using partitioned execution.
     * Concurrency-safe tools run in parallel; others run serially to maintain state integrity.
     */
    suspend fun dispatch(calls: List<ToolCall>, context: ExecutionContext): List<ToolResult> = coroutineScope {
        val results = mutableListOf<ToolResult>()
        
        // Partition tools into Parallel (Safe) and Serial (Mutating) batches
        val (parallelBatch, serialBatch) = calls.partition { call ->
            val tool = toolRegistry.resolve(call.toolName)
            tool?.isConcurrencySafe(call.arguments) ?: false
        }

        // 1. Execute Parallel Batch
        val parallelDeferred = parallelBatch.map { call ->
            async {
                executionSemaphore.withPermit { executeSingle(call, context) }
            }
        }
        results.addAll(parallelDeferred.awaitAll())

        // 2. Execute Serial Batch
        for (call in serialBatch) {
            results.add(executeSingle(call, context))
        }

        results
    }

    /**
     * Last PostToolUse hook results — the orchestrator can read these
     * to inject additional context into the conversation history.
     */
    var lastPostHookContexts: List<String> = emptyList()
        private set

    private suspend fun executeSingle(call: ToolCall, context: ExecutionContext): ToolResult {
        val tool = toolRegistry.resolve(call.toolName)
            ?: return ToolResult.Error(
                reason = "Unknown tool: ${call.toolName}",
                cause = null,
                latencyMs = 0
            )

        // ── Phase 0: PreToolUse Hooks (Claude Code pattern) ──
        // Hooks run BEFORE authorization and can block/modify input.
        val preHookResult = hookEngine.executePreToolUse(call.toolName, call.arguments, context)
        val effectiveArgs = when (preHookResult) {
            is ToolHookEngine.PreToolUseResult.Denied -> {
                return ToolResult.Error(
                    reason = "HOOK_DENIED: ${preHookResult.reason}",
                    cause = null,
                    latencyMs = 0
                )
            }
            is ToolHookEngine.PreToolUseResult.Modified -> {
                Timber.i("🔧 [HOOK] Tool '${call.toolName}' input modified by hook")
                preHookResult.updatedArgs
            }
            is ToolHookEngine.PreToolUseResult.Approved -> {
                preHookResult.finalArgs
            }
        }

        // ── Phase 1: HELIOS L4 Authorization ──
        val auth = authGateway.authorize(call.toolName, effectiveArgs)
        if (!auth.isAuthorized) {
            return ToolResult.Error(
                reason = "HELIOS_DENIED: ${auth.reason}",
                cause = null,
                latencyMs = 0
            )
        }
        if (auth.needsUserConsent) {
            Timber.w("🛡️ [HELIOS L4] Tool '${call.toolName}' requires user consent. Suspending execution.")
            return ToolResult.AwaitingApproval(
                toolName = call.toolName,
                args = effectiveArgs,
                reason = auth.reason ?: "User approval required."
            )
        }

        // ── Phase 2: Legacy Permission Check ──
        if (!tool.checkPermissions(effectiveArgs, context)) {
            return ToolResult.Error(
                reason = "Permission denied for tool: ${call.toolName}",
                cause = null,
                latencyMs = 0
            )
        }

        // ── Phase 3: Schema Validation ──
        val validation = tool.validate(effectiveArgs)
        if (!validation.isValid) {
            return ToolResult.Error(
                reason = "Validation failed: ${validation.errors.joinToString()}",
                cause = null,
                latencyMs = 0
            )
        }

        // ── Phase 4: Execution with Timeout & Circuit Breaker ──
        val start = System.currentTimeMillis()
        val result = try {
            withTimeout(context.toolTimeoutMs) {
                circuitBreaker.execute(call.toolName) { 
                    tool.call(effectiveArgs, context) 
                }
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("Tool execution timed out: ${call.toolName}")
            ToolResult.Error(
                reason = "Execution timed out after ${context.toolTimeoutMs}ms",
                cause = e,
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            Timber.e(e, "Tool execution failed: ${call.toolName}")
            ToolResult.Error(
                reason = "Execution failed: ${e.message}",
                cause = e,
                latencyMs = System.currentTimeMillis() - start
            )
        }

        // ── Phase 5: PostToolUse Hooks ──
        // Hooks run AFTER execution — can inject context, flag for re-evaluation.
        val postHookResult = hookEngine.executePostToolUse(call.toolName, effectiveArgs, result, context)
        lastPostHookContexts = postHookResult.additionalContexts

        return result
    }
}
