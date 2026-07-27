package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.engine.YoloMicroEngine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillIntentRouter @Inject constructor(
    private val skillRegistry: AgentSkillRegistry,
    private val gateway: NeuralGateway,
    private val globalToolsProvider: GlobalToolsProvider,
    private val yoloEngine: YoloMicroEngine
) {

    enum class RoutingPath { OODA_FAST, ORIGA_REASONING }

    companion object {
        // Optimized for SmolLM2 (Micro LLM) - default to B for safety/agentic capabilities
        private const val CLASSIFICATION_PROMPT = """
            |You are a router. Analyze the user message: "%s"
            |If it is ONLY a simple greeting, chit-chat, or basic fact (e.g. "hello", "what is 2+2"), reply ONLY with "A".
            |If it requires searching, executing tools, complex logic, coding, or multi-step tasks, reply ONLY with "B".
            |If unsure, reply "B".
            |Classification (A or B):"""

        private const val SKILL_CLASSIFICATION_PROMPT = """
            |Identify the skills needed for this user message. Reply with a comma-separated list of EXACTLY these keywords: MEDICAL, STEM, EDUCATION, ACCESSIBILITY, EXPLAINABILITY, RESEARCH, RESILIENCE, GENERAL.
            |
            |User message: "%s"
            |
            |Skills:"""

        private val SAFETY_CRITICAL_REGEX = Regex(
            "(suicide|bunuh diri|overdose|overdosis|self-harm|gantung diri|racun|poisoning|darurat medis kritis|emergency vital)",
            RegexOption.IGNORE_CASE
        )

        private const val LLM_TIMEOUT_MS = 1500L
    }

    data class FastRoutingVerdict(
        val skillType: AgentSkillRegistry.SkillType,
        val confidence: Float
    )

    data class RoutingDecision(
        val path: RoutingPath,
        val skillScores: Map<AgentSkillRegistry.SkillType, Float>,
        val skillType: AgentSkillRegistry.SkillType,
        val availableTools: List<String>,
        val requiresWebAccess: Boolean,
        val estimatedTokenCost: Int
    )

    fun routeQuick(query: String, maxLatencyMs: Long = 80L): FastRoutingVerdict {
        // [SBI] Solaris Hardening: Primary Skill Mapping via High-Speed Heuristics
        // To maintain sub-100ms UI responsiveness, we start with regex,
        // but mission-level routing will later refine this with LLM classification.
        val start = System.currentTimeMillis()
        
        if (SAFETY_CRITICAL_REGEX.containsMatchIn(query)) {
            Timber.w("🛡️ [ROUTER] Safety-critical intent detected in routeQuick.")
            return FastRoutingVerdict(AgentSkillRegistry.SkillType.MEDICAL, 1.0f)
        }

        // Check for specific tool intent which usually implies OODA Fast
        val isToolIntent = detectWebIntent(query) || detectFileIntent(query) || detectShellIntent(query)
        
        val scores = classifySkillScores(query)
        val maxSkill = scores.maxByOrNull { it.value }
        
        val result = if (maxSkill != null) {
            val confidence = if (isToolIntent) (maxSkill.value + 0.2f).coerceAtMost(1.0f) else maxSkill.value
            FastRoutingVerdict(maxSkill.key, confidence)
        } else {
            FastRoutingVerdict(AgentSkillRegistry.SkillType.GENERAL, 0.5f)
        }
        
        val latency = System.currentTimeMillis() - start
        Timber.d("[ROUTER] routeQuick resolved in ${latency}ms to ${result.skillType} (Conf: ${result.confidence})")
        return result
    }

    class DomainRejectedException(message: String) : Exception(message)

    suspend fun routeMission(query: String, context: com.scypheon.sdk.core.agent.ooda.SessionContext): RoutingDecision {
        if (SAFETY_CRITICAL_REGEX.containsMatchIn(query)) {
            val skills = classifySkillScoresLlm(query) ?: classifySkillScores(query)
            Timber.i("🛡️ [ROUTER] Safety-critical query detected. Forcing ORIGA reasoning path.")
            return buildRoutingDecision(RoutingPath.ORIGA_REASONING, skills, query, context)
        }

        // 🛡️ [HELIOS L2] Domain Guard
        val allowedClinicalDomains = context.allowedDomains ?: AgentSkillRegistry.SkillType.values().toList()
        
        val scores = classifySkillScoresLlm(query) ?: classifySkillScores(query)
        val topSkill = scores.maxByOrNull { it.value }?.key ?: AgentSkillRegistry.SkillType.GENERAL
        
        if (topSkill !in allowedClinicalDomains) {
            Timber.w("🛡️ [HELIOS L2] Domain Guard blocked query. Top skill: $topSkill not in $allowedClinicalDomains")
            throw DomainRejectedException("Prompt ditolak oleh Domain Guard: Tidak sesuai dengan domain yang diizinkan.")
        }

        // [v1.6.0-SAR] AI-DRIVEN PATH SELECTION (Anti-Tolol Protocol)
        // We attempt YOLO Micro Engine classification to decide between OODA Fast and ORIGA Reasoning.
        val llmDecision = try {
            if (yoloEngine.isReady()) {
                val classification = yoloEngine.classify(String.format(CLASSIFICATION_PROMPT.trimMargin(), query.take(150)))
                findClassificationInText(classification ?: "")
            } else {
                Timber.w("⚠️ [ROUTER] YOLO Engine not ready. Using heuristic fallback.")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "🚨 [ROUTER] YOLO LLM classification failed.")
            null
        }

        val (path, skills) = when (llmDecision) {
            "A" -> {
                Timber.i("🛰️ [ROUTER] YOLO Verdict: OODA_FAST (Direct/Conversational)")
                RoutingPath.OODA_FAST to mapOf(AgentSkillRegistry.SkillType.GENERAL to 1.0f)
            }
            "B" -> {
                Timber.i("🧠 [ROUTER] YOLO Verdict: ORIGA_REASONING (Deep Planning/Thinking)")
                val s = classifySkillScoresLlm(query) ?: classifySkillScores(query)
                RoutingPath.ORIGA_REASONING to s
            }
            else -> {
                val heuristicResult = heuristicRoute(query)
                Timber.i("🛰️ [ROUTER] Fallback to Heuristic Routing Path: ${heuristicResult.first}")
                heuristicResult
            }
        }
        
        return buildRoutingDecision(path, skills, query, context)
    }

    private fun estimateSchemaTokens(toolName: String): Int {
        return when (toolName) {
            "web_search" -> 180
            "web_fetch" -> 150
            "clinical_dosage" -> 320
            "file_read_internal" -> 220
            "glob_internal" -> 190
            "execute_safe_command" -> 250
            else -> 200
        }
    }

    private fun buildRoutingDecision(
        path: RoutingPath,
        skillScores: Map<AgentSkillRegistry.SkillType, Float>,
        query: String,
        context: com.scypheon.sdk.core.agent.ooda.SessionContext
    ): RoutingDecision {
        val skillType = skillScores.maxByOrNull { it.value }?.key ?: AgentSkillRegistry.SkillType.GENERAL
        val requiresWeb = detectWebIntent(query)
        val requiresFile = detectFileIntent(query)
        val tools = mutableListOf<String>()
        
        // 1. Domain specific tools ONLY
        tools.addAll(skillRegistry.getSkill(skillType)?.recommendedToolNames ?: emptyList())
        
        // 2. Conditional global tools (JIT Injection)
        val isOnline = true 
        if (requiresWeb && isOnline) {
            tools.add("web_search")
            tools.add("web_fetch")
        }
        if (requiresFile) {
            tools.add("file_read_internal")
            tools.add("glob_internal")
        }
        if (detectShellIntent(query)) {
            tools.add("execute_safe_command")
        }
        
        val finalTools = tools.distinct()
        val tokenCost = finalTools.sumOf { estimateSchemaTokens(it) }
        
        return RoutingDecision(path, skillScores, skillType, finalTools, requiresWeb, tokenCost)
    }

    private fun detectWebIntent(query: String): Boolean {
        val webKeywords = listOf(
            "latest", "current", "recent", "news", "update",
            "fda recall", "search the web", "look up", "find online", "online"
        )
        return webKeywords.any { query.contains(it, ignoreCase = true) }
    }
    
    private fun detectFileIntent(query: String): Boolean {
        val fileKeywords = listOf("file", "read", "log", "document", "folder", "directory")
        return fileKeywords.any { query.contains(it, ignoreCase = true) }
    }
    
    private fun detectShellIntent(query: String): Boolean {
        val shellKeywords = listOf("shell", "command", "terminal", "execute script")
        return shellKeywords.any { query.contains(it, ignoreCase = true) }
    }

    fun routeMissionSync(query: String): Pair<RoutingPath, Map<AgentSkillRegistry.SkillType, Float>> {
        if (SAFETY_CRITICAL_REGEX.containsMatchIn(query)) {
            return RoutingPath.ORIGA_REASONING to classifySkillScores(query)
        }
        return heuristicRoute(query)
    }

    private class EarlyTerminationException : Exception()

    private fun findClassificationInText(text: String): String? {
        val clean = text.trim().uppercase()
        if (clean.isEmpty()) return null

        if (clean.startsWith("A")) return "A"
        if (clean.startsWith("B")) return "B"

        if (clean.contains("ANSWER: A") || clean.contains("CLASSIFICATION: A")) return "A"
        if (clean.contains("ANSWER: B") || clean.contains("CLASSIFICATION: B")) return "B"

        val words = clean.split(Regex("\\s+"))
        for (word in words) {
            val stripped = word.replace(Regex("[^A-Z]"), "")
            if (stripped == "A") return "A"
            if (stripped == "B") return "B"
        }
        return null
    }

    private suspend fun classifySkillScoresLlm(query: String): Map<AgentSkillRegistry.SkillType, Float>? {
        if (!gateway.isReady()) return null

        return withTimeoutOrNull(LLM_TIMEOUT_MS) {
            val prompt = String.format(SKILL_CLASSIFICATION_PROMPT.trimMargin(), query.take(200))
            val history = listOf(NeuralGateway.NeuralTurn(NeuralGateway.NeuralTurn.Role.USER, prompt))
            var resultText = ""
            try {
                gateway.generateResponse(history, topK = 1, topP = 0.1f, temp = 0.0f, maxTokens = 64, enableThinking = false).collect { token ->
                    resultText += token
                }
            } catch (e: Exception) {
                return@withTimeoutOrNull null
            }

            val cleaned = resultText.uppercase()
            val scores = mutableMapOf<AgentSkillRegistry.SkillType, Float>()
            if (cleaned.contains("MEDICAL")) scores[AgentSkillRegistry.SkillType.MEDICAL] = 1.0f
            if (cleaned.contains("STEM")) scores[AgentSkillRegistry.SkillType.STEM] = 1.0f
            if (cleaned.contains("EDUCATION")) scores[AgentSkillRegistry.SkillType.EDUCATION] = 1.0f
            if (cleaned.contains("ACCESSIBILITY")) scores[AgentSkillRegistry.SkillType.ACCESSIBILITY] = 1.0f
            if (cleaned.contains("EXPLAINABILITY")) scores[AgentSkillRegistry.SkillType.EXPLAINABILITY] = 1.0f
            if (cleaned.contains("RESEARCH")) scores[AgentSkillRegistry.SkillType.RESEARCH] = 1.0f
            if (cleaned.contains("RESILIENCE")) scores[AgentSkillRegistry.SkillType.RESILIENCE] = 1.0f
            if (cleaned.contains("GENERAL")) scores[AgentSkillRegistry.SkillType.GENERAL] = 1.0f

            if (scores.isEmpty()) null else scores
        }
    }

    private fun heuristicRoute(query: String): Pair<RoutingPath, Map<AgentSkillRegistry.SkillType, Float>> {
        val lower = query.lowercase().trim()
        val skills = classifySkillScores(query)

        // 1. Explicitly trivial Chit-Chat
        val isChitChat = lower.matches(Regex("^(halo|hai|selamat( pagi| siang| sore| malam)|apa kabar|tes|test|ok|oke|makasih|terima kasih|siap)[.!?]*$"))
        
        // 2. Action Verbs mapping to tools/logic
        val hasActionVerbs = Regex("\\b(buat|bikin|cari|analisis|tulis|run|eksekusi|jalankan|baca|periksa|cek|bandingkan|hitung|solve|fix)\\b").containsMatchIn(lower)
        
        if (isChitChat && !hasActionVerbs) {
            Timber.i("[ROUTER] Heuristic: Explicit Chit-chat. Routing to OODA Fast.")
            return RoutingPath.OODA_FAST to skills
        }

        if (hasActionVerbs || lower.length > 50) {
            Timber.i("[ROUTER] Heuristic: Action verbs or long query. Routing to ORIGA_REASONING.")
            return RoutingPath.ORIGA_REASONING to skills
        }

        // DEFAULT TO ORIGA (Fail-safe to ensure Agentic behavior is never bypassed for real tasks)
        Timber.i("[ROUTER] Heuristic: Unsure. Defaulting to ORIGA_REASONING to prevent dumb fallbacks.")
        return RoutingPath.ORIGA_REASONING to skills
    }

    private fun classifySkillScores(query: String): Map<AgentSkillRegistry.SkillType, Float> {
        val lower = query.lowercase()
        val scores = mutableMapOf<AgentSkillRegistry.SkillType, Float>()

        if (lower.contains(Regex("(drug|medicine|meds|medication|pill|dosage|side effect|prescription|resep|obat|dosis|pil|efek samping|paracetamol|amoxicillin|migrain|headache|pain|nyeri|sakit|fever|demam|dysentery|disentri|darah|hemorrhage)"))) {
            scores[AgentSkillRegistry.SkillType.MEDICAL] = 0.8f
        }
        if (lower.contains(Regex("(calculate|math|formula|equation|hitung|rumus|persamaan|kalkul)"))) {
            scores[AgentSkillRegistry.SkillType.STEM] = 0.7f
        }
        if (lower.contains(Regex("(teach|learn|lesson|explain topic|education|school|study|sekolah|siswa|guru|ajar|belajar|pelajaran|jelaskan topik)"))) {
            scores[AgentSkillRegistry.SkillType.EDUCATION] = 0.7f
        }
        if (lower.contains(Regex("(difficult to read|dyslexia|reformat|sulit baca|disleksia|sederhanakan|simplification|readability)"))) {
            scores[AgentSkillRegistry.SkillType.ACCESSIBILITY] = 0.9f
        }
        if (lower.contains(Regex("(why|explain reasoning|how did you|kenapa|mengapa|jelaskan alasan)"))) {
            scores[AgentSkillRegistry.SkillType.EXPLAINABILITY] = 0.6f
        }
        if (lower.contains(Regex("(wikipedia|wiki|search|lookup|who is|what is|find info|fandom|cari|siapa|apa itu)"))) {
            scores[AgentSkillRegistry.SkillType.RESEARCH] = 0.6f
        }
        if (lower.contains(Regex("(flood|banjir|gempa|disaster|bencana|darurat|emergency|evakuasi|tsunami|purification|purifikasi|air bersih)"))) {      
            scores[AgentSkillRegistry.SkillType.RESILIENCE] = 0.8f
        }

        if (scores.isEmpty()) {
            scores[AgentSkillRegistry.SkillType.GENERAL] = 1.0f
        }
        return scores
    }
}
