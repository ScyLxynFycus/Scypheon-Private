package com.scypheon.sdk.core.resilience

import com.scypheon.sdk.core.annotations.SafetyCritical
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker.State

@SafetyCritical
@Singleton
class DefaultResilienceCircuitBreaker @Inject constructor() : ResilienceCircuitBreaker {

    var maxFailures: Int = 3
    var resetTimeoutMs: Long = 30000L

    private val circuits = ConcurrentHashMap<String, CircuitState>()
<<<<<<< Updated upstream
=======
    private val failureThreshold = 5
>>>>>>> Stashed changes

    /** Lock-free circuit state container using atomics for non-blocking thread-safety. */
    private class CircuitState {
        val state = AtomicReference(State.CLOSED)
        val failures = AtomicInteger(0)
        val lastFailureTime = AtomicLong(0L)
        val cooldownMs = AtomicLong(30000L) // Default base cooldown is 30 seconds
    }

    private fun getCircuit(key: String): CircuitState {
        return circuits.computeIfAbsent(key) { CircuitState() }
    }

    private fun isCriticalFailure(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val className = throwable.javaClass.name
        val msg = throwable.message?.lowercase() ?: ""
        return throwable is java.lang.UnsatisfiedLinkError ||
               throwable is java.lang.OutOfMemoryError ||
               className.contains("DeadObjectException") ||
               className.contains("RemoteException") ||
               msg.contains("jni") ||
               msg.contains("linkage") ||
               msg.contains("dead object") ||
               msg.contains("binder died") ||
               msg.contains("sandbox process died")
    }

    override fun allowRequest(key: String): Boolean {
        val circuit = getCircuit(key)
        val currentState = circuit.state.get()

        if (currentState == State.CLOSED) return true

        if (currentState == State.OPEN) {
            val timeSinceFailure = System.currentTimeMillis() - circuit.lastFailureTime.get()
<<<<<<< Updated upstream
            if (timeSinceFailure > resetTimeoutMs) {
                // HALF_OPEN Probe: Gunakan compareAndSet agar hanya SATU thread yang lolos menjadi probe
=======
            val currentCooldown = circuit.cooldownMs.get()
            
            if (timeSinceFailure > currentCooldown) {
                // HALF_OPEN Probe: CAS ensures exactly one thread wins the probe race
>>>>>>> Stashed changes
                if (circuit.state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    Timber.d("🛡️ [CIRCUIT] $key shifted to HALF_OPEN (Probe dispatched)")
                    return true
                }
                // Lost CAS race — another thread is already probing. Reject this request.
                return false
            }
            Timber.w("🚨 [CIRCUIT] Request blocked. $key is OPEN. Cooldown remaining: ${currentCooldown - timeSinceFailure}ms")
            return false
        }

        // HALF_OPEN: reject all requests until the active probe completes (success or failure)
        return false
    }

    override fun recordSuccess(key: String) {
        val circuit = getCircuit(key)
        circuit.failures.set(0)
        circuit.cooldownMs.set(30000L) // Reset cooldown on success
        
        val prevState = circuit.state.getAndSet(State.CLOSED)
        if (prevState != State.CLOSED) {
            Timber.i("✅ [CIRCUIT] $key recovered. Circuit CLOSED.")
        }
    }

    override fun recordFailure(key: String, throwable: Throwable?) {
        val circuit = getCircuit(key)
        val lastState = circuit.state.get()
        
        val isCritical = isCriticalFailure(throwable)
        
        if (isCritical) {
            circuit.failures.set(failureThreshold)
            circuit.lastFailureTime.set(System.currentTimeMillis())
            // Apply exponential backoff immediately on critical failures
            val prevCooldown = circuit.cooldownMs.get()
            val newCooldown = (prevCooldown * 2).coerceAtMost(300000L) // limit to 5 minutes
            circuit.cooldownMs.set(newCooldown)
            
            if (circuit.state.getAndSet(State.OPEN) != State.OPEN) {
                Timber.e("💀 [CIRCUIT] $key FAST-TRIPPED to OPEN due to critical error: ${throwable?.javaClass?.simpleName} ($newCooldown ms cooldown)")
            }
            return
        }

        val currentFailures = circuit.failures.incrementAndGet()
        circuit.lastFailureTime.set(System.currentTimeMillis())

        if (lastState == State.HALF_OPEN) {
            // Probe failed — double the cooldown (exponential backoff) and return to OPEN
            val prevCooldown = circuit.cooldownMs.get()
            val newCooldown = (prevCooldown * 2).coerceAtMost(300000L) // 5 minutes max
            circuit.cooldownMs.set(newCooldown)
            
            if (circuit.state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                Timber.e("💀 [CIRCUIT] Probe failed. $key forced back to OPEN. Cooldown increased to $newCooldown ms.")
            }
<<<<<<< Updated upstream
        } else if (currentState == State.CLOSED) {
            if (currentFailures >= maxFailures) {
                // Threshold tercapai, buka sirkuit
=======
        } else if (lastState == State.CLOSED) {
            if (currentFailures >= failureThreshold) {
                // Failure threshold breached — open the circuit
>>>>>>> Stashed changes
                if (circuit.state.compareAndSet(State.CLOSED, State.OPEN)) {
                    Timber.e("💀 [CIRCUIT] $key OPENED due to $currentFailures failures.")
                }
            } else {
                Timber.d("⚠️ [CIRCUIT] $key recorded failure ($currentFailures/$maxFailures)")
            }
        }
    }

    override suspend fun <T> execute(key: String, block: suspend () -> T): T {
        if (!allowRequest(key)) {
            throw CircuitBreakerOpenException("Circuit breaker is OPEN for: $key")
        }

        return try {
            val result = block()
            recordSuccess(key)
            result
        } catch (e: CancellationException) {
            // CRITICAL: Lifecycle/UI cancellation — NOT a component failure.
            Timber.d("🔄 [CIRCUIT] Execution for $key cancelled by system/user.")
            throw e
        } catch (e: Exception) {
            // Real failure (Timeout, NullPointer, DeadObject, etc.)
            recordFailure(key, e)
            throw e
        }
    }
}
