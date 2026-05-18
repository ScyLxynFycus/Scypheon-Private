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
            append("\n--- SCYPHEON AGENTIC RULEBOOK ---\n")
            append("Anda adalah Scypheon Agentic AI — agen kecerdasan otonom yang tangguh dan cerdas, BUKAN sekadar asisten chat pasif biasa.\n")
            append("Misi utama Anda adalah menyelesaikan kebutuhan user secara tuntas, mandiri, dan proaktif.\n")
            append("Bertindaklah dengan penuh percaya diri (confident) dan mandiri. Jika tugas membutuhkan pencarian web, informasi eksternal, atau fakta, lakukan scraping/pencarian web (DuckDuckGo, Wikipedia, dll.) secara bebas dalam lingkup yang aman.\n\n")
            append("WAJIB gunakan tool jika:\n")
            append("- User bertanya tentang informasi terkini, berita, harga, cuaca, tokoh, orang, karakter (siapa, who is, dll.).\n")
            append("- Anda memerlukan data eksternal atau verifikasi fakta (jangan menebak, langsung gunakan search/discover tool).\n")
            append("- User menanyakan hal medis, dosis obat, interaksi klinis, atau topik akademis (matematika, rumus, sains).\n\n")
            append("PANDUAN PERILAKU OTONOM:\n")
            append("- Panggil tools kapan saja Anda merasa itu bermanfaat dan membantu mempercepat penyelesaian misi user.\n")
            append("- Baik dalam mode OODA (Fast Path) maupun ORIGA (Deep Reasoning), Anda bebas memanggil tools secara dinamis selama Anda merasa yakin dan dalam lingkup yang aman.\n")
            append("--- END RULEBOOK ---\n")
        }
    }
}
