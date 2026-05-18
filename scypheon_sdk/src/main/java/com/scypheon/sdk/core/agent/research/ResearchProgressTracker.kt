package com.scypheon.sdk.core.agent.research

import java.util.concurrent.CopyOnWriteArrayList

/**
 * ResearchProgressTracker: UX Feedback for Deep Research Mode
 */
object ResearchProgressTracker {

    private var currentPhase = Phase.IDLE
    private val progressListeners = CopyOnWriteArrayList<(ProgressEvent) -> Unit>()
    
    private var researchStartTime = 0L

    enum class Phase {
        IDLE,
        PLANNING,
        SEARCHING,
        EXTRACTING,
        REASONING,
        SUMMARIZING,
        COMPLETE
    }

    data class ProgressEvent(
        val phase: Phase,
        val message: String,
        val progress: Float,
        val elapsedMs: Long,
        val animated: Boolean = true
    )

    fun start() {
        researchStartTime = System.currentTimeMillis()
        emit(Phase.PLANNING, "🧠 Creating research plan...", 0.1f)
    }

    fun searching(query: String? = null) {
        val msg = if (query != null) {
            "🔍 Searching: \"$query\"..."
        } else {
            "🔍 Searching the web..."
        }
        emit(Phase.SEARCHING, msg, 0.3f)
    }

    fun extracting(sourceCount: Int = 0) {
        val msg = if (sourceCount > 0) {
            "📄 Extracting from $sourceCount sources..."
        } else {
            "📄 Extracting key points..."
        }
        emit(Phase.EXTRACTING, msg, 0.5f)
    }

    fun reasoning() {
        emit(Phase.REASONING, "🤔 Analyzing information...", 0.7f)
    }

    fun summarizing() {
        emit(Phase.SUMMARIZING, "✍️ Writing summary...", 0.9f)
    }

    fun complete() {
        if (currentPhase != Phase.IDLE) {
            emit(Phase.COMPLETE, "✓ Research complete!", 1.0f, animated = false)
            currentPhase = Phase.IDLE
        }
    }

    fun reset() {
        currentPhase = Phase.IDLE
        researchStartTime = 0L
    }

    fun addListener(listener: (ProgressEvent) -> Unit) {
        if (!progressListeners.contains(listener)) {
            progressListeners.add(listener)
        }
    }

    fun removeListener(listener: (ProgressEvent) -> Unit) {
        progressListeners.remove(listener)
    }

    private fun emit(phase: Phase, message: String, progress: Float, animated: Boolean = true) {
        currentPhase = phase
        val elapsed = System.currentTimeMillis() - researchStartTime
        
        val event = ProgressEvent(
            phase = phase,
            message = message,
            progress = progress,
            elapsedMs = elapsed,
            animated = animated
        )
        
        progressListeners.forEach { it(event) }
    }

    fun getCurrentPhase() = currentPhase
}
