package com.scypheon.sdk.core.safety.helios

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * ConversationStateStore: Tracks message history for stateful safety analysis.
 * Essential for preventing multi-turn jailbreak attempts.
 *
 * [v1.5.3-SAR] HELIOS HARDENING: Added TTL eviction to prevent unbounded memory growth.
 * Old implementation never evicted sessions, causing memory leak over app lifetime.
 */
@Singleton
class ConversationStateStore @Inject constructor() {
    
    private data class SessionEntry(
        val turns: MutableList<String>,
        val lastAccessed: Long
    )
    
    private val history = ConcurrentHashMap<String, SessionEntry>()
    private val MAX_HISTORY = 3
    private val MAX_SESSIONS = 50
    private val SESSION_TTL_MS = 30 * 60 * 1000L // 30 minutes

    fun recordTurn(sessionId: String, prompt: String) {
        evictStale()
        val entry = history.getOrPut(sessionId) { 
            SessionEntry(mutableListOf(), System.currentTimeMillis()) 
        }
        entry.turns.add(prompt)
        if (entry.turns.size > MAX_HISTORY) {
            entry.turns.removeAt(0)
        }
        // Update last accessed
        history[sessionId] = entry.copy(lastAccessed = System.currentTimeMillis())
    }

    fun getFullContext(sessionId: String): String {
        val entry = history[sessionId] ?: return ""
        // Touch: update last accessed
        history[sessionId] = entry.copy(lastAccessed = System.currentTimeMillis())
        return entry.turns.joinToString("\n--TURN--\n")
    }

    fun clearSession(sessionId: String) {
        history.remove(sessionId)
    }

    /**
     * Evicts sessions that haven't been accessed within TTL,
     * and enforces max session count by removing oldest entries.
     */
    private fun evictStale() {
        val now = System.currentTimeMillis()
        
        // 1. TTL eviction
        val staleKeys = history.entries
            .filter { now - it.value.lastAccessed > SESSION_TTL_MS }
            .map { it.key }
        staleKeys.forEach { history.remove(it) }

        // 2. Capacity eviction (keep newest MAX_SESSIONS)
        if (history.size > MAX_SESSIONS) {
            val sortedByAge = history.entries.sortedBy { it.value.lastAccessed }
            val toEvict = sortedByAge.take(history.size - MAX_SESSIONS)
            toEvict.forEach { history.remove(it.key) }
        }
    }
}
