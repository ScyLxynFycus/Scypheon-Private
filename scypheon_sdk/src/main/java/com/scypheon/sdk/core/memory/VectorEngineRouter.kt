package com.scypheon.sdk.core.memory

import android.content.Context
import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Vector Engine Router.
 * Implements the "Exclusive Memory" policy (AGENTS.md Section 3).
 * Swaps between LiteRT and Sandbox engines based on the active model LLM.
 */
@Singleton
class VectorEngineRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liteRtEngine: LiteRtVectorEngine,
    private val sandboxLlamaEngine: SandboxLlamaEngine
) : IVectorEngine {

    private val sandboxEngine = SandboxVectorEngine(sandboxLlamaEngine)
    private var activeEngine: IVectorEngine = liteRtEngine

    private val _state = MutableStateFlow<IVectorEngine.EngineState>(IVectorEngine.EngineState.Idle)
    override val state = _state.asStateFlow()

    init {
        // Default to LiteRT if nothing else is active
        activeEngine = liteRtEngine
    }

    /**
     * Strategic Swap: Unloads the current engine and loads the target.
     * Prevents OOM by ensuring only one vector backend is in RAM.
     */
    suspend fun switchToLlamaEmbedder(modelPath: String?) {
        Timber.i("[ROUTER] Switching to GGUF (Sandbox) Embedder.")
        liteRtEngine.close()
        activeEngine = sandboxEngine
        sandboxEngine.initialize(modelPath)
    }

    suspend fun switchToLiteRtEmbedder(modelPath: String?) {
        Timber.i("[ROUTER] Switching to LiteRT (TFLite) Embedder.")
        // Do NOT call sandboxEngine.close() here  it would release the shared Llama model
        // used by the main chat engine (SandboxLlamaEngine is a singleton).
        activeEngine = liteRtEngine
        liteRtEngine.initialize(modelPath)
    }

    override suspend fun initialize(modelPath: String?) {
        activeEngine.initialize(modelPath)
    }

    override suspend fun embedText(text: String): FloatArray? {
        return activeEngine.embedText(text)
    }

    override fun close() {
        liteRtEngine.close()
        sandboxEngine.close()
    }
}
