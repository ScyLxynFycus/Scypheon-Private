package com.scypheon.sdk.core.resilience

import com.scypheon.sdk.core.annotations.SafetyCritical

/**
 * ResilienceCircuitBreaker: Prevents system cascade failure by isolating failing components.
 * Enterprise-grade implementation supporting multiple isolated circuits.
 */
interface ResilienceCircuitBreaker {
    /**
     * Checks if a request is allowed for the given key.
     */
    fun allowRequest(key: String = "default"): Boolean

    /**
     * Records a success for the given key, potentially closing the circuit.
     */
    fun recordSuccess(key: String = "default")

    /**
     * Records a failure for the given key, potentially opening the circuit.
     */
    fun recordFailure(key: String = "default")

    /**
     * Executes a block of code with circuit breaker protection.
     */
    suspend fun <T> execute(key: String, block: suspend () -> T): T
}

enum class State { CLOSED, OPEN, HALF_OPEN }

class CircuitBreakerOpenException(msg: String) : Exception(msg)
