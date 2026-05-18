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

@SafetyCritical
@Singleton
class DefaultResilienceCircuitBreaker @Inject constructor() : ResilienceCircuitBreaker {

    private val circuits = ConcurrentHashMap<String, CircuitState>()
    private val failureThreshold = 5
    private val recoveryTimeoutMs = 30000L // 30 seconds

    // Menggunakan Atomics untuk thread-safety tanpa memblokir (Lock-Free)
    private class CircuitState {
        val state = AtomicReference(State.CLOSED)
        val failures = AtomicInteger(0)
        val lastFailureTime = AtomicLong(0L)
    }

    private fun getCircuit(key: String): CircuitState {
        return circuits.computeIfAbsent(key) { CircuitState() }
    }

    override fun allowRequest(key: String): Boolean {
        val circuit = getCircuit(key)
        val currentState = circuit.state.get()

        if (currentState == State.CLOSED) return true

        if (currentState == State.OPEN) {
            val timeSinceFailure = System.currentTimeMillis() - circuit.lastFailureTime.get()
            if (timeSinceFailure > recoveryTimeoutMs) {
                // HALF_OPEN Probe: Gunakan compareAndSet agar hanya SATU thread yang lolos menjadi probe
                if (circuit.state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    Timber.d("🛡️ [CIRCUIT] $key shifted to HALF_OPEN (Probe dispatched)")
                    return true
                }
                // Jika kalah race condition, berarti thread lain sudah menjadi probe. Tolak yang ini.
                return false
            }
            Timber.w("🚨 [CIRCUIT] Request blocked. $key is OPEN.")
            return false
        }

        // Jika statusnya HALF_OPEN, tolak request lain sampai probe pertama selesai (sukses/gagal)
        return false
    }

    override fun recordSuccess(key: String) {
        val circuit = getCircuit(key)
        circuit.failures.set(0)
        
        val prevState = circuit.state.getAndSet(State.CLOSED)
        if (prevState != State.CLOSED) {
            Timber.i("✅ [CIRCUIT] $key recovered. Circuit CLOSED.")
        }
    }

    override fun recordFailure(key: String) {
        val circuit = getCircuit(key)
        val currentFailures = circuit.failures.incrementAndGet()
        circuit.lastFailureTime.set(System.currentTimeMillis())

        val currentState = circuit.state.get()

        if (currentState == State.HALF_OPEN) {
            // Probe gagal, langsung kembalikan ke OPEN
            if (circuit.state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                Timber.e("💀 [CIRCUIT] Probe failed. $key forced back to OPEN.")
            }
        } else if (currentState == State.CLOSED) {
            if (currentFailures >= failureThreshold) {
                // Threshold tercapai, buka sirkuit
                if (circuit.state.compareAndSet(State.CLOSED, State.OPEN)) {
                    Timber.e("💀 [CIRCUIT] $key OPENED due to $currentFailures failures.")
                }
            } else {
                Timber.d("⚠️ [CIRCUIT] $key recorded failure ($currentFailures/$failureThreshold)")
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
            // 🛑 CRITICAL: Lifecycle UI membatalkan request. INI BUKAN FAILURE!
            Timber.d("🔄 [CIRCUIT] Execution for $key cancelled by system/user.")
            throw e
        } catch (e: Exception) {
            // Ini adalah kegagalan nyata (Timeout, NullPointer, DeadObject, dll)
            recordFailure(key)
            throw e
        }
    }
}
