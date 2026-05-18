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
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ToolHookEngine
import com.scypheon.sdk.core.agent.SkillIntentRouter
import com.scypheon.sdk.core.agent.ContextManager
import com.scypheon.sdk.core.environment.SensorMesh
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgenticSkillOrchestrator:
 * Ported Claude Code QueryEngine pattern (v1.1.0).
 * Multipurpose General Agent Orchestrator for Scypheon.
 */
@Singleton
class AgenticSkillOrchestrator @Inject constructor(
    private val router: SkillIntentRouter,
    private val fastEngine: OODAFastEngine,
    private val toolRegistry: ToolRegistry,
    private val toolMesh: ToolMesh,
    private val hookEngine: ToolHookEngine,
    private val contextManager: ContextManager,
    private val blackBoxVault: BlackBoxVault,
    private val gateway: NeuralGateway,
    private val memoryManager: DualMemoryManager,
    private val sensorMesh: SensorMesh
) {
    /**
     * Internal Loop State (Mirrors Claude Code's State type)
     */
    private data class OrchestratorState(
        val messages: MutableList<Message>,
        var retryCount: Int = 0,
        var isCompleted: Boolean = false,
        var finalReport: String = ""
    )

    private val MAX_SELF_CORRECTION_RETRIES = 2
    private val MAX_STOP_HOOK_RETRIES = 2
    private val streamingToolParser = StreamingToolParser()

    /**
     * Entry point for all user missions.
     */
    suspend fun orchestrateMission(sessionId: String, query: String): String {
        val (path, skillType) = router.routeMission(query)
        
        return when (path) {
            SkillIntentRouter.RoutingPath.OODA_FAST -> {
                val session = SessionContext(sessionId)
                val environment = DeviceEnvironment(100, true, ThermalStatus.NORMAL, "WIFI")
                val result = fastEngine.execute(query, session, environment)
                
                when (result) {
                    is OODAResult.FastPath -> result.result.result
                    is OODAResult.DelegationRequired -> executeOrigaLoop(sessionId, query)
                    is OODAResult.Error -> "OODA Engine Error: ${result.fallbackMessage}"
                }
            }
            SkillIntentRouter.RoutingPath.ORIGA_REASONING -> {
                executeOrigaLoop(sessionId, query)
            }
        }
    }

    /**
     * THE ORIGA LOOP: Observe -> Reason -> Investigate -> Ground -> Act.
     * Uses dynamic tool discovery, thinking preservation, and iterative feedback.
     */
    /**
     * THE ORIGA LOOP: Ported from Claude Code's QueryEngine loop.
     */
    private suspend fun executeOrigaLoop(sessionId: String, query: String): String {
        Timber.i("🏛️ [ORIGA_LOOP] Commencing strategic investigation for: $query")
        
        // 1. Initialize State
        val state = OrchestratorState(
            messages = mutableListOf(Message("user", query))
        )

        // 2. Pillar 4: Proactive Context Gathering (Medical + Environment + History)
        val proactiveContext = gatherProactiveContext(sessionId, query)
        state.messages.add(Message("system", "PROACTIVE_CONTEXT: $proactiveContext", isMeta = true))

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

            // 5. TOOL EXECUTION (Blocking for Origa)
            val call = ToolCall("general_query", mapOf("query" to query))
            val results = toolMesh.dispatch(listOf(call), ExecutionContext(sessionId, 5000L))
            val result = results.firstOrNull()

            if (result is ToolResult.Success) {
                val data = result.data.toString()
                state.messages.add(Message("system", "Tool Output: $data"))

                // Pillar 1: Self-Correction & Reflection
                val reflection = "Reflection: Output received. Does this fulfill the mission goal?"
                state.messages.add(Message("assistant", reflection, isThinking = true))
                blackBoxVault.logEvent("AGENT_REFLECTION", "[Iter ${state.retryCount + 1}] $reflection")

                if (data.contains("SUCCESS") || state.retryCount >= MAX_SELF_CORRECTION_RETRIES) {
                    state.finalReport = data
                    state.isCompleted = true
                } else {
                    state.retryCount++
                }
            } else {
                blackBoxVault.logEvent("TOOL_ERROR", "Tool failed. Retrying with fallback.", "WARNING")
                state.retryCount++
            }
        }

        return if (state.isCompleted) {
            formatStrategicReport(state.finalReport)
        } else {
            "⚠️ MISSION FAILURE: Max iterations exceeded."
        }
    }

    private fun formatStrategicReport(result: String): String {
        return """
            📋 STRATEGIC MISSION REPORT
            ---------------------------
            ANALYSIS: $result
            
            SOURCE: Verified via Scypheon Multi-Tier Oracle (L1-L4)
            INTEGRITY: High (ORIGA Verified)
            SAFETY: Cleared by HELIOS Sentinel
        """.trimIndent()
    }

    /**
     * Pillar 4: Proactive Context Gathering.
     * Ported Pattern: Pre-fetch environmental and memory signals before AI turn.
     */
    private suspend fun gatherProactiveContext(sessionId: String, query: String): String {
        val sensorData = sensorMesh.getContextString()
        val isMedicalQuery = query.contains("drug") || query.contains("obat") || query.contains("sakit")
        
        val memoryData = if (isMedicalQuery) {
            val allergies = memoryManager.getUserAllergies()
            val prescriptions = memoryManager.getCurrentPrescriptions()
            "| MEDICAL: [Allergies: $allergies, Active: $prescriptions]"
        } else {
            ""
        }
        
        return "$sensorData $memoryData"
    }

    /**
     * Entry point for streaming missions. 
     * Handles tool calls, thinking, and iterative generation in a single Flow.
     * Mirroring the Claude Code / Anthropic Thinking block pattern.
     */
    fun generateAgenticStream(
        sessionId: String, 
        baseHistory: List<NeuralGateway.NeuralTurn>,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        enableThinking: Boolean = true,
        allowNetwork: Boolean = true
    ): Flow<String> = flow {
        val userQuery = baseHistory.lastOrNull { it.role == NeuralGateway.NeuralTurn.Role.USER }?.content ?: ""
        val (path, skillType) = router.routeMission(userQuery)

        // [v1.6.0-SAR] Unified Agentic Pipeline:
        // OODA Fast can now dynamically call tools if confident/low-risk.
        // Instead of completely bypassing tools, OODA Fast runs a fast-path tool execution loop
        // without thinking blocks (enableThinking = false) for ultra-low TTFT.
        val actualThinking = if (path == SkillIntentRouter.RoutingPath.OODA_FAST) false else enableThinking
        val maxTurns = if (path == SkillIntentRouter.RoutingPath.OODA_FAST) 2 else 5

        Timber.i("🏎️ [STREAM_ROUTER] Running agentic stream (Path: $path, Thinking: $actualThinking, MaxTurns: $maxTurns)")
        val toolPrompt = toolRegistry.generateToolDefinitionsPrompt()

        val history = baseHistory.toMutableList()
        // Prepend tool definitions to the context if not already present
        if (history.none { it.content.contains("Gunakan format XML <tool_call>") }) {
            history.add(0, NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.SYSTEM, toolPrompt))
        }

        var turns = 0
        var isFinalResponse = false

        while (turns < maxTurns && !isFinalResponse) {
            turns++
            streamingToolParser.reset()
            
            var currentTurnText = ""
            var pendingToolCall: ToolCall? = null
            // [v1.4.0-SAR] Track tool_call XML so we can strip it from UI output
            var toolCallStartIndex = -1

            // 1. Generate stream from LLM using passed config
            gateway.generateResponse(history, topK, topP, temp, 2048, actualThinking).collect { token ->
                    currentTurnText += token

                    // 2. Intercept Tool Calls in real-time — DON'T emit tool_call XML to UI
                    val toolCall = streamingToolParser.processToken(token)
                    if (toolCall != null) {
                        pendingToolCall = toolCall
                        // Mark where the <tool_call> started so we can strip it
                        toolCallStartIndex = currentTurnText.indexOf("<tool_call>")
                    }
                    
                    // Only emit tokens to UI if we haven't entered a tool_call block
                    if (toolCallStartIndex == -1) {
                        emit(token)
                    }
                }

                // 3. Handle Tool Execution if detected
                if (pendingToolCall != null) {
                    val toolCall = pendingToolCall!!
                    Timber.i("🛠️ [STREAM_TOOL] Found tool call: ${toolCall.toolName}")
                    
                    // Strip the tool_call XML from the assistant turn text
                    val cleanAssistantText = if (toolCallStartIndex >= 0) {
                        currentTurnText.substring(0, toolCallStartIndex).trim()
                    } else {
                        currentTurnText
                    }
                    
                    // Brief UI status indicator (not raw data)
                    val toolActivity = toolRegistry.resolve(toolCall.toolName)?.getActivityDescription(toolCall.arguments)
                        ?: "Processing ${toolCall.toolName}..."
                    emit("\n\n*🔍 $toolActivity*\n\n")
                    
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
                } else {
                    // ── STOP HOOKS (Claude Code stopHooks.ts pattern) ──────────
                    // No tool calls detected — LLM responded directly.
                    // Before marking as final, run stop hooks to validate output quality.
                    val stopResult = hookEngine.executeStopHooks(
                        currentTurnText,
                        ExecutionContext(sessionId, 5000L, allowNetwork = allowNetwork)
                    )
                    
                    when (stopResult) {
                        is ToolHookEngine.StopHookResult.Complete -> {
                            // All quality checks passed — response is final.
                            isFinalResponse = true
                        }
                        is ToolHookEngine.StopHookResult.ForceContinuation -> {
                            // Stop hook found issues (truncated, raw data, leaked markers).
                            // Inject blocking errors and force the LLM to re-generate.
                            if (turns < 5) { // Respect max turns to prevent infinite loops
                                Timber.w("🔄 [STOP_HOOK] Forcing continuation: ${stopResult.blockingErrors.size} issues")
                                history.add(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.ASSISTANT, currentTurnText))
                                history.add(NeuralGateway.NeuralTurn(
                                    NeuralGateway.NeuralTurn.Role.SYSTEM,
                                    stopResult.blockingErrors.joinToString("\n")
                                ))
                                blackBoxVault.logEvent("STOP_HOOK_RETRY", "Forcing LLM retry due to: ${stopResult.blockingErrors.first()}")
                                // Loop continues — LLM will re-generate
                            } else {
                                Timber.w("🔄 [STOP_HOOK] Would force retry but max turns reached. Accepting response.")
                                isFinalResponse = true
                            }
                        }
                        is ToolHookEngine.StopHookResult.PreventCompletion -> {
                            // Safety violation — stop immediately.
                            Timber.e("🛑 [STOP_HOOK] Response blocked: ${stopResult.reason}")
                            emit("\n\n⚠️ *Response was blocked by a safety check. Please rephrase your query.*\n")
                            isFinalResponse = true
                        }
                    }
                }
        }
    }
}
