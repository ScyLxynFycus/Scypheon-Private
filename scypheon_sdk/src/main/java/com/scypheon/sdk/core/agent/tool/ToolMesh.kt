package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlin.coroutines.resume
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
    @ApplicationContext private val context: Context,
    private val toolRegistry: ToolRegistry,
    private val circuitBreaker: ResilienceCircuitBreaker,
    private val authGateway: com.scypheon.sdk.core.safety.helios.ToolAuthorizationGateway,
    private val hookEngine: ToolHookEngine,
    private val outputSanitizer: ToolOutputSanitizer,
    private val pqcAuditSigner: com.scypheon.sdk.core.security.PqcAuditSigner,
    private val auditChain: com.scypheon.sdk.core.security.AuditChain,
    private val routerContract: com.scypheon.sdk.core.agent.RouterContract,
    private val fallbackEngine: com.scypheon.sdk.core.resilience.FallbackEngine
) {
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private val TIER_2_TOOLS = setOf("file_read", "file_write", "glob_search", "grep_search", "execute_safe_command")
        private val TIER_3_TOOLS = setOf("calculate_basic", "evaluate_expression", "parse_latex", "hash_verify")
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val BIND_TIMEOUT_MS = 5_000L
    }

    private var sandboxService: ISandboxedToolMesh? = null
    private var computeService: IIsolatedCompute? = null
    private val bindLatch = CountDownLatch(2)

    private val deathRecipientSandbox = IBinder.DeathRecipient {
        Timber.e("🚨 Sandbox process died! Triggering auto-rebind.")
        sandboxService = null
        rebindSandboxService()
    }
    
    private val deathRecipientCompute = IBinder.DeathRecipient {
        Timber.e("🚨 Compute Sandbox process died! Triggering auto-rebind.")
        computeService = null
        rebindComputeService()
    }

    private val sandboxConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sandboxService = ISandboxedToolMesh.Stub.asInterface(service)
            try { service?.linkToDeath(deathRecipientSandbox, 0) } catch (e: Exception) {}
            bindLatch.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            sandboxService = null
        }
    }

    private val computeConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            computeService = IIsolatedCompute.Stub.asInterface(service)
            try { service?.linkToDeath(deathRecipientCompute, 0) } catch (e: Exception) {}
            bindLatch.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            computeService = null
        }
    }

    init {
        rebindSandboxService()
        rebindComputeService()
    }

    private fun rebindSandboxService() {
        try { context.unbindService(sandboxConnection) } catch (e: Exception) {}
        context.bindService(
            Intent(context, SandboxedToolService::class.java),
            sandboxConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun rebindComputeService() {
        try { context.unbindService(computeConnection) } catch (e: Exception) {}
        context.bindService(
            Intent(context, IsolatedComputeService::class.java),
            computeConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun shutdown() {
        try { context.unbindService(sandboxConnection) } catch (e: Exception) {}
        try { context.unbindService(computeConnection) } catch (e: Exception) {}
    }
    
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
                    when (call.toolName) {
                        in TIER_3_TOOLS -> dispatchTier3(call.toolName, effectiveArgs, context)
                        in TIER_2_TOOLS -> dispatchTier2(call.toolName, effectiveArgs, context)
                        else -> tool.call(effectiveArgs, context) // Tier 1 In-Process
                    }
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

        // ── Phase 4.5: PQC Cryptographic Verification ──
        var finalResult = result
        val perm = try { routerContract.getPermission(call.toolName) } catch (e: Exception) { null }
        if (perm != null && perm.requiresPqcSignature) {
            val fingerprint = pqcAuditSigner.getPublicKeyFingerprint()
            val payloadBytes = result.toString().toByteArray()
            
            if (perm.requiredConsent == com.scypheon.sdk.core.agent.ConsentLevel.EXPLICIT_BIOMETRIC) {
                // CRITICAL: Block and sign synchronously
                try {
                    val signature = pqcAuditSigner.signEvent(payloadBytes)
                    finalResult = when (result) {
                        is ToolResult.Success -> result.copy(
                            metadata = result.metadata + mapOf(
                                "pqc_signature" to signature,
                                "signer_fingerprint" to fingerprint
                            )
                        )
                        else -> result
                    }
                    auditChain.logEvent("TOOL_EXECUTION_CRITICAL", "Tool: ${call.toolName}, Success: ${result is ToolResult.Success}")
                } catch (e: Exception) {
                    Timber.e(e, "[ToolMesh] Synchronous PQC signing failed for ${call.toolName}")
                }
            } else {
                // NON-CRITICAL: Sign asynchronously, don't block tool result
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val signature = pqcAuditSigner.signEvent(payloadBytes)
                        auditChain.logEvent("TOOL_EXECUTION_INFO", "Tool: ${call.toolName}, Signature: $signature, Fingerprint: $fingerprint")
                    } catch (e: Exception) {
                        Timber.w(e, "[ToolMesh] Async PQC signing failed for ${call.toolName} (Option A - silent fallback)")
                    }
                }
            }
        }

        // ── Phase 5: PostToolUse Hooks ──
        // Hooks run AFTER execution — can inject context, flag for re-evaluation.
        val postHookResult = hookEngine.executePostToolUse(call.toolName, effectiveArgs, finalResult, context)
        lastPostHookContexts = postHookResult.additionalContexts

        return outputSanitizer.sanitize(call.toolName, finalResult)
    }

    private suspend fun dispatchTier2(toolName: String, args: Map<String, Any?>, executionContext: ExecutionContext): ToolResult {
        if (!bindLatch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return ToolResult.Error("Tier 2 Sandbox bind timeout", null, 0)
        }
        val service = sandboxService ?: return ToolResult.Error("Tier 2 Sandbox unavailable", null, 0)
        
        return suspendCancellableCoroutine { cont ->
            val callback = object : IToolResultCallback.Stub() {
                override fun onSuccess(jsonResult: String, latencyMs: Long) {
                    if (cont.isActive) cont.resume(ToolResult.Success(jsonResult, latencyMs = latencyMs))
                }
                override fun onError(reason: String, latencyMs: Long) {
                    if (cont.isActive) cont.resume(ToolResult.Error(reason, null, latencyMs))
                }
            }
            try {
                service.dispatchTool(toolName, serializeArgs(args), executionContext.toolTimeoutMs, callback)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(ToolResult.Error("IPC failed: ${e.message}", e, 0))
            }
        }
    }

    private suspend fun dispatchTier3(toolName: String, args: Map<String, Any?>, executionContext: ExecutionContext): ToolResult {
        if (!bindLatch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return ToolResult.Error("Tier 3 Sandbox bind timeout", null, 0)
        }
        val service = computeService ?: return ToolResult.Error("Tier 3 Sandbox unavailable", null, 0)
        
        return suspendCancellableCoroutine { cont ->
            val callback = object : IComputeResultCallback.Stub() {
                override fun onResult(jsonResult: String, latencyMs: Long) {
                    if (cont.isActive) {
                        if (jsonResult.startsWith("{\"error\"")) {
                            cont.resume(ToolResult.Error(jsonResult, null, latencyMs))
                        } else {
                            cont.resume(ToolResult.Success(jsonResult, latencyMs = latencyMs))
                        }
                    }
                }
            }
            try {
                service.compute(toolName, serializeArgs(args), callback)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(ToolResult.Error("IPC failed: ${e.message}", e, 0))
            }
        }
    }

    private fun serializeArgs(args: Map<String, Any?>): String {
        return JSONObject(args).toString()
    }
}

/**
 * [v2.0.0-SAR] ToolOutputSanitizer: Implements Microcompact.
 * Truncates massive tool outputs locally BEFORE they hit the LLM context window.
 */
@Singleton
class ToolOutputSanitizer @Inject constructor() {
    
    companion object {
        // Per-tool limits adapted for 8K context
        private val TOOL_LIMITS = mapOf(
            "web_fetch" to OutputLimit(maxChars = 4000, maxLines = 200),
            "file_read_internal" to OutputLimit(maxChars = 6000, maxLines = 300),
            "glob_internal" to OutputLimit(maxChars = 2000, maxLines = 100),
            "grep_internal" to OutputLimit(maxChars = 3000, maxLines = 150),
            "web_search" to OutputLimit(maxChars = 3000, maxLines = 150),
            "discover_openfda" to OutputLimit(maxChars = 5000, maxLines = 250)
        )
        
        private val DEFAULT_LIMIT = OutputLimit(maxChars = 2000, maxLines = 100)
    }
    
    fun sanitize(toolName: String, result: ToolResult): ToolResult {
        if (result !is ToolResult.Success) return result
        
        val limit = TOOL_LIMITS[toolName] ?: DEFAULT_LIMIT
        val data = result.data ?: return result
        
        val serialized = when (data) {
            is String -> data
            is Map<*, *> -> serializeMap(data)
            is List<*> -> serializeList(data)
            else -> data.toString()
        }
        
        return if (serialized.length > limit.maxChars || serialized.lines().size > limit.maxLines) {
            val truncated = truncateSmart(serialized, limit)
            ToolResult.Success(
                data = truncated,
                metadata = result.metadata + mapOf(
                    "truncated" to "true",
                    "original_chars" to serialized.length.toString(),
                    "original_lines" to serialized.lines().size.toString()
                ),
                latencyMs = result.latencyMs
            )
        } else {
            result
        }
    }
    
    /**
     * Smart truncation: preserve structure, add truncation marker
     */
    private fun truncateSmart(text: String, limit: OutputLimit): String {
        val lines = text.lines()
        
        return if (lines.size > limit.maxLines) {
            val kept = lines.take(limit.maxLines)
            val dropped = lines.size - limit.maxLines
            kept.joinToString("\n") + "\n\n[... $dropped lines truncated. Use search or grep tools to find specific content.]"
        } else {
            text.take(limit.maxChars) + "\n\n[... truncated at ${limit.maxChars} chars. Use specific read tools with offset to read more.]"
        }
    }
    
    private fun serializeMap(map: Map<*, *>): String {
        return map.entries.joinToString("\n") { (k, v) ->
            "$k: ${if (v is String && v.length > 200) v.take(200) + "..." else v}"
        }
    }
    
    private fun serializeList(list: List<*>): String {
        return list.joinToString("\n") { item ->
            if (item is String && item.length > 200) item.take(200) + "..." else item.toString()
        }
    }
}

data class OutputLimit(val maxChars: Int, val maxLines: Int)
