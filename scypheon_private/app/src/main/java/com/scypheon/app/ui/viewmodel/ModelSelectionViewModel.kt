package com.scypheon.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scypheon.app.startup.DatabaseReadySignal
import com.scypheon.sdk.core.engine.DetectedModel
import com.scypheon.sdk.core.engine.EngineType
import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import com.scypheon.sdk.core.engine.ModelDetectionService
import com.scypheon.sdk.core.engine.ModelPreferences
import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for the model picker widget.
 *
 * @param selectedModel    The currently chosen model (null = no model selected)
 * @param isLoading        True while the engine is initializing the new model
 * @param availableModels  All models detected on the device (both engines)
 * @param showPicker       Controls the modal sheet visibility
 */
data class ModelSelectionUiState(
    val selectedModel: DetectedModel? = null,
    val isLoading: Boolean = false,
    val availableModels: List<DetectedModel> = emptyList(),
    val showPicker: Boolean = false
)

/**
 * ModelSelectionViewModel — manages the full model lifecycle from picker to engine.
 *
 * On init:
 *  1. Waits for [DatabaseReadySignal] (consistent with Sprint 3 IO discipline).
 *  2. Scans device for available models via [ModelDetectionService].
 *  3. Restores persisted selection from [ModelPreferences] and validates it.
 *
 * On selection:
 *  1. Sets isLoading = true so UI shows a progress indicator.
 *  2. Releases current engine resources.
 *  3. Initializes the correct engine (LiteRT or SandboxLlama) for the chosen model.
 *  4. Notifies [ModelDetectionService] so other observers know the active model.
 *  5. Persists the selection via [ModelPreferences].
 *
 * All IO and engine calls run on [Dispatchers.IO] — the main thread is never blocked.
 */
@HiltViewModel
class ModelSelectionViewModel @Inject constructor(
    private val detectionService: ModelDetectionService,
    private val modelPrefs: ModelPreferences,
    private val liteRtEngine: LiteRtEliteEngine,
    private val sandboxEngine: SandboxLlamaEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelSelectionUiState())
    val uiState: StateFlow<ModelSelectionUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadInitialState() {
        viewModelScope.launch(Dispatchers.IO) {
            // Gate: wait for DB + filesystem to be ready (same as MainViewModel)
            DatabaseReadySignal.awaitReady()

            val available = detectionService.getAllModels()
            val persisted = modelPrefs.getSelectedModel()

            // Validate: persisted model must still exist on disk
            val validated = persisted?.takeIf { saved ->
                available.any { it.id == saved.id } && detectionService.isModelStillPresent(saved)
            }

            if (persisted != null && validated == null) {
                Timber.w("ModelSelectionViewModel: persisted model '${persisted.displayName}' no longer on device — clearing")
                modelPrefs.clearSelection()
            }

            _uiState.value = ModelSelectionUiState(
                selectedModel    = validated,
                isLoading        = false,
                availableModels  = available,
                showPicker       = false
            )

            Timber.d(
                "ModelSelectionViewModel: ${available.size} model(s) detected " +
                "(${available.count { it.engine == EngineType.LLAMA_CPP }} GGUF, " +
                "${available.count { it.engine == EngineType.LITE_RT }} LiteRT), " +
                "restored='${validated?.displayName ?: "none"}'"
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User actions
    // ─────────────────────────────────────────────────────────────────────────

    fun showPicker() {
        _uiState.value = _uiState.value.copy(showPicker = true)
    }

    fun dismissPicker() {
        _uiState.value = _uiState.value.copy(showPicker = false)
    }

    /**
     * User selected a model. Performs the full engine swap on [Dispatchers.IO]:
     *  - Releases the previous engine
     *  - Initializes the new engine with the selected model file
     *  - Notifies [ModelDetectionService] and persists the selection
     */
    fun selectModel(model: DetectedModel) {
        val current = _uiState.value.selectedModel
        if (current?.id == model.id) {
            // Tapped the already-active model — just close the picker
            dismissPicker()
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading  = true,
            showPicker = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Release whichever engine is currently running
            releaseCurrentEngine(current)

            // 2. Initialize the new engine
            val nCtx = 4096 // Reasonable default; MemoryBudgetCalculator could refine this
            val success = when (model.engine) {
                EngineType.LITE_RT -> liteRtEngine.initialize(model.filePath, nCtx)

                EngineType.LLAMA_CPP -> {
                    // Explicit triple-triage: Vulkan → OpenCL → CPU
                    // Mode constants from SandboxLlamaEngine:
                    //   2 = FORCE_VULKAN, 3 = FORCE_OPENCL, 1 = FORCE_CPU
                    triageLoad(model.filePath, nCtx)
                }
            }

            if (success) {
                // 3. Persist + notify
                modelPrefs.saveSelectedModel(model)
                detectionService.notifyModelLoaded(model)

                _uiState.value = _uiState.value.copy(
                    selectedModel = model,
                    isLoading     = false
                )
                Timber.i("ModelSelectionViewModel: ✅ '${model.displayName}' (${model.engine.name}) loaded successfully")
            } else {
                // Engine init failed — keep previous selection, stop spinner
                detectionService.notifyModelUnloaded()
                _uiState.value = _uiState.value.copy(isLoading = false)
                Timber.e("ModelSelectionViewModel: ❌ Failed to load '${model.displayName}'")
            }
        }
    }

    fun clearSelection() {
        viewModelScope.launch(Dispatchers.IO) {
            releaseCurrentEngine(_uiState.value.selectedModel)
            modelPrefs.clearSelection()
            detectionService.notifyModelUnloaded()
            _uiState.value = _uiState.value.copy(
                selectedModel = null,
                isLoading     = false
            )
        }
    }

    /** Force-refresh model list (e.g. after a model download). */
    fun refresh() {
        loadInitialState()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Engine lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private fun releaseCurrentEngine(current: DetectedModel?) {
        if (current == null) return
        try {
            when (current.engine) {
                EngineType.LITE_RT   -> liteRtEngine.release()
                EngineType.LLAMA_CPP -> sandboxEngine.release()
            }
            Timber.d("ModelSelectionViewModel: released ${current.engine.name} engine")
        } catch (e: Exception) {
            Timber.e(e, "ModelSelectionViewModel: error releasing ${current.engine.name} engine")
        }
    }

    /**
     * Explicit Vulkan → OpenCL → CPU triage for LLaMA.cpp models.
     *
     * Attempts each backend in priority order and stops at the first success.
     * This gives the application full observability and control over the fallback
     * chain — more reliable than delegating to the native bridge's AUTO mode.
     *
     * Backend mode constants (from SandboxLlamaEngine):
     *   2 = FORCE_VULKAN   — fastest, requires Vulkan-capable GPU
     *   3 = FORCE_OPENCL   — fallback for Mali/Kirin/OpenCL devices
     *   1 = FORCE_CPU      — universal last resort (mmap, slowest but always works)
     */
    private suspend fun triageLoad(modelPath: String, nCtx: Int): Boolean {
        // Stage 1: Vulkan (GPU hardware acceleration)
        Timber.i("ModelSelectionViewModel: [TRIAGE 1/3] Attempting Vulkan backend...")
        if (sandboxEngine.loadWithMode(modelPath, mode = 2, nCtx = nCtx)) {
            Timber.i("ModelSelectionViewModel: [TRIAGE 1/3] ✅ Vulkan succeeded")
            return true
        }
        Timber.w("ModelSelectionViewModel: [TRIAGE 1/3] ❌ Vulkan failed — trying OpenCL")

        // Stage 2: OpenCL (Mali/Kirin/Exynos GPU fallback)
        Timber.i("ModelSelectionViewModel: [TRIAGE 2/3] Attempting OpenCL backend...")
        if (sandboxEngine.loadWithMode(modelPath, mode = 3, nCtx = nCtx)) {
            Timber.i("ModelSelectionViewModel: [TRIAGE 2/3] ✅ OpenCL succeeded")
            return true
        }
        Timber.w("ModelSelectionViewModel: [TRIAGE 2/3] ❌ OpenCL failed — falling back to CPU")

        // Stage 3: CPU (mmap, universal — always supported)
        Timber.i("ModelSelectionViewModel: [TRIAGE 3/3] Attempting CPU-only backend...")
        val cpuSuccess = sandboxEngine.loadWithMode(modelPath, mode = 1, nCtx = nCtx)
        if (cpuSuccess) {
            Timber.i("ModelSelectionViewModel: [TRIAGE 3/3] ✅ CPU fallback succeeded")
        } else {
            Timber.e("ModelSelectionViewModel: [TRIAGE 3/3] ❌ All backends failed — model cannot be loaded")
        }
        return cpuSuccess
    }
}
