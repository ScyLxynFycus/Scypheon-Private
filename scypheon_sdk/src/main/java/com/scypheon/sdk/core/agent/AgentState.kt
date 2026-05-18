package com.scypheon.sdk.core.agent
 
import com.scypheon.sdk.core.agent.tool.ToolCall

sealed class AgentState {
    object Idle : AgentState()
    data class Reasoning(val iteration: Int, val tokenBudget: Int) : AgentState()
    data class Executing(val toolCalls: List<ToolCall>) : AgentState()
    data class Validating(val resultIds: List<String>) : AgentState()
    data class Synthesizing(val groundedContextId: String) : AgentState()
    data class Blocked(val reason: String, val traceId: String) : AgentState()
    data class Throttled(val backoffMs: Long) : AgentState()
}

object AgentStateSerializer {
    /**
     * Primitive manual serializer for Hackathon PR #1.
     * Avoids dependency issues with kotlinx.serialization in strict environments.
     */
    fun serialize(state: AgentState): String = when (state) {
        is AgentState.Idle -> "IDLE"
        is AgentState.Reasoning -> "REASONING|${state.iteration}|${state.tokenBudget}"
        is AgentState.Executing -> "EXECUTING|${state.toolCalls.joinToString(",") { it.callId }}"
        is AgentState.Validating -> "VALIDATING|${state.resultIds.joinToString(",")}"
        is AgentState.Synthesizing -> "SYNTHESIZING|${state.groundedContextId}"
        is AgentState.Blocked -> "BLOCKED|${state.reason}|${state.traceId}"
        is AgentState.Throttled -> "THROTTLED|${state.backoffMs}"
    }

    fun deserialize(raw: String): AgentState {
        val parts = raw.split("|")
        return when (parts[0]) {
            "IDLE" -> AgentState.Idle
            "REASONING" -> AgentState.Reasoning(parts[1].toInt(), parts[2].toInt())
            "EXECUTING" -> AgentState.Executing(
                if (parts[1].isEmpty()) emptyList() 
                else parts[1].split(",").map { ToolCall(toolName = "unknown", arguments = emptyMap(), callId = it) }
            )
            "VALIDATING" -> AgentState.Validating(if (parts[1].isEmpty()) emptyList() else parts[1].split(","))
            "SYNTHESIZING" -> AgentState.Synthesizing(parts[1])
            "BLOCKED" -> AgentState.Blocked(parts[1], parts[2])
            "THROTTLED" -> AgentState.Throttled(parts[1].toLong())
            else -> AgentState.Idle
        }
    }
}
