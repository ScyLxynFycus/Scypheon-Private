package com.scypheon.sdk.core.humanitarian.education

import android.content.Context
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.utils.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * TutorAgent: Transformative Educational Learning (TEL) Architecture
 *
 * ENTERPRISE: This is a comprehensive educational API with functions for:
 * - explain() - Feynman technique explanations
 * - solveHomework() - Step-by-step problem solving
 * - generateQuiz() - Quiz generation
 * - gradeQuiz() - Answer grading
 * - findResources() - Curated learning resources
 *
 * Wired to AgentCore via detectTutorIntent() routing.
 */
@Suppress("unused") // Enterprise: Educational API - all parts are callable
class TutorAgent(
    private val context: Context,
    private val gateway: NeuralGateway
) {
    companion object {
        private const val TAG = "TutorAgent"
    }

    private val studentProgress = ConcurrentHashMap<String, TopicProgress>()

    data class TopicProgress(
        val topic: String,
        var explanationsViewed: Int = 0,
        var quizzesTaken: Int = 0,
        var correctAnswers: Int = 0,
        var totalQuestions: Int = 0,
        var lastInteraction: Long = System.currentTimeMillis()
    ) {
        val masteryLevel: Float
            get() = if (totalQuestions > 0) correctAnswers.toFloat() / totalQuestions else 0f

        val masteryLabel: String
            get() = when {
                masteryLevel >= 0.9f -> "Master"
                masteryLevel >= 0.7f -> "Proficient"
                masteryLevel >= 0.5f -> "Learning"
                else -> "Beginner"
            }
    }

    data class LearningResponse(
        val mode: LearningMode,
        val content: String,
        val followUpSuggestions: List<String>,
        val resources: List<Resource>? = null
    )

    data class Resource(
        val title: String,
        val url: String,
        val type: ResourceType,
        val verified: Boolean
    )

    enum class ResourceType { VIDEO, ARTICLE, PRACTICE, BOOK }

    enum class LearningMode {
        EXPLAIN,
        HOMEWORK,
        QUIZ,
        GRADE,
        SUMMARIZE,
        RESOURCES
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENTERPRISE: Adaptive Pedagogical Framework
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Bloom's Taxonomy - Cognitive Learning Hierarchy
     * Progression: Remember → Understand → Apply → Analyze → Evaluate → Create
     */
    enum class BloomLevel(val depth: Int, val verb: String) {
        REMEMBER(1, "Recall"),      // Basic recall of facts
        UNDERSTAND(2, "Explain"),   // Explain ideas, concepts
        APPLY(3, "Use"),            // Use in new situations
        ANALYZE(4, "Distinguish"),  // Draw connections, organize
        EVALUATE(5, "Judge"),       // Justify, critique, defend
        CREATE(6, "Design")         // Produce new, original work
    }

    /**
     * Pedagogical Methods - Different teaching strategies
     */
    enum class PedagogicalMethod {
        FEYNMAN,         // Simple explanations, analogies
        SOCRATIC,        // Questioning to stimulate critical thinking
        ELABORATIVE,     // Deep "why" and "how" questioning
        SCAFFOLDED,      // Step-by-step with increasing complexity
        MULTIMODAL       // Visual + verbal + kinesthetic
    }

    /**
     * ENTERPRISE: Determine appropriate Bloom level based on mastery.
     * Zone of Proximal Development - slightly above current level.
     */
    private fun getZoneOfProximalDevelopment(mastery: Float): BloomLevel {
        return when {
            mastery >= 0.9f -> BloomLevel.CREATE      // Master → Challenge to create
            mastery >= 0.8f -> BloomLevel.EVALUATE    // Near-master → Evaluate/critique
            mastery >= 0.6f -> BloomLevel.ANALYZE     // Proficient → Analyze relationships
            mastery >= 0.4f -> BloomLevel.APPLY       // Learning → Apply knowledge
            mastery >= 0.2f -> BloomLevel.UNDERSTAND  // Beginner → Deep understanding
            else -> BloomLevel.REMEMBER               // Novice → Build foundation
        }
    }

    /**
     * ENTERPRISE: Select pedagogical method based on content type and mastery.
     */
    private fun selectPedagogicalMethod(topic: String, mastery: Float): PedagogicalMethod {
        val lowerTopic = topic.lowercase()

        return when {
            // High mastery → Socratic for deeper thinking
            mastery >= 0.7f -> PedagogicalMethod.SOCRATIC

            // STEM topics → Scaffolded approach
            lowerTopic.containsAny("math", "fisika", "kimia", "physics", "chemistry", "calculus") ->
                PedagogicalMethod.SCAFFOLDED

            // Abstract concepts → Elaborative interrogation
            lowerTopic.containsAny("philosophy", "theory", "abstract", "konsep") ->
                PedagogicalMethod.ELABORATIVE

            // Visual topics → Multimodal
            lowerTopic.containsAny("anatomy", "geography", "design", "art") ->
                PedagogicalMethod.MULTIMODAL

            // Default → Feynman for clarity
            else -> PedagogicalMethod.FEYNMAN
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    // ══════════════════════════════════════════════════════════════════════════
    // ENTERPRISE: Multi-Language Support (11+ Languages)
    // LLM responds in user's detected language via instruction injection
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Get language instruction header for prompts.
     * Supports: Indonesian, English, Malay, Japanese, Korean, Chinese,
     * Spanish, French, German, Portuguese, Arabic, Hindi, Vietnamese, Thai
     */
    private fun getLanguageInstruction(lang: String): String {
        val langName = getLanguageName(lang)
        return """
        ⚠️ IMPORTANT: You MUST respond entirely in $langName.
        Adapt your explanations to be culturally relevant to $langName speakers.
        Use local examples and analogies when possible.
        """.trimIndent()
    }

    /**
     * Map language code to display name.
     */
    private fun getLanguageName(lang: String): String {
        return when (lang.lowercase()) {
            "in", "id" -> "Indonesian (Bahasa Indonesia)"
            "en" -> "English"
            "ms" -> "Malay (Bahasa Melayu)"
            "ja" -> "Japanese (日本語)"
            "ko" -> "Korean (한국어)"
            "zh", "zh-cn", "zh-tw" -> "Chinese (中文)"
            "es" -> "Spanish (Español)"
            "fr" -> "French (Français)"
            "de" -> "German (Deutsch)"
            "pt" -> "Portuguese (Português)"
            "ar" -> "Arabic (العربية)"
            "hi" -> "Hindi (हिन्दी)"
            "vi" -> "Vietnamese (Tiếng Việt)"
            "th" -> "Thai (ภาษาไทย)"
            "ru" -> "Russian (Русский)"
            "nl" -> "Dutch (Nederlands)"
            "tr" -> "Turkish (Türkçe)"
            "pl" -> "Polish (Polski)"
            "it" -> "Italian (Italiano)"
            else -> "English" // Fallback
        }
    }

    suspend fun explain(topic: String): LearningResponse = withContext(Dispatchers.IO) {
        val lang = LocaleHelper.getCurrentLanguageCode(context)
        Timber.d("$TAG: Explaining '$topic' in $lang")

        val prompt = buildExplainPrompt(topic, lang)
        val content = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }

        trackProgress(topic) { it.explanationsViewed++ }

        LearningResponse(
            mode = LearningMode.EXPLAIN,
            content = content,
            followUpSuggestions = generateFollowUps(topic, lang, LearningMode.EXPLAIN),
            resources = null
        )
    }

    suspend fun solveHomework(problem: String): LearningResponse = withContext(Dispatchers.IO) {
        val lang = LocaleHelper.getCurrentLanguageCode(context)
        Timber.d("$TAG: Solving homework: ${problem.take(50)}...")

        val prompt = buildHomeworkPrompt(problem, lang)
        val content = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }

        LearningResponse(
            mode = LearningMode.HOMEWORK,
            content = content,
            followUpSuggestions = listOf(
                if (lang in listOf("in", "id")) "Jelaskan langkah 1 lebih detail" else "Explain step 1 in more detail",
                if (lang in listOf("in", "id")) "Berikan soal latihan serupa" else "Give me similar practice problems",
                if (lang in listOf("in", "id")) "Kenapa rumus ini dipakai?" else "Why is this formula used?"
            )
        )
    }

    suspend fun generateQuiz(topic: String, numQuestions: Int = 5): LearningResponse = withContext(Dispatchers.IO) {
        val lang = LocaleHelper.getCurrentLanguageCode(context)
        Timber.d("$TAG: Generating quiz for '$topic'")

        val prompt = buildQuizPrompt(topic, numQuestions, lang)
        val content = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }

        trackProgress(topic) {
            it.quizzesTaken++
            it.totalQuestions += numQuestions
        }

        LearningResponse(
            mode = LearningMode.QUIZ,
            content = content,
            followUpSuggestions = listOf(
                if (lang in listOf("in", "id")) "Cek jawaban saya" else "Check my answers",
                if (lang in listOf("in", "id")) "Jelaskan konsep di soal 1" else "Explain the concept in question 1"
            )
        )
    }

    suspend fun gradeQuiz(
        topic: String,
        questionsAndAnswers: Map<String, String>
    ): LearningResponse = withContext(Dispatchers.IO) {
        val lang = LocaleHelper.getCurrentLanguageCode(context)
        Timber.d("$TAG: Grading ${questionsAndAnswers.size} answers")

        val prompt = buildGradePrompt(topic, questionsAndAnswers, lang)
        val content = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }

        val correctCount = content.lowercase().count { it == '✓' }
        trackProgress(topic) { it.correctAnswers += correctCount }

        LearningResponse(
            mode = LearningMode.GRADE,
            content = content,
            followUpSuggestions = listOf(
                if (lang in listOf("in", "id")) "Jelaskan jawaban yang salah" else "Explain the wrong answers",
                if (lang in listOf("in", "id")) "Beri soal serupa untuk latihan" else "Give me similar practice questions"
            )
        )
    }

    suspend fun findResources(topic: String): LearningResponse = withContext(Dispatchers.IO) {
        val lang = LocaleHelper.getCurrentLanguageCode(context)
        Timber.d("$TAG: Finding resources for '$topic'")

        val verifiedResources = getCuratedResources(topic, lang)
        val prompt = buildResourcePrompt(topic, lang)
        val content = gateway.routeRequest(prompt).reduce { acc, value -> acc + value }

        LearningResponse(
            mode = LearningMode.RESOURCES,
            content = content,
            followUpSuggestions = listOf(
                if (lang in listOf("in", "id")) "Jelaskan topik ini dulu" else "Explain this topic first",
                if (lang in listOf("in", "id")) "Buat kuis untuk topik ini" else "Create a quiz for this topic"
            ),
            resources = verifiedResources
        )
    }

    fun explainStreaming(topic: String): Flow<String> = flow {
        val lang = LocaleHelper.getCurrentLanguageCode(context)
        val prompt = buildExplainPrompt(topic, lang)

        gateway.routeRequest(prompt).collect { segment ->
            emit(segment)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * ENTERPRISE: Adaptive Prompt Builder
     * Selects pedagogy based on topic complexity and student mastery.
     */
    private fun buildExplainPrompt(topic: String, lang: String): String {
        val normalizedTopic = topic.lowercase().trim()
        val progress = studentProgress[normalizedTopic]
        val mastery = progress?.masteryLevel ?: 0f
        val bloomLevel = getZoneOfProximalDevelopment(mastery)
        val method = selectPedagogicalMethod(topic, mastery)

        Timber.d("$TAG: Adaptive pedagogy - Bloom: ${bloomLevel.name}, Method: $method, Mastery: ${(mastery * 100).toInt()}%")

        return when (method) {
            PedagogicalMethod.SOCRATIC -> buildSocraticPrompt(topic, lang, bloomLevel)
            PedagogicalMethod.SCAFFOLDED -> buildScaffoldedPrompt(topic, lang, bloomLevel)
            PedagogicalMethod.ELABORATIVE -> buildElaborativePrompt(topic, lang, bloomLevel)
            PedagogicalMethod.MULTIMODAL -> buildMultimodalPrompt(topic, lang, bloomLevel)
            PedagogicalMethod.FEYNMAN -> buildFeynmanPrompt(topic, lang, bloomLevel)
        }
    }

    /**
     * Feynman Technique: Simple explanations with analogies
     * Best for: Beginners, building foundation
     *
     * ENTERPRISE: Unified English template with dynamic language response.
     */
    private fun buildFeynmanPrompt(topic: String, lang: String, bloom: BloomLevel): String {
        val langInstruction = getLanguageInstruction(lang)
        val bloomInstruction = getBloomInstruction(bloom, lang)

        return """
        You are a VERY patient and expert private tutor.

        $langInstruction

        TASK: Explain "$topic" using the FEYNMAN TECHNIQUE.

        COGNITIVE LEVEL: ${bloom.name} - $bloomInstruction

        STRUCTURE:
        1. 🎯 CORE: Explain in simple words (like explaining to a 12-year-old)
        2. 🧠 DEEP DIVE: Execute the "7 Whys" and "7 Hows" technique. Ask and answer WHY it works, and HOW it operates fundamentally.
        3. 🌍 ANALOGIES: Give 2-3 culturally relevant everyday analogies
        4. 📝 CONCRETE EXAMPLES: Provide practical, actionable examples
        5. 🔗 CONNECTIONS: Link to familiar concepts
        6. ⚠️ MISCONCEPTIONS: Mention 2-3 common mistakes and how to avoid them
        7. 💡 MEMORY HOOK: Provide a mnemonic or easy way to remember

        Use emojis for visual engagement. Format with markdown.
        """.trimIndent()
    }

    /**
     * Socratic Method: Teaching through strategic questioning
     * Best for: Proficient students, critical thinking development
     */
    private fun buildSocraticPrompt(topic: String, lang: String, bloom: BloomLevel): String {
        val langInstruction = getLanguageInstruction(lang)

        return """
        You are a Socratic teacher who teaches through QUESTIONS, not direct answers.

        $langInstruction

        TOPIC: "$topic"
        COGNITIVE LEVEL: ${bloom.name}

        SOCRATIC METHOD:
        1. 🤔 OPENING QUESTION: Start with a curiosity-sparking question
        2. 🔍 CLARIFYING QUESTIONS: "What do you mean by...?"
        3. 🎯 ASSUMPTION QUESTIONS: "Why do you think that?"
        4. 💭 EVIDENCE QUESTIONS: "How do you know this is true?"
        5. 🌐 PERSPECTIVE QUESTIONS: "What if we look at it differently?"
        6. 🎓 IMPLICATION QUESTIONS: "What are the consequences of this thinking?"

        IMPORTANT RULES:
        - Don't give direct answers
        - Guide the student to discover themselves
        - Each question should build on the previous
        - End with a deep reflective question
        """.trimIndent()
    }

    /**
     * Scaffolded Learning: Step-by-step with increasing complexity
     * Best for: STEM subjects, procedural knowledge
     */
    private fun buildScaffoldedPrompt(topic: String, lang: String, bloom: BloomLevel): String {
        val langInstruction = getLanguageInstruction(lang)

        return """
        You are a STEM instructor using SCAFFOLDING PEDAGOGY.

        $langInstruction

        TOPIC: "$topic"
        LEVEL: ${bloom.name}

        SCAFFOLDED STRUCTURE:

        📊 LEVEL 1 - FOUNDATION (Remember)
        - Precise, concise definition
        - Basic formulas if applicable
        - Main components/parts

        📈 LEVEL 2 - COMPREHENSION (Understand)
        - Why is this important?
        - How does it work?
        - When is this used?

        🔧 LEVEL 3 - APPLICATION (Apply)
        - Example problem with step-by-step solution
        - Guided practice with hints

        🔬 LEVEL 4 - ANALYSIS (Analyze)
        - Compare with related concepts
        - Identify patterns and relationships

        ⚡ LEVEL 5 - CHALLENGE (Evaluate/Create)
        - Challenge problems for extension
        - Mini-project that can be worked on

        Provide CHECKPOINT after each level.
        """.trimIndent()
    }

    /**
     * Elaborative Interrogation: Deep "why" questioning
     * Best for: Abstract concepts, theoretical understanding
     */
    private fun buildElaborativePrompt(topic: String, lang: String, bloom: BloomLevel): String {
        val langInstruction = getLanguageInstruction(lang)

        return """
        You are a professor using ELABORATIVE INTERROGATION.

        $langInstruction

        TOPIC: "$topic"
        COGNITIVE TARGET: ${bloom.name}

        METHOD:
        For EVERY fact or concept, answer:

        1. 🤷 WHY is this true?
        2. 🔬 HOW do we know this?
        3. 🌐 HOW does this connect to other things?
        4. ⚖️ WHAT would happen if this weren't true?
        5. 🎯 WHY is this important to understand?

        ANSWER STRUCTURE:
        - Start with CLAIM (statement)
        - Provide EVIDENCE (proof)
        - Explain REASONING (logic)
        - Draw CONCLUSION (summary)
        - Pose FURTHER QUESTIONS (continuing inquiry)
        """.trimIndent()
    }

    /**
     * Multimodal Learning: Visual + Verbal + Kinesthetic
     * Best for: Visual topics, complex systems
     */
    private fun buildMultimodalPrompt(topic: String, lang: String, bloom: BloomLevel): String {
        val langInstruction = getLanguageInstruction(lang)

        return """
        You are a teacher using MULTIMODAL LEARNING.

        $langInstruction

        TOPIC: "$topic"
        LEVEL: ${bloom.name}

        USE ALL MODALITIES:

        👁️ VISUAL:
        - Draw with ASCII diagrams if possible
        - Describe what it looks like
        - Use colors and emojis for visualization

        👂 AUDITORY:
        - Explain as if speaking aloud
        - Use rhythm and repetition
        - Create a "jingle" or mnemonic

        ✋ KINESTHETIC:
        - Give hands-on activities that can be done
        - "Imagine you are..."
        - Connect to movement or physical sensation

        📊 DUAL REPRESENTATION:
        - Comparison tables
        - Process flowcharts
        - Concept mind maps
        """.trimIndent()
    }

    /**
     * Get Bloom-level specific instruction for prompt.
     * ENTERPRISE: English-only, LLM will translate to target language.
     */
    private fun getBloomInstruction(bloom: BloomLevel, @Suppress("UNUSED_PARAMETER") lang: String): String {
        return when (bloom) {
            BloomLevel.REMEMBER -> "Focus on REMEMBERING basic facts and definitions"
            BloomLevel.UNDERSTAND -> "Focus on UNDERSTANDING meaning and concepts"
            BloomLevel.APPLY -> "Focus on APPLYING in new situations"
            BloomLevel.ANALYZE -> "Focus on ANALYZING relationships and patterns"
            BloomLevel.EVALUATE -> "Focus on EVALUATING and critiquing"
            BloomLevel.CREATE -> "Focus on CREATING something new"
        }
    }

    private fun buildHomeworkPrompt(problem: String, lang: String): String {
        val langInstruction = getLanguageInstruction(lang)

        return """
        You are a tutor helping students UNDERSTAND, not just answer.

        $langInstruction

        PROBLEM: "$problem"

        IMPORTANT RULES:
        1. DON'T give the final answer directly
        2. EXPLAIN STEP BY STEP with numbering
        3. Each step MUST explain WHY it's done
        4. If there's a formula, EXPLAIN it first before using
        5. At the end, give TIPS for similar problems
        """.trimIndent()
    }

    private fun buildQuizPrompt(topic: String, numQuestions: Int, lang: String): String {
        val langInstruction = getLanguageInstruction(lang)

        return """
        Create $numQuestions quiz questions about: "$topic"

        $langInstruction

        FORMAT:
        - 3 multiple choice (A, B, C, D) with varying difficulty
        - 2 short essay questions

        RULES:
        - DO NOT show answers
        - Questions should test UNDERSTANDING, not memorization

        At the end write: "📝 Send your answers to be graded!"
        """.trimIndent()
    }

    private fun buildGradePrompt(topic: String, qa: Map<String, String>, lang: String): String {
        val langInstruction = getLanguageInstruction(lang)
        val qaText = qa.entries.joinToString("\n") { (q, a) -> "Q: $q\nA: $a" }

        return """
        Grade the following student answers for topic "$topic":

        $langInstruction

        $qaText

        GRADING FORMAT:
        For each question:
        - ✓ CORRECT / ✗ WRONG
        - Explain why correct/wrong
        - If wrong, provide correct answer with explanation

        At the end:
        📊 **Score:** X/Y correct
        """.trimIndent()
    }

    private fun buildResourcePrompt(topic: String, lang: String): String {
        val langInstruction = getLanguageInstruction(lang)

        return """
        Recommend quality learning resources for topic: "$topic"

        $langInstruction

        FORMAT:
        📺 **Video Recommendations:**
        - [Channel/Video name] - Why it's good

        📚 **Articles/Books:**
        - [Title] - Suitable for which level

        🎮 **Interactive Practice:**
        - [Platform/website] - What can be learned
        """.trimIndent()
    }

    private fun getCuratedResources(topic: String, lang: String): List<Resource> {
        val lower = topic.lowercase()
        val resources = mutableListOf<Resource>()

        if (lower.contains("math") || lower.contains("matematika") ||
            lower.contains("aljabar") || lower.contains("kalkulus")) {
            resources.add(Resource(
                title = "3Blue1Brown",
                url = "https://youtube.com/@3blue1brown",
                type = ResourceType.VIDEO,
                verified = true
            ))
            resources.add(Resource(
                title = "Khan Academy - Math",
                url = "https://khanacademy.org/math",
                type = ResourceType.PRACTICE,
                verified = true
            ))
        }

        if (lower.contains("science") || lower.contains("sains") ||
            lower.contains("physics") || lower.contains("fisika") ||
            lower.contains("chemistry") || lower.contains("kimia")) {
            resources.add(Resource(
                title = "Kurzgesagt - In a Nutshell",
                url = "https://youtube.com/@kurzgesagt",
                type = ResourceType.VIDEO,
                verified = true
            ))
            resources.add(Resource(
                title = "Veritasium",
                url = "https://youtube.com/@veritasium",
                type = ResourceType.VIDEO,
                verified = true
            ))
        }

        if (lang in listOf("in", "id")) {
            resources.add(Resource(
                title = "Zenius",
                url = "https://zenius.net",
                type = ResourceType.VIDEO,
                verified = true
            ))
            resources.add(Resource(
                title = "Ruangguru",
                url = "https://ruangguru.com",
                type = ResourceType.PRACTICE,
                verified = true
            ))
        }

        return resources
    }

    private fun trackProgress(topic: String, update: (TopicProgress) -> Unit) {
        val normalizedTopic = topic.lowercase().trim()
        val progress = studentProgress.getOrPut(normalizedTopic) {
            TopicProgress(topic = topic)
        }
        update(progress)
        progress.lastInteraction = System.currentTimeMillis()
    }

    fun getProgress(topic: String): TopicProgress? {
        return studentProgress[topic.lowercase().trim()]
    }

    fun getAllProgress(): List<TopicProgress> {
        return studentProgress.values.sortedByDescending { it.lastInteraction }
    }

    private fun generateFollowUps(_topic: String, lang: String, mode: LearningMode): List<String> {
        return when (mode) {
            LearningMode.EXPLAIN -> if (lang in listOf("in", "id")) {
                listOf(
                    "Beri contoh lebih banyak",
                    "Buat kuis untuk cek pemahaman",
                    "Cari video pembelajaran"
                )
            } else {
                listOf(
                    "Give more examples",
                    "Create a quiz to check understanding",
                    "Find learning videos"
                )
            }
            else -> emptyList()
        }
    }
}
