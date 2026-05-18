package com.scypheon.sdk.core.intelligence.graph.steps

import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.Observation
import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Configuration for the final inference stream.
 */
data class AnswerStreamConfig(
    val traceId: String,
    val timeoutMs: Long = 12000L,
    val fallbackMessage: String = "[SYSTEM] Inference timed out or failed. Safe fallback activated."
)

@Singleton
class AnswerStep @Inject constructor(
    private val neuralGateway: NeuralGateway
) {
    companion object {
        private const val DELIM_SYSTEM = "=== SYSTEM INSTRUCTIONS ==="
        private const val DELIM_CONTEXT = "=== VERIFIED CONTEXT ==="
        private const val DELIM_QUERY = "=== USER QUERY ==="
        private const val DELIM_END = "=== END ==="
        private val INJECTION_REGEX = Regex("===.*===")
    }

    /**
     * Synthesizes the final grounded response using a secure, timeout-enforced stream.
     */
    fun process(
        query: String,
        verifiedFacts: List<String>,
        observation: Observation,
        environment: DeviceEnvironment,
        config: AnswerStreamConfig
    ): Flow<String> = flow {
        val prompt = buildSecurePrompt(query, verifiedFacts, observation, environment)
        Timber.i("[ORRIGA_ANSWER] Initiating inference synthesis | Trace: ${config.traceId}")

        neuralGateway.routeRequest(prompt)
            .timeout(config.timeoutMs.milliseconds)
            .catch { throwable ->
                Timber.w("[ORRIGA_ANSWER] Stream failure | Trace: ${config.traceId} | Reason: ${throwable.message}")
                emit(config.fallbackMessage)
            }
            .collect { chunk ->
                emit(chunk)
            }
    }.flowOn(Dispatchers.IO)

    private fun buildSecurePrompt(
        query: String,
        facts: List<String>,
        observation: Observation,
        environment: DeviceEnvironment
    ): String {
        val safeQuery = query.replace(INJECTION_REGEX, "[REDACTED]")
        val safeFacts = facts.map { it.replace(INJECTION_REGEX, "[REDACTED]") }
        val constraintLine = buildConstraintLine(environment, observation)

        return buildString {
            appendLine(DELIM_SYSTEM)
            appendLine("ROLE: ORRIGA grounded reasoning engine.")
            appendLine("RULE 1: Respond STRICTLY using verified context. Do not hallucinate.")
            appendLine("RULE 2: If context is insufficient, state limitations explicitly.")
            appendLine("RULE 3: Prioritize accuracy over verbosity.")
            appendLine("CONSTRAINTS: $constraintLine")
            appendLine(DELIM_SYSTEM)
            appendLine()
            appendLine(DELIM_CONTEXT)
            appendLine(safeFacts.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" } ?: "No verified context available. Use general knowledge cautiously.")
            appendLine(DELIM_CONTEXT)
            appendLine()
            appendLine(DELIM_QUERY)
            appendLine(safeQuery)
            appendLine(DELIM_QUERY)
            appendLine()
            appendLine(DELIM_END)
        }
    }

    private fun buildConstraintLine(env: DeviceEnvironment, obs: Observation): String {
        return buildList {
            add("battery=${env.batteryPercent}%")
            add("network=${env.networkType}")
            add("thermal=${env.thermalStatus}")
            if (obs.isUrgent) add("priority=CRITICAL")
        }.joinToString(", ")
    }
}
