package com.scypheon.sdk.core.memory

import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import com.scypheon.sdk.core.engine.InitializationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enterprise Sandbox Vector Engine — Production-Hardened.
 * Implements [IVectorEngine] by delegating to the isolated :ai_sandbox process.
 *
 * Architecture:
 * - Zero busy-wait: Uses Flow-based reactive state synchronization.
 * - Circuit Breaker: Trips after [MAX_CONSECUTIVE_FAILURES] to prevent cascading failures.
 * - Structured Concurrency: All coroutines are bound to a cancellable [SupervisorJob].
 * - Lifecycle-aware: [close] cancels all internal coroutines and resets state.
 *
 * [v1.6.1-SAR] Rewritten to eliminate zombie polling, coroutine starvation,
 * and race conditions during sandbox crash/recovery cycles.
 */
class SandboxVectorEngine(
    private val sandboxLlamaEngine: SandboxLlamaEngine
) : IVectorEngine {

    companion object {
        /** Maximum time (ms) to wait for sandbox readiness before returning null. */
        private const val READINESS_TIMEOUT_MS = 15_000L

        /** After this many consecutive embed failures, the circuit breaker opens. */
        private const val MAX_CONSECUTIVE_FAILURES = 5

        /** After circuit trips, cooldown before allowing a retry probe. */
        private const val CIRCUIT_COOLDOWN_MS = 30_000L

        private const val TAG = "SandboxVectorEngine"
    }

    private val _state = MutableStateFlow<IVectorEngine.EngineState>(IVectorEngine.EngineState.Idle)
    override val state = _state.asStateFlow()

    // Structured concurrency: SupervisorJob so child failures don't kill the scope.
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    // Circuit Breaker state
    private val consecutiveFailures = AtomicInteger(0)
    @Volatile private var circuitOpenUntil: Long = 0L
    private val isCircuitOpen: Boolean
        get() = System.currentTimeMillis() < circuitOpenUntil

    init {
        // Reactive state synchronization: Maps sandbox InitializationState → IVectorEngine.EngineState.
        // No polling. No busy-wait. Pure Flow collection.
        scope.launch {
            sandboxLlamaEngine.initializationState.collect { initStatus ->
                val newState = when (initStatus) {
                    is InitializationState.Success -> IVectorEngine.EngineState.Ready
                    is InitializationState.Failed -> IVectorEngine.EngineState.Failed
                    is InitializationState.Loading,
                    is InitializationState.Analyzing,
                    is InitializationState.Attaching -> IVectorEngine.EngineState.Initializing
                    else -> IVectorEngine.EngineState.Idle
                }
                if (_state.value != newState) {
                    Timber.d("[$TAG] State transition: ${_state.value} → $newState")
                    _state.value = newState

                    // Auto-reset circuit breaker on successful recovery
                    if (newState == IVectorEngine.EngineState.Ready) {
                        resetCircuitBreaker()
                    }
                }
            }
        }

        // Secondary health monitor: Catches edge cases where initializationState
        // emission is missed (e.g., sandbox was already loaded before this engine was created).
        scope.launch {
            sandboxLlamaEngine.processHealth.collect { isAlive ->
                if (!isAlive && _state.value == IVectorEngine.EngineState.Ready) {
                    Timber.w("[$TAG] Sandbox process died. Transitioning to Failed.")
                    _state.value = IVectorEngine.EngineState.Failed
                }
            }
        }
    }

    override suspend fun initialize(modelPath: String?) {
        if (modelPath == null) {
            // Piggyback mode: Use the already-loaded Universal model for embeddings.
            // No separate model load needed — just sync state with sandbox.
            Timber.i("[$TAG] Piggyback mode: Awaiting sandbox readiness.")
            if (sandboxLlamaEngine.isReady()) {
                _state.value = IVectorEngine.EngineState.Ready
                resetCircuitBreaker()
            } else {
                _state.value = IVectorEngine.EngineState.Initializing
                // State will automatically transition to Ready via the Flow collector
                // when sandboxLlamaEngine.initializationState emits Success.
            }
            return
        }

        // Dedicated embedding model load
        _state.value = IVectorEngine.EngineState.Initializing
        val success = sandboxLlamaEngine.initialize(modelPath, 2048)
        if (success) {
            _state.value = IVectorEngine.EngineState.Ready
            resetCircuitBreaker()
        } else {
            _state.value = IVectorEngine.EngineState.Failed
        }
    }

    override suspend fun embedText(text: String): FloatArray? {
        // Circuit Breaker: Fast-fail if too many consecutive errors
        if (isCircuitOpen) {
            Timber.w("[$TAG] Circuit breaker OPEN. Rejecting embed request. Cooldown remaining: ${circuitOpenUntil - System.currentTimeMillis()}ms")
            return null
        }

        // Efficient readiness gate: Suspend (not poll!) until Ready or timeout.
        if (_state.value != IVectorEngine.EngineState.Ready) {
            // Quick check: Maybe sandbox is ready but our state hasn't caught up
            if (sandboxLlamaEngine.isReady()) {
                _state.value = IVectorEngine.EngineState.Ready
            } else {
                // Suspend efficiently until the state Flow emits Ready, or timeout.
                val becameReady = withTimeoutOrNull(READINESS_TIMEOUT_MS) {
                    _state.first { it == IVectorEngine.EngineState.Ready }
                }
                if (becameReady == null) {
                    // One more direct check before giving up (handles race conditions)
                    if (sandboxLlamaEngine.isReady()) {
                        _state.value = IVectorEngine.EngineState.Ready
                    } else {
                        Timber.e("[$TAG] Readiness timeout (${READINESS_TIMEOUT_MS}ms). State: ${_state.value}")
                        recordFailure()
                        return null
                    }
                }
            }
        }

        // Execute embedding via sandbox IPC
        return try {
            val result = sandboxLlamaEngine.getEmbeddings(text)
            if (result != null && result.isNotEmpty()) {
                consecutiveFailures.set(0) // Reset on success
                result
            } else {
                Timber.w("[$TAG] getEmbeddings returned null/empty for text (${text.length} chars)")
                recordFailure()
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] getEmbeddings threw during IPC call")
            recordFailure()
            null
        }
    }

    /**
     * Records a failure and trips the circuit breaker if threshold is exceeded.
     */
    private fun recordFailure() {
        val count = consecutiveFailures.incrementAndGet()
        if (count >= MAX_CONSECUTIVE_FAILURES) {
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS
            Timber.e("[$TAG] Circuit breaker TRIPPED after $count consecutive failures. Cooldown: ${CIRCUIT_COOLDOWN_MS}ms")
        }
    }

    /**
     * Resets the circuit breaker after a successful operation or recovery.
     */
    private fun resetCircuitBreaker() {
        if (consecutiveFailures.getAndSet(0) > 0 || isCircuitOpen) {
            circuitOpenUntil = 0L
            Timber.i("[$TAG] Circuit breaker RESET. Engine healthy.")
        }
    }

    override fun close() {
        Timber.i("[$TAG] Closing. Cancelling all internal coroutines.")
        job.cancel()
        _state.value = IVectorEngine.EngineState.Idle
        consecutiveFailures.set(0)
        circuitOpenUntil = 0L
    }
}
