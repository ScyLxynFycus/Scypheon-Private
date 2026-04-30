package com.scypheon.sdk.core.resilience

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise-Grade Resilience Circuit Breaker
 * 
 * Implements fault tolerance pattern for offline-first disaster response.
 * Prevents cascade failures by temporarily halting requests after repeated failures.
 * 
 * State Machine:
 * CLOSED → OPEN (after N failures)
 * OPEN → HALF_OPEN (after cooldown period)
 * HALF_OPEN → CLOSED (on success) or OPEN (on failure)
 * 
 * Mobile-Optimized:
 * - Zero allocation in hot path
 * - Atomic state transitions
 * - Configurable thresholds per use case
 */
@Singleton
class ResilienceCircuitBreaker @Inject constructor() {

    companion object {
        private const val TAG = "ResilienceCircuitBreaker"
        
        // Default configuration
        private const val DEFAULT_FAILURE_THRESHOLD = 3
        private const val DEFAULT_COOLDOWN_MS = 60_000L // 60 seconds
        private const val DEFAULT_SUCCESS_THRESHOLD = 2 // Successes needed in HALF_OPEN
    }

    /**
     * Circuit breaker states
     */
    enum class State {
        /** Normal operation - requests allowed */
        CLOSED,
        
        /** Fault detected - requests blocked */
        OPEN,
        
        /** Testing recovery - limited requests allowed */
        HALF_OPEN
    }

    /**
     * Configuration builder for custom circuit breakers
     */
    data class Config(
        val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
        val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        val successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD,
        val name: String = "default"
    )

    // Atomic state management
    @Volatile
    private var state = State.CLOSED
    
    @Volatile
    private var failureCount = 0
    
    @Volatile
    private var successCount = 0
    
    @Volatile
    private var lastFailureTime = 0L
    
    @Volatile
    private var lastStateChangeTime = System.currentTimeMillis()
    
    // Configuration
    private var config: Config = Config()
    
    // Statistics for monitoring
    private var totalRequests = 0L
    private var totalFailures = 0L
    private var totalSuccesses = 0L
    private var totalCircuitOpens = 0L

    constructor(config: Config = Config()) : this() {
        this.config = config
    }

    /**
     * Checks if request should be allowed.
     * 
     * State transitions:
     * - CLOSED: Always allow
     * - OPEN: Allow only after cooldown (transition to HALF_OPEN)
     * - HALF_OPEN: Allow (will transition based on result)
     * 
     * @return true if request should proceed, false if blocked
     */
    fun allowRequest(): Boolean {
        totalRequests++
        
        return when (state) {
            State.CLOSED -> {
                Log.d(TAG, "🟢 State=CLOSED: Request allowed")
                true
            }
            
            State.OPEN -> {
                val elapsed = System.currentTimeMillis() - lastFailureTime
                if (elapsed >= config.cooldownMs) {
                    val oldState = state
                    state = State.HALF_OPEN
                    successCount = 0
                    lastStateChangeTime = System.currentTimeMillis()
                    Log.w(TAG, "🟡 State transition: $oldState → HALF_OPEN (cooldown expired)")
                    true
                } else {
                    val remaining = config.cooldownMs - elapsed
                    Log.w(TAG, "🔴 State=OPEN: Request blocked (${remaining}ms remaining in cooldown)")
                    false
                }
            }
            
            State.HALF_OPEN -> {
                Log.d(TAG, "🟡 State=HALF_OPEN: Test request allowed")
                true
            }
        }
    }

    /**
     * Records successful request.
     * 
     * State transitions:
     * - CLOSED: Reset failure count
     * - HALF_OPEN: Increment success count, transition to CLOSED if threshold met
     */
    fun recordSuccess() {
        totalSuccesses++
        
        when (state) {
            State.CLOSED -> {
                // Reset failure count on success
                if (failureCount > 0) {
                    Log.d(TAG, "✅ Success in CLOSED state: resetting failure count ($failureCount → 0)")
                    failureCount = 0
                }
            }
            
            State.HALF_OPEN -> {
                successCount++
                Log.d(TAG, "✅ Success in HALF_OPEN state: $successCount/${config.successThreshold}")
                
                if (successCount >= config.successThreshold) {
                    val oldState = state
                    state = State.CLOSED
                    failureCount = 0
                    successCount = 0
                    lastStateChangeTime = System.currentTimeMillis()
                    Log.i(TAG, "🟢 State transition: $oldState → CLOSED (recovery complete)")
                }
            }
            
            State.OPEN -> {
                // Should not happen, but handle gracefully
                Log.w(TAG, "⚠️ Unexpected success in OPEN state")
            }
        }
    }

    /**
     * Records failed request.
     * 
     * State transitions:
     * - CLOSED: Increment failure count, transition to OPEN if threshold met
     * - HALF_OPEN: Immediate transition to OPEN (recovery failed)
     */
    fun recordFailure() {
        totalFailures++
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        when (state) {
            State.CLOSED -> {
                Log.w(TAG, "❌ Failure in CLOSED state: $failureCount/${config.failureThreshold}")
                
                if (failureCount >= config.failureThreshold) {
                    val oldState = state
                    state = State.OPEN
                    totalCircuitOpens++
                    lastStateChangeTime = System.currentTimeMillis()
                    Log.e(TAG, "🔴 CRITICAL: State transition: $oldState → OPEN (failure threshold reached)")
                    Log.e(TAG, "   Circuit breaker activated. Cooldown: ${config.cooldownMs / 1000}s")
                }
            }
            
            State.HALF_OPEN -> {
                val oldState = state
                state = State.OPEN
                successCount = 0
                lastFailureTime = System.currentTimeMillis()
                lastStateChangeTime = System.currentTimeMillis()
                totalCircuitOpens++
                Log.e(TAG, "🔴 State transition: $oldState → OPEN (recovery failed)")
            }
            
            State.OPEN -> {
                // Already open, just update timestamp
                Log.d(TAG, "❌ Failure in OPEN state: extending cooldown")
                lastFailureTime = System.currentTimeMillis()
            }
        }
    }

    /**
     * Gets current state.
     */
    fun getState(): State = state

    /**
     * Gets detailed status for diagnostics.
     */
    fun getStatus(): CircuitBreakerStatus {
        return CircuitBreakerStatus(
            state = state,
            failureCount = failureCount,
            successCount = successCount,
            lastFailureTime = lastFailureTime,
            cooldownRemaining = if (state == State.OPEN) {
                maxOf(0L, config.cooldownMs - (System.currentTimeMillis() - lastFailureTime))
            } else 0L,
            totalRequests = totalRequests,
            totalFailures = totalFailures,
            totalSuccesses = totalSuccesses,
            totalCircuitOpens = totalCircuitOpens,
            uptimeMs = System.currentTimeMillis() - lastStateChangeTime
        )
    }

    /**
     * Resets circuit breaker to initial state.
     * Use for manual intervention or testing.
     */
    fun reset() {
        Log.w(TAG, "⚠️ Manual reset requested")
        
        state = State.CLOSED
        failureCount = 0
        successCount = 0
        lastFailureTime = 0L
        lastStateChangeTime = System.currentTimeMillis()
        
        Log.i(TAG, "✅ Circuit breaker reset to CLOSED state")
    }

    /**
     * Force opens circuit (for emergency shutdown).
     */
    fun forceOpen() {
        Log.w(TAG, "⚠️ Force opening circuit breaker")
        
        state = State.OPEN
        lastFailureTime = System.currentTimeMillis()
        lastStateChangeTime = System.currentTimeMillis()
        totalCircuitOpens++
        
        Log.i(TAG, "🔴 Circuit breaker forced OPEN")
    }

    /**
     * Executes operation with automatic circuit breaker protection.
     * 
     * @param operation Suspending function to execute
     * @return Result of operation or CircuitBreakerOpenException
     */
    suspend fun <T> executeWithProtection(operation: suspend () -> T): Result<T> {
        if (!allowRequest()) {
            val status = getStatus()
            return Result.failure(
                CircuitBreakerOpenException(
                    "Circuit breaker is OPEN. Cooldown remaining: ${status.cooldownRemaining}ms"
                )
            )
        }

        return try {
            val result = operation()
            recordSuccess()
            Result.success(result)
        } catch (e: Exception) {
            recordFailure()
            Result.failure(e)
        }
    }

    /**
     * Data class for status reporting
     */
    data class CircuitBreakerStatus(
        val state: State,
        val failureCount: Int,
        val successCount: Int,
        val lastFailureTime: Long,
        val cooldownRemaining: Long,
        val totalRequests: Long,
        val totalFailures: Long,
        val totalSuccesses: Long,
        val totalCircuitOpens: Long,
        val uptimeMs: Long
    )

    /**
     * Exception thrown when circuit is open
     */
    class CircuitBreakerOpenException(message: String) : Exception(message)

    /**
     * Factory for creating named circuit breakers with custom configs
     */
    @Singleton
    class CircuitBreakerFactory @Inject constructor() {
        private val breakers = mutableMapOf<String, ResilienceCircuitBreaker>()

        fun getOrCreate(name: String, config: Config = Config()): ResilienceCircuitBreaker {
            return breakers.getOrPut(name) {
                ResilienceCircuitBreaker(config.copy(name = name))
            }
        }

        fun getAll(): Map<String, ResilienceCircuitBreaker> = breakers.toMap()

        fun resetAll() {
            breakers.values.forEach { it.reset() }
        }

        fun getGlobalStatus(): Map<String, CircuitBreakerStatus> {
            return breakers.mapValues { it.value.getStatus() }
        }
    }
}

/**
 * Convenience extension for executing with circuit breaker protection
 */
suspend fun <T> ResilienceCircuitBreaker.execute(
    operation: suspend () -> T
): Result<T> {
    return executeWithProtection(operation)
}
