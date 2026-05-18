package com.scypheon.sdk.core.agent.tool

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ToolRegistry: Dynamic tool discovery and resolution system.
 * Implements the "Claude Code" pattern for pluggable agentic capabilities.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Map<String, @JvmSuppressWildcards Tool>
) {
    /**
     * Resolves a tool by its primary name or one of its aliases.
     */
    fun resolve(name: String): Tool? {
        // 1. Try primary name match
        val primaryMatch = tools[name]
        if (primaryMatch != null) return primaryMatch

        // 2. Try alias match
        return tools.values.find { it.aliases.contains(name) }
    }

    /**
     * Returns all registered tools for capability discovery.
     */
    fun getAllTools(): List<Tool> = tools.values.toList()

    /**
     * Returns tools that are currently enabled in the environment.
     */
    fun getEnabledTools(): List<Tool> = tools.values.filter { it.isEnabled() }

    /**
     * Generates a text representation of all available tools for prompt injection.
     */
    fun generateToolDefinitionsPrompt(): String {
        return buildString {
            append("You have access to the following tools. Use the XML format <tool_call>{ \"toolName\": \"...\", \"arguments\": { ... } }</tool_call> to invoke them:\n\n")
            tools.values.forEach { tool ->
                append("- ${tool.name}: ${tool.description}\n")
                append("  Schema: ${tool.inputSchema}\n\n")
            }
            append("\n--- SCYPHEON AGENTIC RULEBOOK ---\n")
            append("You are Scypheon Agentic AI — a powerful, highly intelligent, and autonomous agent, NOT a passive chat assistant.\n")
            append("Your primary mission is to resolve the user's needs fully, independently, and proactively.\n")
            append("Act with complete confidence and autonomy. If the task requires web searching, external information, or fact-checking, feel free to run web searches/scraping (DuckDuckGo, Wikipedia, etc.) dynamically within a safe scope to discover the absolute ground truth.\n")
            append("Always respond in the same language as the user's query unless requested otherwise. If the query is in English, you MUST reply in highly professional English.\n\n")
            append("REQUIRED to use tools if:\n")
            append("- The user asks for real-time information, news, prices, weather, public figures, or objective facts.\n")
            append("- You need external data or fact verification (do NOT guess or assume, immediately use search/discover tools).\n")
            append("- The user asks about medical topics, drug dosages, clinical interactions, or academic subjects (mathematics, formulas, science).\n\n")
            append("AUTONOMOUS BEHAVIOR GUIDELINES:\n")
            append("- Invoke tools whenever you believe they are beneficial and speed up the resolution of the user's mission.\n")
            append("- Whether in OODA (Fast Path) or ORIGA (Deep Reasoning) mode, you are completely free to call tools dynamically as long as you are confident and within a safe scope.\n")
            append("--- END RULEBOOK ---\n")
        }
    }
}
