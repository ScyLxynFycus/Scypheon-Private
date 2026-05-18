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
            append("Anda memiliki akses ke tools berikut. Gunakan format XML <tool_call>{ \"toolName\": \"...\", \"arguments\": { ... } }</tool_call> untuk memanggilnya:\n\n")
            tools.values.forEach { tool ->
                append("- ${tool.name}: ${tool.description}\n")
                append("  Schema: ${tool.inputSchema}\n\n")
            }
            append("\n--- PANDUAN PENGGUNAAN TOOLS ---\n")
            append("WAJIB gunakan tool jika:\n")
            append("- User bertanya tentang orang, tokoh, karakter (siapa, who is)\n")
            append("- User bertanya fakta spesifik yang kamu tidak 100% yakin\n")
            append("- User meminta data terkini, berita, harga, cuaca\n")
            append("- User bertanya tentang obat, dosis, interaksi obat\n")
            append("- User meminta perhitungan atau rumus\n")
            append("- User bertanya 'apa itu', 'what is', 'how does X work'\n")
            append("\nJANGAN gunakan tool jika:\n")
            append("- User hanya ngobrol biasa, salam, curhat\n")
            append("- User minta pendapat atau opini\n")
            append("- User minta creative writing atau roleplay\n")
            append("--- END PANDUAN ---\n")
        }
    }
}
