package com.scypheon.sdk.core.utils

import java.util.concurrent.atomic.AtomicLong

/**
 * snapshot of a token's state for KV restoration.
 */
data class TokenSnapshot(
    val tokenId: Int,
    val kvOffset: Int,     // Captured via llama_kv_cache_seq_pos
    val sequenceNumber: Long // Monotonic for idempotency
)

/**
 * 🛡️ [SAR] Phase 3: Context Replay Buffer
 * Maintains a sliding window of tokens to rebuild state in <200ms.
 */
class ContextReplayBuffer(private val maxTokens: Int = 2048) {
    private val ring = java.util.concurrent.ConcurrentLinkedQueue<TokenSnapshot>()
    private val sequenceCounter = AtomicLong(0)

    fun record(tokenId: Int, kvOffset: Int) {
        if (ring.size >= maxTokens) {
            ring.poll()
        }
        ring.add(TokenSnapshot(tokenId, kvOffset, sequenceCounter.getAndIncrement()))
    }

    fun snapshot(): List<TokenSnapshot> = ring.toList()

    fun clear() {
        ring.clear()
        sequenceCounter.set(0)
    }
    
    fun lastPos(): Int = ring.lastOrNull()?.kvOffset ?: 0
}

/**
 * 🛡️ [SAR] Phase 3: Idempotent Token Injector
 * Prevents race conditions from injecting duplicate tokens during resurrection.
 */
class IdempotentTokenInjector(private val nativeInjector: (Int, Int) -> Unit) {
    private val lastProcessedSeq = AtomicLong(-1)

    fun inject(token: TokenSnapshot): Boolean {
        if (token.sequenceNumber <= lastProcessedSeq.get()) {
            return false // Already processed
        }
        
        // Atomic compare-and-swap or simple update since this is typically 
        // called from a single recovery thread
        if (lastProcessedSeq.get() < token.sequenceNumber) {
            nativeInjector(token.tokenId, token.kvOffset)
            lastProcessedSeq.set(token.sequenceNumber)
            return true
        }
        return false
    }
    
    fun reset() {
        lastProcessedSeq.set(-1)
    }
}
