package com.scypheon.sdk.core.resilience

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * ResilienceCircuitBreaker: Enterprise-grade fault tolerance.
 * Prevents system degradation by "opening" the circuit when a component (e.g., BLE Mesh, FTS5) 
 * fails repeatedly. Implements exponential backoff for recovery attempts.
 */
interface ResilienceCircuitBreaker {
    
    enum class State { CLOSED, OPEN, HALF_OPEN }

    fun allowRequest(key: String): Boolean
    fun recordSuccess(key: String)
    fun recordFailure(key: String, throwable: Throwable? = null)
    
    /**
     * Executes a block of code protected by the circuit breaker.
     */
    suspend fun <T> execute(key: String, block: suspend () -> T): T
}

class CircuitBreakerOpenException(msg: String) : Exception(msg)
