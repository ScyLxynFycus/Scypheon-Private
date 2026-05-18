package com.scypheon.sdk.core.agent.ooda

import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// --- Data Classes ---
data class SessionContext(val id: String, val userId: String = "anonymous")
data class DeviceEnvironment(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val thermalStatus: ThermalStatus,
    val networkType: String
)
enum class ThermalStatus { NORMAL, WARM, CRITICAL }
enum class InputModality { TEXT, VOICE, IMAGE }

data class Observation(
    val sessionId: String, // Added sessionId to propagate context to ORRIGA
    val query: String,
    val context: List<String>,
    val environmentSnapshot: DeviceEnvironment,
    val isUrgent: Boolean,
    val modality: InputModality,
    val timestamp: Long,
    val confidenceScore: Float = 1.0f
)

/**
 * Interface for conversation history retrieval (Dependency Inversion)
 */
interface ConversationRepository {
    suspend fun getRecentTurns(sessionId: String, windowSize: Int): List<String>
}

/**
 * Interface for urgency classification (Allows swapping Rule Engine vs ML)
 */
interface UrgencyClassifier {
    suspend fun classify(query: String): UrgencyResult
}

data class UrgencyResult(val isUrgent: Boolean, val confidence: Float, val reason: String)

/**
 * Step 1: OBSERVE
 * Gathers all necessary information to understand the user's query without
 * performing deep investigation. Hardened with timeout protection and DI.
 */
@Singleton
class ObserveStep @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val urgencyClassifier: UrgencyClassifier
) {
    companion object {
        private const val OBSERVATION_TIMEOUT_MS = 500L
    }

    suspend fun execute(
        query: String,
        session: SessionContext,
        environment: DeviceEnvironment,
        modality: InputModality = InputModality.TEXT 
    ): Observation {
        Timber.d("👁️ [OODA_OBSERVE] Starting observation for session: ${session.id}")

        val recentTurns = withTimeoutOrNull(OBSERVATION_TIMEOUT_MS) {
            conversationRepo.getRecentTurns(session.id, 3)
        } ?: emptyList()

        val urgencyResult = urgencyClassifier.classify(query)
        
        if (urgencyResult.isUrgent) {
            Timber.w("🚨 [OODA_OBSERVE] Urgency detected: ${urgencyResult.reason} (Conf: ${urgencyResult.confidence})")
        }

        return Observation(
            sessionId = session.id,
            query = query,
            context = recentTurns,
            environmentSnapshot = environment,
            isUrgent = urgencyResult.isUrgent,
            modality = modality,
            timestamp = System.currentTimeMillis(),
            confidenceScore = urgencyResult.confidence
        )
    }
}
