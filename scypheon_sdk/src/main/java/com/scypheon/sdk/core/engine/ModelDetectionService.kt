package com.scypheon.sdk.core.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ModelDetectionService — the bridge between the on-disk model scan and the
 * running inference engines.
 *
 * Responsibilities:
 *  1. [getAllModels]   — performs the dual-engine filesystem scan via [ModelRegistry].
 *  2. [getLoadedModel] — tracks which model is currently active in the inference layer.
 *  3. [notifyModelLoaded] / [notifyModelUnloaded] — called by the engine switch logic
 *     inside [ModelSelectionViewModel] to keep this state consistent.
 *
 * Threading: [getAllModels] is a suspend function and must be called on [Dispatchers.IO].
 */
@Singleton
class ModelDetectionService @Inject constructor(
    private val registry: ModelRegistry
) {
    // The currently active model — updated via notifyModelLoaded() after a successful engine load.
    private val _loadedModel = MutableStateFlow<DetectedModel?>(null)
    val loadedModelFlow: StateFlow<DetectedModel?> = _loadedModel.asStateFlow()

    /**
     * Returns all model files physically present on the device.
     * Suspends on [Dispatchers.IO] — safe to call from a ViewModel coroutine.
     */
    suspend fun getAllModels(): List<DetectedModel> = withContext(Dispatchers.IO) {
        val candidates = registry.getAvailableModels()
        candidates.map { candidate ->
            val engine = if (candidate.arch == "litert" || candidate.path.endsWith(".task")) EngineType.LITE_RT else EngineType.LLAMA_CPP
            DetectedModel(
                id = candidate.name,
                displayName = candidate.name,
                engine = engine,
                sizeMb = candidate.sizeMb,
                filePath = candidate.path
            )
        }
    }

    /**
     * Returns the model that is currently loaded in the inference engine, or null
     * if no model has been activated since the last app cold-start.
     */
    fun getLoadedModel(): DetectedModel? = _loadedModel.value

    /**
     * Call this after the engine has successfully initialized a new model.
     * Updates [loadedModelFlow] so all observers react immediately.
     */
    fun notifyModelLoaded(model: DetectedModel) {
        _loadedModel.value = model
        Timber.i("ModelDetectionService: active model → '${model.displayName}' (${model.engine.name})")
    }

    /**
     * Call this when the engine is shut down or a model load fails.
     */
    fun notifyModelUnloaded() {
        _loadedModel.value = null
        Timber.i("ModelDetectionService: active model → null (unloaded)")
    }

    /**
     * Checks whether a [DetectedModel] (possibly from persisted preferences)
     * is still physically present on the device. Asset-backed models are always valid.
     */
    fun isModelStillPresent(model: DetectedModel): Boolean {
        if (model.filePath.startsWith("assets://")) return true
        return File(model.filePath).exists()
    }
}
