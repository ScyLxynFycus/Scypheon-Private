package com.scypheon.sdk.core.memory

import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import com.scypheon.sdk.core.engine.InitializationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Enterprise Sandbox Vector Engine.
 * Implements IVectorEngine by delegating to the isolated :sandbox process.
 * This allows using GGUF models for RAG in a memory-efficient isolated context.
 */
class SandboxVectorEngine(
    private val sandboxLlamaEngine: SandboxLlamaEngine
) : IVectorEngine {

    private val _state = MutableStateFlow<IVectorEngine.EngineState>(IVectorEngine.EngineState.Idle)
    override val state = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        // Observe sandbox health and status
        scope.launch {
            sandboxLlamaEngine.initializationState.collect { initStatus ->
                when (initStatus) {
                    is InitializationState.Success -> _state.value = IVectorEngine.EngineState.Ready
                    is InitializationState.Failed -> _state.value = IVectorEngine.EngineState.Failed
                    is InitializationState.Loading, is InitializationState.Analyzing -> _state.value = IVectorEngine.EngineState.Initializing
                    else -> _state.value = IVectorEngine.EngineState.Idle
                }
            }
        }
    }

    override suspend fun initialize(modelPath: String?) {
        if (modelPath == null) {
            //  [HOTSWAP] Piggyback mode: Use the already-loaded Universal model for embeddings.
            // No separate model load needed  just mark as ready when sandbox is alive.
            Timber.i(" [EMBED] Piggyback mode: Awaiting sandbox readiness for embedded embeddings.")
            if (sandboxLlamaEngine.isReady()) {
                _state.value = IVectorEngine.EngineState.Ready
            } else {
                _state.value = IVectorEngine.EngineState.Initializing
            }
            return
        }
        
        // nCtx for embedding models is usually small (e.g. 512 or 2048)
        sandboxLlamaEngine.initialize(modelPath, 2048)
    }

    override suspend fun embedText(text: String): FloatArray? {
        // [PHOENIX-RESILIENCE] Wait for engine readiness with a 15s timeout
        // This handles transient 'Not Ready' states during LLM 'Lazarus' restarts.
        var retryCount = 0
        while (_state.value != IVectorEngine.EngineState.Ready && retryCount < 30) {
            if (retryCount % 10 == 0) {
                Timber.w(" [EMBED] Awaiting engine readiness (State: ${_state.value})...")
            }
            kotlinx.coroutines.delay(500)
            retryCount++
        }

        if (_state.value != IVectorEngine.EngineState.Ready) {
            Timber.e(" [EMBED] SandboxVectorEngine still not ready after 15s timeout.")
            return null
        }
        
        return sandboxLlamaEngine.getEmbeddings(text)
    }

    override fun close() {
        sandboxLlamaEngine.release()
        _state.value = IVectorEngine.EngineState.Idle
    }
}
