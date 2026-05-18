package com.scypheon.sdk.core.swarm

import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.flow.reduce
import timber.log.Timber

/**
 * Base template for an autonomous sub-agent in the Swarm ecosystem.
 */
abstract class BaseAgent(
    protected val name: String,
    protected val roleInstruction: String,
    protected val gateway: NeuralGateway
) {
    /**
     * Executes a sub-task. The agent can choose to reject the task if it's out of scope.
     */
    open suspend fun executeTask(task: String): String {
        Timber.i("🤖 Agent [$name] processing task...")

        // Combine system instruction with task for simple routeRequest
        val fullPrompt = "$roleInstruction\n\nTask: $task"

        val response = gateway.routeRequest(fullPrompt).reduce { acc: String, value: String -> acc + value }
        return "Report from $name:\n$response"
    }
}

/**
 * Example Sub-Agent: Specialized in critical medical queries.
 */
class MedicalSubAgent(gateway: NeuralGateway) : BaseAgent(
    name = "Medical Expert",
    roleInstruction = "You are a highly trained medical AI. If the user's prompt involves health, biology, or medicine, answer it precisely. If it does not, reply strictly with 'OUT_OF_SCOPE'.",
    gateway = gateway
)

/**
 * Example Sub-Agent: Specialized in security and scam detection.
 */
class SecuritySubAgent(gateway: NeuralGateway) : BaseAgent(
    name = "Security Expert",
    roleInstruction = "You are a strict cybersecurity AI. If the user's prompt involves scams, tech support, passwords, or phishing, analyze the risk. If it does not, reply strictly with 'OUT_OF_SCOPE'.",
    gateway = gateway
)
