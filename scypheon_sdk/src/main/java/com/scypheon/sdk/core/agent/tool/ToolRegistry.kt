package com.scypheon.sdk.core.agent.tool

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ToolRegistry: Dynamic tool discovery and resolution system.
 * Implements the "Claude Code" pattern for pluggable agentic capabilities.
 *
 * [v1.6.0-PROGRESSIVE] Two-Stage Progressive Disclosure.
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
     * Generates a text representation of tools with progressive disclosure.
     * @param fullSchemaToolNames Set of tools to inject with full JSON schemas.
     */
    fun generateToolDefinitionsPrompt(fullSchemaToolNames: Set<String> = emptySet()): String {
        return buildString {
            append("You have access to the following tools. Use the XML format <tool_call>{ \"name\": \"...\", \"arguments\": { ... } }</tool_call> to invoke them.\n")
            append("Note: For efficiency, some tools show only a brief summary. You can invoke any tool listed below; if you call a tool with only a summary, the system will provide the full schema in the next turn.\n\n")

            // Group tools for better LLM context organization
            val groups = tools.values.groupBy { tool ->
                when {
                    tool.isMedical -> "MEDICAL SAFETY"
                    tool.name.contains("calculate") || tool.name.contains("math") -> "STEM & MATH"
                    tool.name.contains("web") || tool.name.contains("wiki") || tool.name.contains("discover") -> "RESEARCH"
                    else -> "UTILITIES"
                }
            }

            groups.forEach { (groupName, toolList) ->
                append("### $groupName TOOLS ###\n")
                toolList.forEach { tool ->
                    val isFull = fullSchemaToolNames.contains(tool.name)
                    if (isFull) {
                        append("- ${tool.name}: ${tool.description}\n")
                        append("  Schema: ${tool.inputSchema}\n\n")
                    } else {
                        append("- ${tool.name}: ${tool.triggerDescription}\n")
                        append("  [Full Schema Hidden for Context Efficiency]\n\n")
                    }
                }
            }

            append("\n--- SCYPHEON AGENTIC RULEBOOK ---\n")
            append("You are Scypheon Agentic AI — an autonomous, highly intelligent agent.\n")
            append("Your primary mission is to resolve the user's needs proactively using the tools provided.\n")
            append("NEVER guess objective facts. ALWAYS use search tools for real-time or verified data.\n")
            append("--- END RULEBOOK ---\n")
        }
    }
}
