package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
import com.scypheon.sdk.core.gateway.NeuralGateway
import kotlinx.coroutines.flow.toList
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SkillIntentRouter v2.0 — LLM-Powered Adaptive Mission Routing
 * 
 * [v1.5.0-SAR] Replaced fragile regex keyword matching with an LLM-based
 * pre-flight classifier, inspired by Claude Code's QueryEngine pattern.
 * 
 * The LLM itself determines whether a query needs tool-augmented reasoning
 * (ORIGA) or can be answered directly (OODA_FAST). This works because:
 * 
 * 1. The classification prompt generates only 1 token — near-zero latency
 * 2. The LLM knows its own knowledge boundaries better than regex patterns
 * 3. Works across ALL languages without manual keyword lists
 * 
 * Fallback: If LLM classification fails (not ready, timeout), falls back
 * to a conservative heuristic that routes ambiguous queries to ORIGA.
 */
@Singleton
class SkillIntentRouter @Inject constructor(
    private val skillRegistry: AgentSkillRegistry,
    private val gateway: NeuralGateway
) {

    enum class RoutingPath { OODA_FAST, ORIGA_REASONING }

    companion object {
        /**
         * Ultra-compact classification prompt. Optimized for small models:
         * - Clear binary choice (A or B)
         * - Concrete examples for each path
         * - Single token response expected
         */
        private val CLASSIFICATION_PROMPT = """
            |Classify this user message. Reply with ONLY the letter A or B:
            |A = You can answer directly from your knowledge (casual chat, opinions, greetings, creative writing)
            |B = Needs external information, fact-checking, search, calculations, or tools (who is X, what happened, current events, specific data, medical info)
            |
            |User message: "%s"
            |
            |Classification:""".trimMargin()

        // Precompiled safety-critical patterns that ALWAYS route to ORIGA
        // regardless of LLM classification (defense in depth)
        private val SAFETY_CRITICAL_REGEX = Regex(
            "(medical|medicine|meds|medication|dosage|drug interaction|side effect|prescription|resep|obat|dosis|overdose|suicide|bunuh diri|darurat|emergency|migrain|headache|sakit|disease|fever|demam|pain|nyeri|doctor|dokter|clinical|education|teach|learn|lesson|explain topic|school|study|sekolah|siswa|guru|ajar|belajar|pelajaran|jelaskan topik|paracetamol|panadol|ibuprofen|aspirin|acetaminophen|amoxicillin|antibiotic|antibiotik|insulin|vaccine|vaksin|health|kesehatan|hospital|rumah sakit|clinic|klinik|nurse|perawat|pharmacy|apotek|allergy|alergi|cough|batuk|flu|pilek|wound|luka|blood|darah|depress|depresi|anxiety|panic attack|self-harm|matematika|fisika|kimia|biologi)",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * LLM-powered mission routing with safety fallbacks.
     * 
     * Priority order:
     * 1. Safety-critical keywords → ORIGA (always, no LLM needed)
     * 2. LLM classification → A=OODA, B=ORIGA
     * 3. Fallback (if LLM unavailable) → conservative heuristic
     */
    suspend fun routeMission(query: String): Pair<RoutingPath, AgentSkillRegistry.SkillType> {
        // Layer 1: Safety-critical hard override (no LLM needed)
        if (SAFETY_CRITICAL_REGEX.containsMatchIn(query)) {
            val skill = classifySkillType(query)
            Timber.i("🛡️ [ROUTER] Safety-critical query detected. Forcing ORIGA via $skill")
            return RoutingPath.ORIGA_REASONING to skill
        }

        // Layer 2: LLM-based classification (single token, ~50-100ms)
        val llmDecision = try {
            if (gateway.isReady()) {
                classifyWithLlm(query)
            } else {
                Timber.w("🧠 [ROUTER] Gateway not ready. Using heuristic fallback.")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "🧠 [ROUTER] LLM classification failed. Using heuristic fallback.")
            null
        }

        return when (llmDecision) {
            "A" -> {
                Timber.i("🏎️ [ROUTER] LLM classified as DIRECT. Routing to OODA Fast.")
                RoutingPath.OODA_FAST to AgentSkillRegistry.SkillType.GENERAL
            }
            "B" -> {
                val skill = classifySkillType(query)
                Timber.i("🧠 [ROUTER] LLM classified as NEEDS_TOOLS. Routing to ORIGA via $skill")
                RoutingPath.ORIGA_REASONING to skill
            }
            else -> {
                // Fallback: heuristic classification (conservative — ambiguous → ORIGA)
                heuristicRoute(query)
            }
        }
    }

    /**
     * Synchronous version for telemetry/logging only.
     * Uses heuristic fallback since suspend isn't available.
     */
    fun routeMissionSync(query: String): Pair<RoutingPath, AgentSkillRegistry.SkillType> {
        if (SAFETY_CRITICAL_REGEX.containsMatchIn(query)) {
            return RoutingPath.ORIGA_REASONING to classifySkillType(query)
        }
        return heuristicRoute(query)
    }

    /**
     * Single-token LLM classification.
     * Uses a minimal history to keep KV cache usage near zero.
     */
    private suspend fun classifyWithLlm(query: String): String? {
        val classificationPrompt = String.format(CLASSIFICATION_PROMPT, query.take(200))
        
        val history = listOf(
            NeuralGateway.NeuralTurn(
                NeuralGateway.NeuralTurn.Role.USER,
                classificationPrompt
            )
        )

        val tokens = mutableListOf<String>()
        try {
            // Generate with very low temperature for deterministic classification
            // maxTokens=3 to capture just "A" or "B" plus any whitespace
            gateway.generateResponse(
                history,
                topK = 1,
                topP = 0.1f,
                temp = 0.0f,
                maxTokens = 3,
                enableThinking = false
            ).collect { token ->
                tokens.add(token.trim())
            }
        } catch (e: Exception) {
            Timber.e(e, "🧠 [ROUTER] LLM classification stream failed.")
            return null
        }

        val result = tokens.joinToString("").trim().uppercase()
        Timber.d("🧠 [ROUTER] LLM classification raw: '$result'")

        // Extract the classification letter
        return when {
            result.startsWith("A") -> "A"
            result.startsWith("B") -> "B"
            result.contains("A") && !result.contains("B") -> "A"
            result.contains("B") && !result.contains("A") -> "B"
            else -> {
                Timber.w("🧠 [ROUTER] LLM gave ambiguous classification: '$result'. Defaulting to ORIGA.")
                "B" // Conservative: ambiguous → use tools
            }
        }
    }

    /**
     * Conservative heuristic fallback — routes knowledge-seeking queries to ORIGA.
     * Used when LLM is unavailable or classification fails.
     */
    private fun heuristicRoute(query: String): Pair<RoutingPath, AgentSkillRegistry.SkillType> {
        val lower = query.lowercase()
        
        // Knowledge-seeking patterns → ORIGA
        val needsResearch = lower.contains(Regex(
            "(who is|what is|when did|where is|how to|how does|why does|" +
            "siapa|apa itu|kapan|dimana|bagaimana|mengapa|kenapa|" +
            "tell me about|explain|define|search|find|lookup|" +
            "jelaskan|cari|carikan|tolong cari|tau ga|tau tidak|kau tau)"
        ))
        
        return if (needsResearch) {
            val skill = classifySkillType(query)
            Timber.i("🧠 [ROUTER] Heuristic: Knowledge-seeking query. Routing to ORIGA via $skill")
            RoutingPath.ORIGA_REASONING to skill
        } else {
            Timber.i("🏎️ [ROUTER] Heuristic: Casual/creative query. Routing to OODA Fast.")
            RoutingPath.OODA_FAST to AgentSkillRegistry.SkillType.GENERAL
        }
    }

    /**
     * Skill type classification for ORIGA routing.
     */
    private fun classifySkillType(query: String): AgentSkillRegistry.SkillType {
        val lower = query.lowercase()
        return when {
            lower.contains(Regex("(drug|medicine|meds|medication|pill|dosage|side effect|prescription|resep|obat|dosis|pil|efek samping|paracetamol|amoxicillin|migrain|headache|pain|nyeri|sakit|fever|demam)")) -> AgentSkillRegistry.SkillType.MEDICAL
            lower.contains(Regex("(calculate|math|formula|equation|hitung|rumus|persamaan|kalkul)")) -> AgentSkillRegistry.SkillType.STEM
            lower.contains(Regex("(teach|learn|lesson|explain topic|education|school|study|sekolah|siswa|guru|ajar|belajar|pelajaran|jelaskan topik)")) -> AgentSkillRegistry.SkillType.EDUCATION
            lower.contains(Regex("(difficult to read|dyslexia|reformat|sulit baca|disleksia)")) -> AgentSkillRegistry.SkillType.ACCESSIBILITY
            lower.contains(Regex("(why|explain reasoning|how did you|kenapa|mengapa|jelaskan alasan)")) -> AgentSkillRegistry.SkillType.EXPLAINABILITY
            lower.contains(Regex("(wikipedia|wiki|search|lookup|who is|what is|find info|fandom|cari|siapa|apa itu)")) -> AgentSkillRegistry.SkillType.RESEARCH
            else -> AgentSkillRegistry.SkillType.GENERAL
        }
    }
}
