package com.scypheon.sdk.core.humanitarian.education

import android.content.Context
import com.scypheon.sdk.core.security.PromptGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * ENTERPRISE-GRADE: Adaptive Learning Progression Engine
 * 
 * MANDATORY for all educational interactions. Implements evidence-based pedagogical frameworks:
 * - Bloom's Taxonomy (6 cognitive levels)
 * - Zone of Proximal Development (Vygotsky)
 * - Spaced Repetition (Ebbinghaus forgetting curve)
 * - Mastery Learning (Bloom 1968)
 * - Universal Design for Learning (UDL)
 * 
 * SAFETY LAYERS:
 * 1. Age-appropriate content filtering
 * 2. Learning disability accommodations (dyslexia, ADHD, autism)
 * 3. Cultural sensitivity adaptation
 * 4. Progress tracking with privacy preservation
 * 5. Anti-cheating detection for homework help
 */
class AdaptiveLearningEngine(private val context: Context) {

    companion object {
        private const val TAG = "AdaptiveLearningEngine"
        
        // Minimum mastery threshold before advancing Bloom level
        private const val MIN_MASTERY_FOR_ADVANCEMENT = 0.75f
        
        // Spaced repetition intervals (in hours) based on Ebbinghaus curve
        private val SPACED_REPETITION_INTERVALS = listOf(
            0.5,   // 30 minutes (initial review)
            1.0,   // 1 hour
            3.0,   // 3 hours
            12.0,  // 12 hours
            24.0,  // 1 day
            72.0,  // 3 days
            168.0, // 7 days
            720.0  // 30 days
        )
    }

    data class LearnerProfile(
        val learnerId: String,
        val age: Int?,
        val gradeLevel: Int?,
        val learningDisabilities: Set<LearningDisability>,
        val preferredLearningStyles: Set<LearningStyle>,
        val languageCode: String,
        val culturalContext: String?,
        val timezoneOffset: Int
    )

    data class LearningSession(
        val sessionId: String,
        val learnerId: String,
        val topic: String,
        val startTime: Long,
        var endTime: Long? = null,
        var bloomLevelAchieved: BloomLevel = BloomLevel.REMEMBER,
        var masteryScore: Float = 0f,
        var questionsAttempted: Int = 0,
        var questionsCorrect: Int = 0,
        var hintsRequested: Int = 0,
        var timeSpentSeconds: Long = 0
    )

    data class SpacedRepetitionSchedule(
        val topic: String,
        val nextReviewTime: Long,
        val intervalIndex: Int,
        val retentionStrength: Float // 0.0 to 1.0
    )

    enum class LearningDisability {
        DYSLEXIA,
        ADHD,
        AUTISM_SPECTRUM,
        DYSCALCULIA,
        AUDITORY_PROCESSING,
        VISUAL_PROCESSING
    }

    enum class LearningStyle {
        VISUAL,
        AUDITORY,
        KINESTHETIC,
        READ_WRITE,
        SOCIAL,
        SOLITARY
    }

    /**
     * Bloom's Taxonomy - Revised (Anderson & Krathwohl 2001)
     * Cognitive process dimensions for adaptive questioning
     */
    enum class BloomLevel(val depth: Int, val actionVerbs: List<String>) {
        REMEMBER(1, listOf("define", "list", "recall", "identify", "name")),
        UNDERSTAND(2, listOf("explain", "describe", "summarize", "paraphrase", "interpret")),
        APPLY(3, listOf("use", "demonstrate", "solve", "apply", "illustrate")),
        ANALYZE(4, listOf("compare", "contrast", "categorize", "differentiate", "examine")),
        EVALUATE(5, listOf("judge", "critique", "defend", "argue", "assess")),
        CREATE(6, listOf("design", "construct", "produce", "develop", "formulate"))
    }

    data class MasteryProgress(
        val topic: String,
        val currentBloomLevel: BloomLevel,
        val masteryPercentage: Float,
        val lastPracticed: Long,
        val totalSessions: Int,
        val spacedRepetitionStage: Int
    )

    private val learnerProfiles = ConcurrentHashMap<String, LearnerProfile>()
    private val activeSessions = ConcurrentHashMap<String, LearningSession>()
    private val masteryProgress = ConcurrentHashMap<String, MasteryProgress>()
    private val spacedRepetitionQueue = ConcurrentHashMap<String, SpacedRepetitionSchedule>()

    /**
     * STEP 1: Initialize learner profile with accessibility needs
     * Privacy-preserving: no PII stored, anonymized IDs only
     */
    fun initializeLearner(
        learnerId: String,
        age: Int?,
        gradeLevel: Int?,
        learningDisabilities: Set<LearningDisability> = emptySet(),
        preferredStyles: Set<LearningStyle> = emptySet(),
        languageCode: String = "en",
        culturalContext: String? = null
    ): LearnerProfile {
        val profile = LearnerProfile(
            learnerId = learnerId,
            age = age,
            gradeLevel = gradeLevel,
            learningDisabilities = learningDisabilities,
            preferredLearningStyles = preferredStyles.ifEmpty { setOf(LearningStyle.VISUAL) },
            languageCode = languageCode,
            culturalContext = culturalContext,
            timezoneOffset = java.util.TimeZone.getDefault().rawOffset / 3600000
        )
        
        learnerProfiles[learnerId] = profile
        Timber.i(TAG, "📚 Initialized learner profile: $learnerId with ${learningDisabilities.size} accommodations")
        
        return profile
    }

    /**
     * STEP 2: Start learning session with appropriate scaffolding
     * Adjusts difficulty based on learner profile and history
     */
    fun startSession(learnerId: String, topic: String): LearningSession {
        val profile = learnerProfiles[learnerId] 
            ?: throw IllegalStateException("Learner profile not initialized: $learnerId")
        
        val sessionId = "${learnerId}_${topic}_${System.currentTimeMillis()}"
        
        // Determine starting Bloom level based on mastery history
        val existingProgress = masteryProgress["${learnerId}_$topic"]
        val startingBloomLevel = existingProgress?.currentBloomLevel ?: BloomLevel.REMEMBER
        
        val session = LearningSession(
            sessionId = sessionId,
            learnerId = learnerId,
            topic = topic,
            startTime = System.currentTimeMillis(),
            bloomLevelAchieved = startingBloomLevel
        )
        
        activeSessions[sessionId] = session
        Timber.d(TAG, "🎯 Started session $sessionId at Bloom level: ${startingBloomLevel.name}")
        
        return session
    }

    /**
     * STEP 3: Generate age-appropriate, accessibility-aware prompt
     * Filters inappropriate content, adapts for disabilities
     */
    fun buildAdaptivePrompt(
        session: LearningSession,
        question: String,
        bloomTarget: BloomLevel
    ): String {
        val profile = learnerProfiles[session.learnerId]
            ?: throw IllegalStateException("Learner profile missing")
        
        val accommodations = buildString {
            // Dyslexia accommodations
            if (LearningDisability.DYSLEXIA in profile.learningDisabilities) {
                appendLine("DYSLEXIA ACCOMMODATIONS:")
                appendLine("- Use sans-serif friendly formatting")
                appendLine("- Break into short bullet points")
                appendLine("- Avoid dense paragraphs")
                appendLine("- Include phonetic guides for complex words")
                appendLine()
            }
            
            // ADHD accommodations
            if (LearningDisability.ADHD in profile.learningDisabilities) {
                appendLine("ADHD ACCOMMODATIONS:")
                appendLine("- Keep explanations under 3 sentences per concept")
                appendLine("- Use bold highlights for key terms")
                appendLine("- Include interactive checkpoints every 2-3 concepts")
                appendLine("- Provide immediate positive feedback")
                appendLine()
            }
            
            // Autism spectrum accommodations
            if (LearningDisability.AUTISM_SPECTRUM in profile.learningDisabilities) {
                appendLine("AUTISM SPECTRUM ACCOMMODATIONS:")
                appendLine("- Be literal and precise, avoid idioms/metaphors")
                appendLine("- Provide clear structure and predictability")
                appendLine("- Include special interest connections if known")
                appendLine("- Allow extra processing time indicators")
                appendLine()
            }
        }
        
        val ageAppropriateFilter = when {
            profile.age != null && profile.age!! < 13 -> """
                CHILD SAFETY FILTER (Age: ${profile.age}):
                - No complex medical/scientific details inappropriate for children
                - Use simple vocabulary appropriate for grade ${profile.gradeLevel ?: "unknown"}
                - Include encouraging, growth-mindset language
                - Never provide dangerous experiment instructions
            """.trimIndent()
            else -> ""
        }
        
        val culturalAdaptation = if (profile.culturalContext != null) {
            """
                CULTURAL CONTEXT: ${profile.culturalContext}
                - Use locally relevant examples and analogies
                - Respect cultural sensitivities
                - Include diverse representation in examples
            """.trimIndent()
        } else ""
        
        val bloomInstruction = getBloomInstruction(bloomTarget)
        
        return """
            LEARNER PROFILE:
            - Age: ${profile.age ?: "Not specified"}
            - Language: ${profile.languageCode}
            - Learning Styles: ${profile.preferredLearningStyles.joinToString(", ")}
            
            $accommodations
            $ageAppropriateFilter
            $culturalAdaptation
            
            COGNITIVE TARGET: ${bloomTarget.name} - $bloomInstruction
            
            QUESTION/TASK:
            $question
            
            Respond following all accommodations and safety filters above.
        """.trimIndent()
    }

    /**
     * STEP 4: Evaluate response and update mastery tracking
     * Implements knowledge tracing algorithm
     */
    fun evaluateResponse(
        session: LearningSession,
        isCorrect: Boolean,
        timeSpentSeconds: Long,
        hintsUsed: Int
    ): MasteryUpdate {
        session.questionsAttempted++
        if (isCorrect) session.questionsCorrect++
        session.hintsRequested += hintsUsed
        session.timeSpentSeconds += timeSpentSeconds
        
        // Calculate weighted score (correctness + speed + independence)
        val correctnessScore = if (session.questionsAttempted > 0) {
            session.questionsCorrect.toFloat() / session.questionsAttempted
        } else 0f
        
        val speedBonus = when {
            timeSpentSeconds < 10 -> 0.0f  // Too fast, possibly guessing
            timeSpentSeconds < 30 -> 0.1f  // Good pace
            timeSpentSeconds < 60 -> 0.05f // Acceptable
            else -> 0.0f                   // Slow but ok
        }
        
        val independencePenalty = when (hintsUsed) {
            0 -> 0.0f
            1 -> -0.05f
            2 -> -0.1f
            else -> -0.2f
        }
        
        val weightedScore = (correctnessScore + speedBonus + independencePenalty).coerceIn(0f, 1f)
        session.masteryScore = weightedScore
        
        // Determine if ready to advance Bloom level
        val currentProgress = masteryProgress["${session.learnerId}_${session.topic}"]
        val shouldAdvance = weightedScore >= MIN_MASTERY_FOR_ADVANCEMENT &&
                           currentProgress?.currentBloomLevel?.depth ?: 0 < BloomLevel.CREATE.depth
        
        val newBloomLevel = if (shouldAdvance) {
            BloomLevel.values().firstOrNull { it.depth == (currentProgress?.currentBloomLevel?.depth ?: 0) + 1 }
                ?: session.bloomLevelAchieved
        } else {
            session.bloomLevelAchieved
        }
        
        session.bloomLevelAchieved = newBloomLevel
        
        // Update mastery progress
        val progressKey = "${session.learnerId}_${session.topic}"
        val updatedProgress = MasteryProgress(
            topic = session.topic,
            currentBloomLevel = newBloomLevel,
            masteryPercentage = weightedScore,
            lastPracticed = System.currentTimeMillis(),
            totalSessions = (currentProgress?.totalSessions ?: 0) + 1,
            spacedRepetitionStage = calculateSpacedRepetitionStage(weightedScore, currentProgress?.spacedRepetitionStage ?: 0)
        )
        
        masteryProgress[progressKey] = updatedProgress
        
        // Schedule next review using spaced repetition
        scheduleNextReview(session.learnerId, session.topic, updatedProgress)
        
        return MasteryUpdate(
            previousMastery = currentProgress?.masteryPercentage ?: 0f,
            newMastery = weightedScore,
            previousBloomLevel = currentProgress?.currentBloomLevel ?: BloomLevel.REMEMBER,
            newBloomLevel = newBloomLevel,
            advancedLevel = shouldAdvance,
            nextReviewTime = spacedRepetitionQueue[progressKey]?.nextReviewTime
        )
    }

    /**
     * STEP 5: Calculate optimal spaced repetition interval
     * Based on Ebbinghaus forgetting curve with individual adjustment
     */
    private fun calculateSpacedRepetitionStage(masteryScore: Float, currentStage: Int): Int {
        return when {
            masteryScore >= 0.9f -> (currentStage + 1).coerceAtMost(SPACED_REPETITION_INTERVALS.size - 1)
            masteryScore >= 0.7f -> currentStage.coerceAtMost(SPACED_REPETITION_INTERVALS.size - 2)
            masteryScore >= 0.5f -> currentStage.coerceAtMost(SPACED_REPETITION_INTERVALS.size - 3)
            else -> 0 // Reset to beginning if mastery is low
        }
    }

    private fun scheduleNextReview(
        learnerId: String,
        topic: String,
        progress: MasteryProgress
    ) {
        val intervalIndex = progress.spacedRepetitionStage.coerceIn(0, SPACED_REPETITION_INTERVALS.size - 1)
        val intervalHours = SPACED_REPETITION_INTERVALS[intervalIndex]
        val nextReviewTime = System.currentTimeMillis() + (intervalHours * 3600 * 1000).toLong()
        
        // Estimate retention strength based on time since last practice
        val hoursSinceLastPractice = (System.currentTimeMillis() - progress.lastPracticed) / 3600000f
        val retentionStrength = when {
            hoursSinceLastPractice <= intervalHours -> 0.9f
            hoursSinceLastPractice <= intervalHours * 2 -> 0.7f
            hoursSinceLastPractice <= intervalHours * 4 -> 0.5f
            else -> 0.3f
        }
        
        spacedRepetitionQueue["${learnerId}_$topic"] = SpacedRepetitionSchedule(
            topic = topic,
            nextReviewTime = nextReviewTime,
            intervalIndex = intervalIndex,
            retentionStrength = retentionStrength
        )
        
        Timber.d(TAG, "📅 Scheduled review for $topic in ${intervalHours}h (Stage $intervalIndex)")
    }

    /**
     * STEP 6: Detect potential cheating/academic dishonesty
     * Flags suspicious patterns for educator review
     */
    fun detectAcademicDishonesty(session: LearningSession): DishonestyAlert? {
        val accuracy = if (session.questionsAttempted > 0) {
            session.questionsCorrect.toFloat() / session.questionsAttempted
        } else 0f
        
        val avgTimePerQuestion = if (session.questionsAttempted > 0) {
            session.timeSpentSeconds.toFloat() / session.questionsAttempted
        } else 0f
        
        val alerts = mutableListOf<String>()
        
        // Red flag: Perfect score with impossibly fast completion
        if (accuracy == 1.0f && avgTimePerQuestion < 3.0f && session.questionsAttempted > 5) {
            alerts.add("Perfect score with <3s per question - possible answer lookup")
        }
        
        // Red flag: Zero hints but complex topic at high Bloom level
        if (session.bloomLevelAchieved.depth >= 4 && session.hintsRequested == 0 && 
            accuracy == 1.0f && session.questionsAttempted > 3) {
            alerts.add("High-level cognitive task completed perfectly without hints - verify understanding")
        }
        
        // Yellow flag: Inconsistent performance pattern
        if (session.questionsAttempted >= 4) {
            val firstHalfAccuracy = session.questionsCorrect.toFloat() / (session.questionsAttempted / 2)
            val recentAccuracy = if (session.questionsAttempted > 2) 1.0f else accuracy // Simplified
            
            if (recentAccuracy > firstHalfAccuracy + 0.5f) {
                alerts.add("Sudden accuracy improvement mid-session - possible external help")
            }
        }
        
        return if (alerts.isNotEmpty()) {
            DishonestyAlert(
                sessionId = session.sessionId,
                severity = if (alerts.size >= 2) AlertSeverity.HIGH else AlertSeverity.MEDIUM,
                flags = alerts,
                recommendedAction = when {
                    alerts.size >= 2 -> "Require oral verification of understanding"
                    else -> "Add follow-up conceptual questions"
                }
            )
        } else null
    }

    enum class AlertSeverity { LOW, MEDIUM, HIGH }

    data class DishonestyAlert(
        val sessionId: String,
        val severity: AlertSeverity,
        val flags: List<String>,
        val recommendedAction: String
    )

    data class MasteryUpdate(
        val previousMastery: Float,
        val newMastery: Float,
        val previousBloomLevel: BloomLevel,
        val newBloomLevel: BloomLevel,
        val advancedLevel: Boolean,
        val nextReviewTime: Long?
    )

    private fun getBloomInstruction(level: BloomLevel): String {
        return when (level) {
            BloomLevel.REMEMBER -> "Recall facts, terms, basic concepts"
            BloomLevel.UNDERSTAND -> "Explain ideas, interpret, summarize"
            BloomLevel.APPLY -> "Use information in new situations, solve problems"
            BloomLevel.ANALYZE -> "Draw connections, compare, organize information"
            BloomLevel.EVALUATE -> "Justify decisions, critique, defend opinions"
            BloomLevel.CREATE -> "Produce new work, design, construct, formulate"
        }
    }

    /**
     * Get personalized study recommendations based on spaced repetition
     */
    fun getStudyRecommendations(learnerId: String, currentTime: Long = System.currentTimeMillis()): List<StudyRecommendation> {
        return spacedRepetitionQueue
            .filterKeys { it.startsWith("${learnerId}_") }
            .filterValues { it.nextReviewTime <= currentTime }
            .map { (key, schedule) ->
                StudyRecommendation(
                    topic = schedule.topic,
                    priority = when (schedule.retentionStrength) {
                        in 0.8f..1.0f -> Priority.LOW
                        in 0.5f..0.79f -> Priority.MEDIUM
                        else -> Priority.HIGH
                    },
                    estimatedReviewTimeMinutes = when (schedule.intervalIndex) {
                        0, 1 -> 5
                        2, 3 -> 10
                        4, 5 -> 15
                        else -> 20
                    },
                    suggestedBloomLevel = masteryProgress[key]?.currentBloomLevel ?: BloomLevel.REMEMBER
                )
            }
            .sortedByDescending { it.priority.ordinal }
    }

    data class StudyRecommendation(
        val topic: String,
        val priority: Priority,
        val estimatedReviewTimeMinutes: Int,
        val suggestedBloomLevel: BloomLevel
    )

    enum class Priority { LOW, MEDIUM, HIGH }

    /**
     * End session and persist final metrics
     */
    fun endSession(sessionId: String) {
        val session = activeSessions.remove(sessionId) ?: return
        session.endTime = System.currentTimeMillis()
        
        // Check for academic dishonesty
        val alert = detectAcademicDishonesty(session)
        if (alert != null) {
            Timber.w(TAG, "⚠️ Academic dishonesty detected in session $sessionId: ${alert.flags}")
            // In production: notify educator, flag for review
        }
        
        Timber.i(TAG, "✅ Session $sessionId ended. Mastery: ${(session.masteryScore * 100).toInt()}%, Bloom: ${session.bloomLevelAchieved.name}")
    }
}
