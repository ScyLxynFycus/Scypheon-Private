package com.scypheon.sdk.core.safety.helios

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class SafetyVerdict { SAFE, FLAGGED, BLOCKED }

/**
 * Tracks cumulative risk per session for adaptive escalation.
 * Thread-safe, memory-efficient, offline-compatible.
 *
 * [v1.5.3-SAR] HELIOS HARDENING: Time-based decay.
 * Old implementation applied RISK_DECAY_PER_MINUTE on every call regardless of elapsed time.
 * An attacker could flush risk by sending benign messages quickly.
 * Now decay is proportional to actual elapsed minutes since last evaluation.
 */
@Singleton
class SessionRiskManager @Inject constructor() {
    companion object {
        private const val RISK_DECAY_PER_MINUTE = 0.1f
        private const val LOCKDOWN_THRESHOLD = 10.0f
        private const val FLAGGED_WEIGHT = 1.0f
        private const val BLOCKED_WEIGHT = 3.0f
        private const val MAX_TRACKED_SESSIONS = 100
    }

    private data class SessionRisk(
        val score: Float,
        val lastUpdated: Long
    )

    private val sessionScores = mutableMapOf<String, SessionRisk>()
    private val sessionMutex = Mutex()

    /**
     * Updates risk score for a session based on safety verdict.
     * Returns true if session should be locked down.
     */
    suspend fun updateRisk(sessionId: String, verdict: SafetyVerdict): Boolean = sessionMutex.withLock {
        val now = System.currentTimeMillis()
        val current = sessionScores[sessionId]
        
        // Time-based decay: proportional to actual elapsed minutes
        val decayedScore = if (current != null) {
            val elapsedMinutes = ((now - current.lastUpdated) / 60_000f).coerceAtMost(30f)
            maxOf(0.0f, current.score - (RISK_DECAY_PER_MINUTE * elapsedMinutes))
        } else 0.0f

        val increment = when (verdict) {
            SafetyVerdict.FLAGGED -> FLAGGED_WEIGHT
            SafetyVerdict.BLOCKED -> BLOCKED_WEIGHT
            SafetyVerdict.SAFE -> 0.0f
        }
        
        val newScore = decayedScore + increment
        sessionScores[sessionId] = SessionRisk(newScore, now)
        
        // Evict oldest sessions if we exceed capacity (prevent memory leak)
        if (sessionScores.size > MAX_TRACKED_SESSIONS) {
            val oldest = sessionScores.entries.minByOrNull { it.value.lastUpdated }?.key
            if (oldest != null && oldest != sessionId) {
                sessionScores.remove(oldest)
            }
        }
        
        newScore >= LOCKDOWN_THRESHOLD
    }

    /**
     * Checks if session is currently locked down.
     */
    suspend fun isLockedDown(sessionId: String): Boolean = sessionMutex.withLock {
        val current = sessionScores[sessionId] ?: return@withLock false
        val now = System.currentTimeMillis()
        val elapsedMinutes = ((now - current.lastUpdated) / 60_000f).coerceAtMost(30f)
        val decayedScore = maxOf(0.0f, current.score - (RISK_DECAY_PER_MINUTE * elapsedMinutes))
        decayedScore >= LOCKDOWN_THRESHOLD
    }

    /**
     * Resets risk for a session (e.g., after successful authentication).
     */
    suspend fun resetRisk(sessionId: String): Unit = sessionMutex.withLock {
        sessionScores.remove(sessionId)
        Unit
    }
}
