package com.scypheon.sdk.core.safety

import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level security router that determines the initial path for a user prompt.
 * Acts as the first line of defense before full orchestration.
 */
@Singleton
class SafetyRouter @Inject constructor() {

    fun route(prompt: String): RoutingDecision {
        // Early exit: Block empty or malicious-looking prompts early
        if (prompt.isBlank()) {
            return RoutingDecision(RoutingPath.BLOCKED, "Prompt cannot be empty.")
        }

        // Basic structural integrity check
        if (prompt.length > 8000) {
            return RoutingDecision(RoutingPath.BLOCKED, "Prompt exceeds maximum allowed length.")
        }

        // Default to pass-through; the orchestrator will handle deep safety/jailbreak detection
        return RoutingDecision(RoutingPath.PASS)
    }
}

enum class RoutingPath {
    PASS,
    BLOCKED,
    REDIRECT
}

data class RoutingDecision(
    val path: RoutingPath,
    val blockedReason: String? = null
)
