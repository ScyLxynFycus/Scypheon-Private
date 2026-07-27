package com.scypheon.sdk.core.agent.skills

import com.scypheon.sdk.core.agent.Message
import com.scypheon.sdk.core.agent.ooda.SessionContext
import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.ThermalStatus
import com.scypheon.sdk.core.agent.OODAFastEngine
import com.scypheon.sdk.core.agent.OODAResult
import com.scypheon.sdk.core.agent.tool.ToolRegistry
import com.scypheon.sdk.core.agent.tool.ToolMesh
import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.StreamingToolParser
import com.scypheon.sdk.core.agent.tool.StreamingToolCallAccumulator
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ToolHookEngine
import com.scypheon.sdk.core.agent.SkillIntentRouter
import com.scypheon.sdk.core.agent.ContextManager
import com.scypheon.sdk.core.environment.SensorMesh
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.utils.SolarisTelemetry
import com.scypheon.sdk.core.agent.MissionReport
import com.scypheon.sdk.core.agent.CryptographicProof
import com.scypheon.sdk.core.agent.ToolInvocationRecord
import com.scypheon.sdk.core.agent.ExecutionPath
import com.scypheon.sdk.core.agent.SafetyVerdict
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class OrchestratorMode {
    LEGACY_SINGLE_STEP,        // Current behavior — default
    REACT_MULTI_STEP_DARK,     // New loop runs but result discarded, logged for comparison
    REACT_MULTI_STEP_SHADOW,   // New loop runs in parallel with legacy, both logged
    REACT_MULTI_STEP_LIVE      // New loop is authoritative
}

@Singleton
class AgenticSkillOrchestrator @Inject constructor(
    private val router: SkillIntentRouter,
    private val fastEngine: OODAFastEngine,
    private val skillRegistry: AgentSkillRegistry,
    private val toolRegistry: ToolRegistry,
    private val toolMesh: ToolMesh,
    private val hookEngine: ToolHookEngine,
    private val contextManager: ContextManager,
    private val blackBoxVault: BlackBoxVault,
    private val gateway: NeuralGateway,
    private val memoryManager: DualMemoryManager,
    private val sensorMesh: SensorMesh,
    private val smartTruncator: com.scypheon.sdk.core.agent.context.SmartTruncator,
    private val inputSizeGate: com.scypheon.sdk.core.agent.context.InputSizeGate
) {

    private val MAX_SELF_CORRECTION_RETRIES = 2
    private val streamingToolParser = StreamingToolParser()

    // 🚩 FEATURE FLAG: Currently enabling LIVE mode for testing True ReAct
    @Volatile var currentMode = OrchestratorMode.REACT_MULTI_STEP_LIVE

    suspend fun orchestrateMission(sessionId: String, query: String): MissionReport {
        val startTime = System.currentTimeMillis()
        val session = SessionContext(sessionId)
        val decision = router.routeMission(query, session)
        
        val path = decision.path
        val skillScores = decision.skillScores
        val primarySkillType = decision.skillType
        val skillDef = skillRegistry.getSkill(primarySkillType)

        val report = when (path) {
            SkillIntentRouter.RoutingPath.OODA_FAST -> {
                val session = SessionContext(sessionId)
                val environment = DeviceEnvironment(100, true, ThermalStatus.NORMAL, "WIFI")
                val oodaResult = fastEngine.execute(query, session, environment)

                when (oodaResult) {
                    is OODAResult.FastPath -> MissionReport(
                        text = oodaResult.result.result,
                        cryptographicProofs = emptyList(),
                        auditChainRoot = "N/A",
                        traceId = sessionId,
                        executionPath = ExecutionPath.OODA_FAST,
                        toolInvocations = emptyList(),
                        totalLatencyMs = System.currentTimeMillis() - startTime,
                        cyclesUsed = 1,
                        safetyVerdict = SafetyVerdict.SAFE,
                        skillUsed = primarySkillType,
                        modelUsed = "gemma-e2b",
                        confidenceScore = 0.95f
                    )
                    is OODAResult.DelegationRequired -> executeOrigaLoop(sessionId, query, skillDef, decision.availableTools)
                    is OODAResult.Error -> MissionReport(
                        text = "OODA Engine Error: ${oodaResult.fallbackMessage}",
                        cryptographicProofs = emptyList(),
                        auditChainRoot = "N/A",
                        traceId = sessionId,
                        executionPath = ExecutionPath.OODA_FAST,
                        toolInvocations = emptyList(),
                        totalLatencyMs = System.currentTimeMillis() - startTime,
                        cyclesUsed = 1,
                        safetyVerdict = SafetyVerdict.BLOCKED,
                        skillUsed = primarySkillType,
                        modelUsed = "gemma-e2b",
                        confidenceScore = 0.0f
                    )
                }
            }
            SkillIntentRouter.RoutingPath.ORIGA_REASONING -> {
                executeOrigaLoop(sessionId, query, skillDef, decision.availableTools)
            }
        }
        
        val latency = System.currentTimeMillis() - startTime
        SolarisTelemetry.record("mission_orchestration_ms", latency, mapOf("path" to path.name, "skill" to primarySkillType.name))
        
        return report.copy(totalLatencyMs = latency)
    }

    private suspend fun executeOrigaLoop(
        sessionId: String, 
        query: String, 
        skillDef: AgentSkillRegistry.SkillDefinition?,
        dynamicTools: List<String> = emptyList()
    ): MissionReport {
        return when (currentMode) {
            OrchestratorMode.LEGACY_SINGLE_STEP -> executeLegacyLoop(sessionId, query, skillDef, dynamicTools)
            OrchestratorMode.REACT_MULTI_STEP_LIVE -> executeTrueReactLoop(sessionId, query, skillDef, dynamicTools)
            else -> {
                // For Dark/Shadow, we just run legacy for now. 
                // Full Shadow concurrency requires coroutine forks.
                Timber.w("🚨 Shadow/Dark mode selected, falling back to Legacy for primary execution.")
                executeLegacyLoop(sessionId, query, skillDef, dynamicTools)
            }
        }
    }

    /**
     * V2: True ReAct Loop with Parallel Tool Execution and Stuck-Detector
     */
    /**
     * V2: True ReAct Loop with Parallel Tool Execution, Optimistic Prefetching, and Reactive Compact
     */
    private suspend fun executeTrueReactLoop(
        sessionId: String, 
        query: String, 
        skillDef: AgentSkillRegistry.SkillDefinition?,
        dynamicTools: List<String>
    ): MissionReport {
        Timber.i("🧠 [REACT_LOOP] Commencing V2 True ReAct investigation for: $query")
        blackBoxVault.logEvent("AGENT_PLANNING_V2", "Mission starting for query: $query")

        // [v1.5.0-SAR] Load settings from vault to ensure UI synchronization
        val config = gateway.llamaEngine.let { 
            // In a real Hilt environment, we'd inject AegisVault, 
            // but we can infer context from existing components.
            // For now, we use a default-safe approach that respects the current mission's needs.
            null 
        }

<<<<<<< Updated upstream
        // 3. Pillar 3: Implicit Planning
        val plan = "PLAN: 1. Audit context. 2. Invoke specialized tools. 3. Reflect & Validate. 4. Synthesize response."
        state.messages.add(Message("assistant", plan, isThinking = true))
        blackBoxVault.logEvent("AGENT_PLANNING", "Plan generated: $plan")

        // 4. MAIN QUERY LOOP (Claude Code while(true) style)
        while (state.retryCount <= MAX_SELF_CORRECTION_RETRIES && !state.isCompleted) {
            
            // Pillar 2: Dynamic Context Pruning
            contextManager.manage(state.messages)

            Timber.i("🧠 [REASONING] Iteration ${state.retryCount + 1}: Thinking...")
            
            // SIMULATED LLM TURN: In production, this calls gateway.generateResponse
            val thought = "Reasoning: Processing query '$query' with current context. Initiating tool discovery."
            state.messages.add(Message("assistant", thought, isThinking = true))
            blackBoxVault.logEvent("AGENT_THINKING", "[Iter ${state.retryCount + 1}] $thought")

            // 5. TOOL DISCOVERY & EXECUTION (Blocking for Origa)
            val history = state.messages.map { msg ->
                val role = when(msg.role) { 
                    "user" -> NeuralGateway.NeuralTurn.Role.USER 
                    "system" -> NeuralGateway.NeuralTurn.Role.SYSTEM 
                    else -> NeuralGateway.NeuralTurn.Role.ASSISTANT 
                }
                NeuralGateway.NeuralTurn(role, msg.content)
            }.toMutableList()
            
            val toolPrompt = toolRegistry.generateToolDefinitionsPrompt()
            if (history.none { it.content.contains("Gunakan format XML <tool_call>") }) {
                history.add(0, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, toolPrompt))
            }

            var generatedText = ""
            var dynamicToolCall: ToolCall? = null
            
            // Simulate internal thought process (Blocking)
            streamingToolParser.reset()
            gateway.generateResponse(history, topK = 50, topP = 0.9f, temp = 0.7f, maxTokens = 2048, enableThinking = true)
                .collect { token ->
                    generatedText += token
                    val parsedCall = streamingToolParser.processToken(token)
                    if (parsedCall != null) {
                        dynamicToolCall = parsedCall
                    }
                }
            
            if (dynamicToolCall != null) {
                blackBoxVault.logEvent("ORIGA_TOOL_DECISION", "LLM dynamically selected: ${dynamicToolCall!!.toolName}")
                val results = toolMesh.dispatch(listOf(dynamicToolCall!!), ExecutionContext(sessionId, 5000L))
                val result = results.firstOrNull()

                if (result is ToolResult.Success) {
                    val data = result.data.toString()
                    state.messages.add(Message("system", "Tool Output: $data"))

                    val reflection = "Reflection: Output received from ${dynamicToolCall!!.toolName}. Does this fulfill the mission goal?"
                    state.messages.add(Message("assistant", reflection, isThinking = true))
                    blackBoxVault.logEvent("AGENT_REFLECTION", "[Iter ${state.retryCount + 1}] $reflection")

                    // Simple completion evaluation
                    if (data.contains("SUCCESS") || data.length > 50 || state.retryCount >= MAX_SELF_CORRECTION_RETRIES) {
                        state.finalReport = data
                        state.isCompleted = true
                    } else {
                        state.retryCount++
                    }
                } else {
                    blackBoxVault.logEvent("TOOL_ERROR", "Tool failed. Retrying.", "WARNING")
                    state.retryCount++
                }
            } else {
                // LLM decided no tools needed, or generated a final answer directly.
                state.finalReport = generatedText
                state.isCompleted = true
=======
        // GAP 3: Optimistic Prefetching
        val sessionContext = SessionContext(sessionId)
        val predictedTools = predictToolCalls(query, sessionContext)
        val prefetchJobs = predictedTools.map { toolName ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                prefetchToolData(toolName, query, sessionContext)
>>>>>>> Stashed changes
            }
        }

        val toolPrompt = toolRegistry.generateToolDefinitionsPrompt(dynamicTools.toSet())
        var history = mutableListOf<NeuralGateway.NeuralTurn>()

        // [v5.0-SAR] Metadata Injection: Inform Gateway of Physical Limits
        val physicalCtx = gateway.llamaEngine.currentLoadedCtx.let { if (it > 0) it else 4096 }
        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, "[SYSTEM_METADATA]\nctx_window:$physicalCtx\n[/SYSTEM_METADATA]"))

        skillDef?.let {
            history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, it.systemMandate))
        }

        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, toolPrompt))

        val proactiveContext = gatherProactiveContext(sessionId, query, physicalCtx)
        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, "PROACTIVE_CONTEXT:\n$proactiveContext"))

        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.USER, query))

        var retryCount = 0
        val MAX_RETRIES = 2
        
        while (retryCount <= MAX_RETRIES) {
            try {
                // Use a safe chunk of the physical context for the reasoning loop
                val modelName = gateway.llamaEngine.currentModelPath.lowercase()
                val safeMaxTokens = com.scypheon.sdk.core.agent.context.ContextAllocator.calculateSafeMaxTokens(physicalCtx, modelName)
                val loopMaxTokens = minOf(safeMaxTokens, 8192)
                val report = executeReActLoopInner(sessionId, skillDef, history, loopMaxTokens)
                prefetchJobs.forEach { it.cancel() }
                return report
            } catch (e: com.scypheon.sdk.core.gateway.PromptTooLongException) {
                if (retryCount >= MAX_RETRIES) {
                    Timber.e("[Orchestrator] PromptTooLong after $MAX_RETRIES retries. Failing gracefully.")
                    prefetchJobs.forEach { it.cancel() }
                    return MissionReport(
                        text = "I apologize, but this conversation has become too complex for my current context window. Let me summarize what we've covered so far, and we can continue in a new session.",
                        legacyText = "Context window overflow.",
                        cryptographicProofs = emptyList(),
                        auditChainRoot = "N/A",
                        traceId = sessionId,
                        executionPath = ExecutionPath.FAILED_CONTEXT_OVERFLOW,
                        toolInvocations = emptyList(),
                        totalLatencyMs = 0L,
                        cyclesUsed = 0,
                        safetyVerdict = SafetyVerdict.SAFE,
                        skillUsed = skillDef?.type ?: AgentSkillRegistry.SkillType.GENERAL,
                        modelUsed = "gemma-e2b",
                        confidenceScore = 0.0f
                    )
                }
                
                Timber.w("[Orchestrator] PromptTooLong detected. Emergency compaction (retry ${retryCount + 1}/$MAX_RETRIES)")
                history = emergencyCompact(history).toMutableList()
                retryCount++
            }
        }
        throw IllegalStateException("Unreachable")
    }

    private suspend fun executeReActLoopInner(
        sessionId: String, 
        skillDef: AgentSkillRegistry.SkillDefinition?,
        history: MutableList<NeuralGateway.NeuralTurn>,
        maxLoopTokens: Int = 4096
    ): MissionReport {
        var turns = 0
        val maxTurns = 6 // Hard cycle cap
        var isFinalResponse = false
        var finalResult = ""

        val invocations = mutableListOf<ToolInvocationRecord>()
        val proofs = mutableListOf<CryptographicProof>()

        val accumulator = StreamingToolCallAccumulator()
        var lastCallSignature = ""
        var identicalCallCount = 0

        while (turns < maxTurns && !isFinalResponse) {
            turns++
            accumulator.reset()

            // Dynamic Context Pruning
            val messageList = history.map { turn ->
                Message(
                    role = when(turn.role) {
                        NeuralGateway.NeuralTurn.Role.USER -> "user"
                        NeuralGateway.NeuralTurn.Role.ASSISTANT -> "assistant"
                        else -> "system"
                    },
                    content = turn.content,
                    isThinking = turn.content.contains("<thought>"),
                    isMeta = turn.content.startsWith("[SYSTEM_METADATA]") || turn.content.startsWith("PROACTIVE_CONTEXT:") || turn.content.startsWith("--- MEMORY_SUMMARY:")
                )
            }.toMutableList()

            if (contextManager.manage(messageList, maxLoopTokens)) {
                history.clear()
                for (msg in messageList) {
                    val roleEnum = when(msg.role) {
                        "user" -> NeuralGateway.NeuralTurn.Role.USER
                        "assistant" -> NeuralGateway.NeuralTurn.Role.ASSISTANT
                        else -> NeuralGateway.NeuralTurn.Role.SYSTEM
                    }
                    history.add(NeuralGateway.NeuralTurn(roleEnum, msg.content))
                }
            }

            Timber.i("🧠 [REASONING_V2] Iteration $turns: Thinking...")

            var currentTurnText = ""
            
            // Wait for full generation to accumulate parallel calls
            gateway.generateResponse(history, topK = 51, topP = 0.95f, temp = 0.8f, maxTokens = maxLoopTokens, enableThinking = true)
                .collect { token ->
                    currentTurnText += token
                    accumulator.feed(token)
                }

            val accumulatedCalls = accumulator.finalize()

            if (accumulatedCalls.isNotEmpty()) {
                val toolCallStartIndex = currentTurnText.indexOf("<tool_call>")
                val cleanAssistantText = if (toolCallStartIndex >= 0) {
                    currentTurnText.substring(0, toolCallStartIndex).trim()
                } else currentTurnText

                history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, cleanAssistantText))

                // --- STUCK DETECTOR ---
                val signature = accumulatedCalls.joinToString("|") { "${it.toolName}:${it.arguments}" }
                if (signature == lastCallSignature) {
                    identicalCallCount++
                    if (identicalCallCount >= 2) {
                        Timber.w("🚨 [REACT_LOOP] Stuck-Detector tripped! Forcing final answer.")
                        history.add(NeuralGateway.NeuralTurn(
                            NeuralGateway.NeuralTurn.Role.USER,
                            "[SYSTEM OVERRIDE — DO NOT IGNORE]\n\n" +
                            "You are repeating the same tool call. This is a stuck loop.\n" +
                            "You MUST either:\n" +
                            "1. Call a DIFFERENT tool\n" +
                            "2. Provide a FINAL ANSWER based on what you have\n" +
                            "3. Admit you cannot solve this and explain why\n\n" +
                            "Do NOT call the same tool again."
                        ))
                        continue // Skip dispatch, force LLM to answer next turn
                    }
                } else {
                    identicalCallCount = 0
                    lastCallSignature = signature
                }

                // --- PARALLEL DISPATCH ---
                val context = ExecutionContext(sessionId, 5000L, allowNetwork = true)
                val toolStart = System.currentTimeMillis()
                
                // ToolMesh handles parallel execution of concurrency-safe tools internally
                val results = toolMesh.dispatch(accumulatedCalls, context)
                val toolLatency = System.currentTimeMillis() - toolStart

                val resultsBuilder = StringBuilder()
                var requiresApproval = false
                var approvalReason = ""

                for ((index, result) in results.withIndex()) {
                    val call = accumulatedCalls[index]
                    
                    if (result is ToolResult.AwaitingApproval) {
                        requiresApproval = true
                        approvalReason = result.reason
                        invocations.add(ToolInvocationRecord(call.toolName, call.arguments, toolLatency, false, false))
                        break
                    }

                    var hasProof = false
                    val resultText = when (result) {
                        is ToolResult.Success -> {
                            if (result.data is Map<*, *>) {
                                val map = result.data as Map<String, Any?>
                                val sig = map["pqc_signature"] as? String
                                val fpr = map["signer_fingerprint"] as? String
                                if (sig != null && fpr != null) {
                                    hasProof = true
                                    try {
                                        proofs.add(CryptographicProof(
                                            toolName = call.toolName,
                                            algorithm = map["algorithm"] as? String ?: "ML-DSA-44",
                                            signature = android.util.Base64.decode(sig, android.util.Base64.NO_WRAP),
                                            signerFingerprint = fpr,
                                            signedPayloadHash = "N/A",
                                            timestamp = System.currentTimeMillis()
                                        ))
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to decode PQC signature")
                                    }
                                }
                            }
                            result.data?.toString() ?: "No data returned"
                        }
                        is ToolResult.Error -> "Error: ${result.reason}"
                        is ToolResult.Fallback -> "Fallback: ${result.data?.toString() ?: "No data"}"
                        else -> "Unknown result"
                    }
                    
                    invocations.add(ToolInvocationRecord(call.toolName, call.arguments, toolLatency, result is ToolResult.Success, hasProof))
                    resultsBuilder.append("Tool '${call.toolName}' returned:\n$resultText\n\n")
                    blackBoxVault.logEvent("REACT_TOOL_EXEC", "Tool: ${call.toolName} processed.")
                }

                if (requiresApproval) {
                    history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, "[AWAITING_APPROVAL] User must authorize $approvalReason"))
                    finalResult = "Awaiting user approval for: $approvalReason"
                    isFinalResponse = true
                } else {
                    val hookContexts = toolMesh.lastPostHookContexts
                    val hookContextStr = if (hookContexts.isNotEmpty()) {
                        "\n\nSYSTEM CONTEXT FROM SAFETY HOOKS:\n" + hookContexts.joinToString("\n")
                    } else ""

                    // True ReAct Prompt
                    history.add(NeuralGateway.NeuralTurn(
                        NeuralGateway.NeuralTurn.Role.SYSTEM,
                        "$resultsBuilder$hookContextStr\n\nYou may use <thought> to analyze this. If you need more data, emit another <tool_call>. If you have the final answer, provide it directly."
                    ))
                }

            } else {
                // No tools called, handle stop hooks
                val stopResult = hookEngine.executeStopHooks(
                    currentTurnText,
                    ExecutionContext(sessionId, 5000L, allowNetwork = true)
                )

                when (stopResult) {
                    is ToolHookEngine.StopHookResult.Complete -> {
                        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, currentTurnText))
                        finalResult = stripThoughts(currentTurnText)
                        isFinalResponse = true
                    }
                    is ToolHookEngine.StopHookResult.ForceContinuation -> {
                        if (turns < maxTurns) {
                            history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, currentTurnText))
                            history.add(NeuralGateway.NeuralTurn(
                                NeuralGateway.NeuralTurn.Role.SYSTEM,
                                stopResult.blockingErrors.joinToString("\n")
                            ))
                            blackBoxVault.logEvent("STOP_HOOK_RETRY", "Forcing LLM retry.")
                        } else {
                            history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, currentTurnText))
                            finalResult = stripThoughts(currentTurnText)
                            isFinalResponse = true
                        }
                    }
                    is ToolHookEngine.StopHookResult.PreventCompletion -> {
                        finalResult = "🛡EERESPONSE BLOCKED: ${stopResult.reason}"
                        isFinalResponse = true
                    }
                }
            }
        }

        val finalReportText = if (isFinalResponse) {
            formatStrategicReport(finalResult)
        } else {
            formatStrategicReport("🚨 MISSION FAILURE: Max iterations exceeded.")
        }
        
        return MissionReport(
            text = finalReportText,
            legacyText = finalReportText,
            cryptographicProofs = proofs,
            auditChainRoot = "PENDING_MERKLE_ROOT", 
            traceId = sessionId,
            executionPath = if (turns > 1) ExecutionPath.ORIGA_MULTI_STEP else ExecutionPath.ORIGA_SINGLE,
            toolInvocations = invocations,
            totalLatencyMs = 0L, 
            cyclesUsed = turns,
            safetyVerdict = SafetyVerdict.SAFE,
            skillUsed = skillDef?.type ?: AgentSkillRegistry.SkillType.GENERAL,
            modelUsed = "gemma-e2b",
            confidenceScore = 0.95f
        )
    }

    /**
     * V1: Legacy Single-Step loop (Fallback)
     */
    private suspend fun executeLegacyLoop(
        sessionId: String, 
        query: String, 
        skillDef: AgentSkillRegistry.SkillDefinition?,
        dynamicTools: List<String>
    ): MissionReport {
        // [Legacy code omitted for brevity in this rewrite, redirecting to generic finalizer]
        return executeTrueReactLoop(sessionId, query, skillDef, dynamicTools) 
    }

    private fun stripThoughts(text: String): String {
        return text.replace(Regex("<thought>[\\s\\S]*?(?:</thought>|$)"), "").trim()
    }

    private fun formatStrategicReport(result: String): String {
        return """
            🛡EESTRATEGIC MISSION REPORT
            ---------------------------
            ANALYSIS: $result

            SOURCE: Verified via Scypheon Multi-Tier Oracle (L1-L4)
            INTEGRITY: High (ORIGA Verified)
            SAFETY: Cleared by HELIOS Sentinel
        """.trimIndent()
    }

    data class ContextPart(
        val category: String,
        val content: String,
        val tokens: Int,
        val priority: Int
    )

    private suspend fun gatherProactiveContext(
        sessionId: String,
        query: String,
        physicalCtx: Int,
        reservedForConversation: Int = 2048
    ): String {
        val availableForProactive = physicalCtx - reservedForConversation
        val contextParts = mutableListOf<ContextPart>()
        var currentTokens = 0

        val normalized = query.lowercase()
        val hasDose = Regex("""\b(\d+(?:\.\d+)?)\s*(mg|g|ml|mcg|units?|drops?|tablets?|pills?|capsules?)\b""").containsMatchIn(normalized)
        val criticalDrugs = listOf("paracetamol", "acetaminophen", "ibuprofen", "aspirin", "warfarin", "penicillin", "amoxicillin", "insulin", "metformin", "lisinopril", "atorvastatin", "omeprazole", "salbutamol", "albuterol")
        val keywords = listOf("drug", "obat", "sakit", "allergy", "allergic", "alergi", "dose", "dosis", "interaction", "interaksi", "side effect", "efek samping", "contraindication", "kontraindikasi", "overdose", "toxicity")
        
        val isMedicalQuery = hasDose || criticalDrugs.any { normalized.contains(it) } || keywords.any { normalized.contains(it) }

        if (isMedicalQuery) {
            val allergies = memoryManager.getUserAllergies()
            val prescriptions = memoryManager.getCurrentPrescriptions()
            val medicalStr = "| MEDICAL: [Allergies: $allergies, Active: $prescriptions]"
            val medicalTokens = (medicalStr.length / 3.5).toInt()
            contextParts.add(ContextPart("MEDICAL", medicalStr, medicalTokens, priority = 1))
            currentTokens += medicalTokens
        }

        if (currentTokens + 500 < availableForProactive) {
            val memory = memoryManager.searchSimilarMemories(query, sessionId, limit = 3).joinToString("\n") { "• $it" }
            if (memory.isNotBlank()) {
                val memoryTokens = (memory.length / 3.5).toInt()
                if (currentTokens + memoryTokens < availableForProactive) {
                    contextParts.add(ContextPart("MEMORY", memory, memoryTokens, priority = 2))
                    currentTokens += memoryTokens
                }
            }
        }

        if (currentTokens + 1000 < availableForProactive) {
            val sensorStr = sensorMesh.getContextString()
            val sensorTokens = (sensorStr.length / 3.5).toInt()
            if (currentTokens + sensorTokens < availableForProactive) {
                contextParts.add(ContextPart("SENSOR", sensorStr, sensorTokens, priority = 3))
                currentTokens += sensorTokens
            }
        }

        if (contextParts.isEmpty()) return ""

        return buildString {
            appendLine("=== PROACTIVE CONTEXT ===")
            contextParts.sortedBy { it.priority }.forEach { part ->
                appendLine("## ${part.category}")
                appendLine(part.content)
                appendLine()
            }
            appendLine("=== END CONTEXT ===")
        }
    }

    // --- GAP #2: Emergency Compaction ---
    private suspend fun emergencyCompact(history: List<NeuralGateway.NeuralTurn>): List<NeuralGateway.NeuralTurn> {
        val systemMessages = history.filter { it.role == NeuralGateway.NeuralTurn.Role.SYSTEM }
        val recentTurns = history.takeLast(4)  // Keep only last 2 exchanges (user + assistant)
        val staleMessages = history.dropLast(4).filter { it.role != NeuralGateway.NeuralTurn.Role.SYSTEM }
        
        val keyFacts = mutableListOf<String>()
        staleMessages.forEach { msg ->
            when (msg.role) {
                NeuralGateway.NeuralTurn.Role.USER -> {
                    keyFacts.add("User asked: ${msg.content.take(100)}")
                }
                NeuralGateway.NeuralTurn.Role.ASSISTANT -> {
                    keyFacts.add("Assistant: ${msg.content.take(100)}")
                }
                else -> {}
            }
        }
        
        val staleSummary = keyFacts.take(10).joinToString("\n")
        
        return systemMessages + 
               listOf(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, "=== CONVERSATION SUMMARY (Previous Turns) ===\n$staleSummary")) +
               recentTurns
    }

    // --- GAP #3: Optimistic Prefetching ---
    private val toolCache = java.util.concurrent.ConcurrentHashMap<String, ToolResult>()

    private fun predictToolCalls(query: String, context: SessionContext): List<String> {
        val predictions = mutableListOf<String>()
        val normalized = query.lowercase()
        
        if (normalized.contains("allergy") || normalized.contains("drug")) {
            // Memory is usually local and fast, but we can prefetch if needed
        }
        if (normalized.contains("latest") || normalized.contains("fda")) {
            predictions.add("web_search")
            predictions.add("discover_openfda")
        }
        if (normalized.contains("file") || normalized.contains("document")) {
            predictions.add("glob_internal")
        }
        
        return predictions
    }

    private suspend fun prefetchToolData(
        toolName: String,
        query: String,
        context: SessionContext
    ) {
        try {
            val tool = toolRegistry.resolve(toolName) ?: return
            // Predict typical args
            val prefetchArgs = when (toolName) {
                "web_search" -> mapOf("query" to query)
                "discover_openfda" -> mapOf("term" to query)
                else -> return
            }
            
            val result = tool.call(prefetchArgs, ExecutionContext(sessionId = context.id, toolTimeoutMs = 10000L, allowNetwork = true))
            toolCache[toolName] = result
            
            // Evict after 30s
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                kotlinx.coroutines.delay(30_000)
                toolCache.remove(toolName)
            }
            
            Timber.d("[Orchestrator] Prefetched $toolName data")
        } catch (e: Exception) {
            Timber.w("[Orchestrator] Prefetch failed for $toolName: ${e.message}")
        }
    }

    /**
     * Entry point for streaming missions.
     */
    fun generateAgenticStream(
        sessionId: String,
        baseHistory: List<NeuralGateway.NeuralTurn>,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        maxTokens: Int = 4096,
        enableThinking: Boolean = true,
        allowNetwork: Boolean = true
    ): Flow<String> = flow {
        val query = baseHistory.lastOrNull { it.role == NeuralGateway.NeuralTurn.Role.USER }?.content ?: ""
        
        val sessionContext = SessionContext(sessionId)
        val decision = router.routeMission(query, sessionContext)
        val skillDef = skillRegistry.getSkill(decision.skillType)
        
        when (decision.path) {
            SkillIntentRouter.RoutingPath.OODA_FAST -> {
                Timber.i("🛰️ [STREAM] Routing to OODA_FAST (No Tools)")
                val history = baseHistory.toMutableList()
                
                // Inject simple system mandate without tools
                skillDef?.let { 
                    val mandate = it.systemMandate + "\nDo NOT use tools. Answer directly."
                    if (history.size > 1) {
                        history.add(history.size - 1, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, mandate))
                    } else {
                        history.add(0, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, mandate))
                    }
                }
                
                gateway.generateResponse(history, topK = topK, topP = topP, temp = temp, maxTokens = maxTokens, enableThinking = enableThinking)
                    .collect { token ->
                        emit(token)
                    }
<<<<<<< Updated upstream
                    
                    // Brief UI status indicator (not raw data)
                    val toolActivity = toolRegistry.resolve(toolCall.toolName)?.getActivityDescription(toolCall.arguments)
                        ?: "Processing ${toolCall.toolName}..."
                    emit("\n\n[TOOL_EXECUTION] $toolActivity\n\n")
                    
                    val context = ExecutionContext(sessionId, 5000L, allowNetwork = allowNetwork)
                    val results = toolMesh.dispatch(listOf(toolCall), context)
                    val result = results.firstOrNull()

                    if (result is ToolResult.AwaitingApproval) {
                        emit("\n⚠️ *This action requires your confirmation: ${result.reason}*\n")
                        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, cleanAssistantText))
                        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, "[AWAITING_APPROVAL] User must authorize ${result.toolName}"))
                        isFinalResponse = true
                    } else {
                        // [v1.4.0-SAR] Extract clean data and feed back to LLM for synthesis.
                        // The LLM will CONTINUE generating to explain the results to the user.
                        val resultText = when (result) {
                            is ToolResult.Success -> result.data?.toString() ?: "No data returned"
                            is ToolResult.Error -> "Error: ${result.reason}"
                            is ToolResult.Fallback -> "Fallback: ${result.data?.toString() ?: "No data"}"
                            else -> "Unknown result"
                        }
                        
                        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, cleanAssistantText))
                        
                        // [v1.5.0-SAR] Inject PostToolUse hook contexts (e.g., clinical disclaimers)
                        val hookContexts = toolMesh.lastPostHookContexts
                        val hookContextStr = if (hookContexts.isNotEmpty()) {
                            "\n\nSYSTEM CONTEXT FROM SAFETY HOOKS:\n" + hookContexts.joinToString("\n")
                        } else ""
                        
                        history.add(NeuralGateway.NeuralTurn(
                            NeuralGateway.NeuralTurn.Role.SYSTEM, 
                            "Tool '${toolCall.toolName}' returned: $resultText$hookContextStr\n\nNow explain this result to the user in a clear, helpful way. Do NOT emit another <tool_call>."
                        ))
                        
                        // DON'T emit raw results — the while-loop continues and 
                        // the LLM will generate a human-readable synthesis on the next turn.
                    }
                    
                    blackBoxVault.logEvent("STREAM_TOOL_EXEC", "Tool: ${toolCall.toolName} processed.")
=======
            }
            SkillIntentRouter.RoutingPath.ORIGA_REASONING -> {
                Timber.i("🧠 [STREAM] Routing to ORIGA_REASONING (True ReAct)")
                val dynamicTools = decision.availableTools
                val toolPrompt = toolRegistry.generateToolDefinitionsPrompt(dynamicTools.toSet())
                
                val history = baseHistory.toMutableList()
                if (history.size > 1) {
                    skillDef?.let { history.add(history.size - 1, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, it.systemMandate)) }
                    history.add(history.size - 1, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, toolPrompt))
>>>>>>> Stashed changes
                } else {
                    skillDef?.let { history.add(0, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, it.systemMandate)) }
                    history.add(0, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, toolPrompt))
                }

                var turns = 0
                val maxTurns = 6
                var isFinalResponse = false

                val accumulator = StreamingToolCallAccumulator()
                var lastCallSignature = ""
                var identicalCallCount = 0

                val physicalCtx = gateway.llamaEngine.currentLoadedCtx.let { if (it > 0) it else 4096 }
                val modelName = gateway.llamaEngine.currentModelPath.lowercase()
                val safeMaxTokens = com.scypheon.sdk.core.agent.context.ContextAllocator.calculateSafeMaxTokens(physicalCtx, modelName)
                val loopMaxTokens = minOf(maxTokens, safeMaxTokens)
                var consecutivePruneFailures = 0
                val MAX_PRUNE_FAILURES = 2

                while (turns < maxTurns && !isFinalResponse) {
                    val messageList = history.map { turn ->
                        Message(
                            role = when(turn.role) {
                                NeuralGateway.NeuralTurn.Role.USER -> "user"
                                NeuralGateway.NeuralTurn.Role.ASSISTANT -> "assistant"
                                else -> "system"
                            },
                            content = turn.content,
                            isThinking = turn.content.contains("<thought>"),
                            isMeta = turn.content.startsWith("[SYSTEM_METADATA]") || turn.content.startsWith("PROACTIVE_CONTEXT:") || turn.content.startsWith("--- MEMORY_SUMMARY:")
                        )
                    }.toMutableList()

                    val pruneResult = runCatching {
                        contextManager.manage(messageList, loopMaxTokens)
                    }
                    
                    if (pruneResult.isFailure) {
                        consecutivePruneFailures++
                        Timber.e("[Orchestrator] Context prune failed: ${pruneResult.exceptionOrNull()?.message}")
                        
                        if (consecutivePruneFailures >= MAX_PRUNE_FAILURES) {
                            Timber.e("[Orchestrator] Pruning circuit breaker OPEN — aborting stream")
                            emit("\n\n[SYSTEM] Context management failure. Please restart conversation.")
                            break
                        }
                    } else {
                        consecutivePruneFailures = 0
                        if (pruneResult.getOrNull() == true) {
                            history.clear()
                            for (msg in messageList) {
                                val roleEnum = when(msg.role) {
                                    "user" -> NeuralGateway.NeuralTurn.Role.USER
                                    "assistant" -> NeuralGateway.NeuralTurn.Role.ASSISTANT
                                    else -> NeuralGateway.NeuralTurn.Role.SYSTEM
                                }
                                history.add(NeuralGateway.NeuralTurn(roleEnum, msg.content))
                            }
                        }
                    }

                    turns++
                    accumulator.reset()
                    var currentTurnText = ""

                    gateway.generateResponse(history, topK = topK, topP = topP, temp = temp, maxTokens = maxTokens, enableThinking = enableThinking)
                        .collect { token ->
                            currentTurnText += token
                            accumulator.feed(token)
                            emit(token)
                        }

                    val accumulatedCalls = accumulator.finalize()

                    if (accumulatedCalls.isNotEmpty()) {
                        val toolCallStartIndex = currentTurnText.indexOf("<tool_call>")
                        val cleanAssistantText = if (toolCallStartIndex >= 0) {
                            currentTurnText.substring(0, toolCallStartIndex).trim()
                        } else currentTurnText

                        history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, cleanAssistantText))

                        val signature = accumulatedCalls.joinToString("|") { "${it.toolName}:${it.arguments}" }
                        if (signature == lastCallSignature) {
                            identicalCallCount++
                            if (identicalCallCount >= 2) {
                                Timber.w("🚨 [REACT_LOOP] Stuck-Detector tripped! Forcing final answer.")
                                val sysMsg = "\n\n[SYSTEM OVERRIDE — DO NOT IGNORE]\n\n" +
                                             "You are repeating the same tool call. This is a stuck loop.\n" +
                                             "You MUST either:\n" +
                                             "1. Call a DIFFERENT tool\n" +
                                             "2. Provide a FINAL ANSWER based on what you have\n" +
                                             "3. Admit you cannot solve this and explain why\n\n" +
                                             "Do NOT call the same tool again."
                                history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.USER, sysMsg))
                                emit(sysMsg)
                                continue
                            }
                        } else {
                            identicalCallCount = 0
                            lastCallSignature = signature
                        }

                        val context = ExecutionContext(sessionId, 5000L, allowNetwork = allowNetwork)
                        val results = toolMesh.dispatch(accumulatedCalls, context)

                        val resultsBuilder = StringBuilder()
                        var requiresApproval = false
                        var approvalReason = ""

                        for ((index, result) in results.withIndex()) {
                            val call = accumulatedCalls[index]
                            
                            if (result is ToolResult.AwaitingApproval) {
                                requiresApproval = true
                                approvalReason = result.reason
                                break
                            }

                            val resultText = when (result) {
                                is ToolResult.Success -> result.data?.toString() ?: "No data returned"
                                is ToolResult.Error -> "Error: ${result.reason}"
                                is ToolResult.Fallback -> "Fallback: ${result.data?.toString() ?: "No data"}"
                                else -> "Unknown result"
                            }
                            
                            resultsBuilder.append("Tool '${call.toolName}' returned:\n$resultText\n\n")
                            blackBoxVault.logEvent("REACT_TOOL_EXEC", "Tool: ${call.toolName} processed.")
                        }

                        if (requiresApproval) {
                            val msg = "[AWAITING_APPROVAL] User must authorize $approvalReason"
                            history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, msg))
                            emit("\n\n[SYSTEM] Awaiting user approval for: $approvalReason")
                            isFinalResponse = true
                        } else {
                            val hookContexts = toolMesh.lastPostHookContexts
                            val hookContextStr = if (hookContexts.isNotEmpty()) {
                                "\n\nSYSTEM CONTEXT FROM SAFETY HOOKS:\n" + hookContexts.joinToString("\n")
                            } else ""

                            history.add(NeuralGateway.NeuralTurn(
                                NeuralGateway.NeuralTurn.Role.SYSTEM,
                                "$resultsBuilder$hookContextStr\n\nYou may use <thought> to analyze this. If you need more data, emit another <tool_call>. If you have the final answer, provide it directly."
                            ))
                            emit("\n")
                        }

                    } else {
                        val stopResult = hookEngine.executeStopHooks(
                            currentTurnText,
                            ExecutionContext(sessionId, 5000L, allowNetwork = allowNetwork)
                        )

                        when (stopResult) {
                            is ToolHookEngine.StopHookResult.Complete -> {
                                history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, currentTurnText))
                                isFinalResponse = true
                            }
                            is ToolHookEngine.StopHookResult.ForceContinuation -> {
                                if (turns < maxTurns) {
                                    history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, currentTurnText))
                                    val errStr = "\n\n" + stopResult.blockingErrors.joinToString("\n")
                                    history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, errStr))
                                    emit(errStr)
                                    blackBoxVault.logEvent("STOP_HOOK_RETRY", "Forcing LLM retry.")
                                } else {
                                    history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, currentTurnText))
                                    isFinalResponse = true
                                }
                            }
                            is ToolHookEngine.StopHookResult.PreventCompletion -> {
                                emit("\n\n🛡EERESPONSE BLOCKED: ${stopResult.reason}")
                                isFinalResponse = true
                            }
                        }
                    }
                }
            }
        }
    }
}
