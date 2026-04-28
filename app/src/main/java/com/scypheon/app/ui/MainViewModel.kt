package com.scypheon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scypheon.app.data.repository.ScypheonRepository
import com.scypheon.sdk.core.humanitarian.education.LiveEnglishTutor
import com.scypheon.sdk.core.humanitarian.psychology.ReminiscenceCompanion
import com.scypheon.sdk.core.humanitarian.accessibility.DeafEnvironmentGuardian
import com.scypheon.sdk.core.memory.ContextSummarizer
import com.scypheon.sdk.core.engine.InitializationState
import com.scypheon.sdk.core.telemetry.AuditLogEntry
import android.net.Uri
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.humanitarian.accessibility.VisualGuide
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.memory.GraphMemoryManager
import com.scypheon.sdk.core.security.AegisPrivacyShield
import com.scypheon.app.data.models.RawGraphEdge
import com.scypheon.sdk.core.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OutOfQuotaPolicy
import com.scypheon.app.workers.VitreusFlowWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.scypheon.sdk.core.humanitarian.accessibility.GestureGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.KineticGuardian
import com.scypheon.sdk.core.model.ScypheonConfig
import com.scypheon.sdk.core.model.ScypheonBackendDiagnostic
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.withContext

data class ChatMessageUiState(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false,
    val hardwareStatus: String? = null,
    val status: Int = 0, // STATUS_SUCCESS
    val isContextEligible: Boolean = true,
    val id: String = java.util.UUID.randomUUID().toString()
)

data class UiState(
    val isReady: Boolean = false,
    val messages: List<ChatMessageUiState> = emptyList(),
    val currentSessionId: String = "",
    val error: String? = null,
    val activeFeature: String? = null,
    val telemetryLogs: List<AuditLogEntry> = emptyList(),
    val isTelemetryDashboardVisible: Boolean = false,
    val isGraphExplorerVisible: Boolean = false,
    val graphData: List<RawGraphEdge> = emptyList(),
    val sessionHistory: List<com.scypheon.sdk.core.memory.DualMemoryManager.Session> = emptyList(),
    
    // Identity
    val userName: String = "",
    
    // Model Hub State
    val isModelHubVisible: Boolean = false,
    val activeModelName: String = "no models selected",
    val activeEngineType: String? = null,
    val hfToken: String = "",
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    
    // Live Mode State
    val isLiveModeActive: Boolean = false,
    
    // Waveform Animation Phase
    val voiceAmplitude: Float = 0f,
    
    // System Health / Diagnostics
    val systemHealth: ScypheonRepository.SystemHealth? = null,
    val isSystemHealthVisible: Boolean = false,
    val systemWarning: String? = null,
    
    // Scypheon Pro Configurations
    val config: ScypheonConfig = ScypheonConfig(),
    val isConfigVisible: Boolean = false,
    val isAiGenerating: Boolean = false,
    val engineState: InitializationState = InitializationState.Idle,
    val ragState: com.scypheon.sdk.core.memory.IVectorEngine.EngineState = com.scypheon.sdk.core.memory.IVectorEngine.EngineState.Initializing,
    val diagnosticLogs: List<String> = emptyList(),
    val isSandboxAlive: Boolean = true,
    val isMemoryOptimized: Boolean = false,
    val isNotificationSuppressed: Boolean = false,
    val memoryStabilityState: MemoryStabilityState = MemoryStabilityState.IDLE,
    val memoryWarningCooldown: Int = 0,
    val oomDiagnostic: ScypheonRepository.OomDiagnostic? = null
)

enum class MemoryStabilityState {
    IDLE,
    WARNING_COOLDOWN,
    READY_TO_FORCE,
    CRASHED
}

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: ScypheonRepository,
    private val liveEnglishTutor: LiveEnglishTutor,
    private val reminiscenceCompanion: ReminiscenceCompanion,
    private val deafEnvironmentGuardian: DeafEnvironmentGuardian,
    private val gestureGuardian: GestureGuardian,
    private val kineticGuardian: KineticGuardian,
    private val blackBoxVault: BlackBoxVault,
    private val contextSummarizer: ContextSummarizer,
    private val dualMemoryManager: DualMemoryManager,
    private val graphMemoryManager: GraphMemoryManager,
    private val modelProvisioner: com.scypheon.sdk.core.provision.ModelProvisioner,
    private val vault: com.scypheon.sdk.core.security.AegisVault,
    private val sensoryHooks: com.scypheon.sdk.core.gateway.SensoryHooks
) : AndroidViewModel(application) {

    private val voiceEngine = com.scypheon.sdk.core.voice.AegisVoiceEngine(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pendingModelFile: java.io.File? = null

    private val promptQueue = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Uri?>>(replay = 0)
    private var inferenceJob: kotlinx.coroutines.Job? = null

    // --- POCKET AGENTS REGISTRY ---
    private val agents = mapOf<String, com.scypheon.sdk.core.humanitarian.ScypheonAgent>(
        "LiveEnglishTutor" to liveEnglishTutor,
        "ReminiscenceCompanion" to reminiscenceCompanion,
        "DeafEnvironmentGuardian" to deafEnvironmentGuardian,
        "GestureGuardian" to gestureGuardian,
        "KineticGuardian" to kineticGuardian
    )

    fun performFullSensoryAudit(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isAiGenerating = true) }
            try {
                val auditResult = sensoryHooks.performMultiModalAudit(uri)
                
                // Add the result as a system message or hidden context for the next LLM turn
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + ChatMessageUiState(
                            text = auditResult,
                            isUser = false,
                            status = 0
                        ),
                        isAiGenerating = false
                    )
                }
                
                Timber.i("✅ [UI] Sensory Audit completed and wired to chat.")
            } catch (e: Exception) {
                Timber.e(e, "Sensory audit failed")
                _uiState.update { it.copy(isAiGenerating = false, error = "Audit gagal: ${e.message}") }
            }
        }
    }

    init {
        // [PERFORMANCE] Offload startup configuration to prevent Main-thread ANR.
        viewModelScope.launch(Dispatchers.IO) {
            val hfToken = vault.getHfToken() ?: ""
            val name = vault.getUserName() ?: ""
            val cfg = vault.loadConfig()
            
            _uiState.update { 
                it.copy(
                    hfToken = hfToken,
                    userName = name,
                    config = cfg
                ) 
            }
            
            // [MEMORY GUARD] Concurrent purge of corrupted engine error messages.
            try {
                dualMemoryManager.purgeEngineErrorMessages()
            } catch (e: Exception) {
                Timber.e(e, "Fatal database purge failure during startup.")
            }
        }

        observeLiveEvents()
        observeEngineState()
        observeRagState()
        observeProcessHealth()
        observeMemoryOptimization()
        observePromptQueue()
        observeOomDiagnostic()
    }

    private fun observeProcessHealth() {
        viewModelScope.launch {
            repository.processHealth.collect { isAlive ->
                if (!isAlive) {
                    val logEntry = "🛡️ ALERT: Sandbox Process CRASHED during inference."
                    _uiState.update { s -> 
                        s.copy(
                            isSandboxAlive = false,
                            isAiGenerating = false,
                            error = "Fatal Error: Your device ran out of memory. The system has automatically isolated the failure to protect your device.",
                            memoryStabilityState = MemoryStabilityState.CRASHED,
                            diagnosticLogs = s.diagnosticLogs + logEntry
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSandboxAlive = true) }
                }
            }
        }
    }

    private fun observeEngineState() {
        viewModelScope.launch {
            repository.engineState.collect { state ->
                val logEntry = when(state) {
                    is InitializationState.Analyzing -> "MDRS: ${state.step}"
                    is InitializationState.Trying -> "Triage: Attempting ${state.backend} (v${state.attempt})"
                    is InitializationState.Failed -> "Alert: ${state.backend} failed - ${state.error}"
                    is InitializationState.Success -> "Harmony: Engine locked on ${state.hardware}"
                    else -> null
                }
                
                _uiState.update { s -> 
                    s.copy(
                        engineState = state,
                        isReady = if (state is InitializationState.Success) true else s.isReady,
                        error = when(state) {
                            is InitializationState.Success -> null
                            is InitializationState.Failed -> state.error
                            else -> s.error
                        },
                        diagnosticLogs = if (logEntry != null) s.diagnosticLogs + logEntry else s.diagnosticLogs
                    )
                }
            }
        }
    }

    private fun observeOomDiagnostic() {
        viewModelScope.launch {
            repository.oomDiagnostic.collect { diagnostic ->
                _uiState.update { it.copy(oomDiagnostic = diagnostic) }
            }
        }
    }

    fun dismissOomDiagnostic() {
        repository.dismissOomDiagnostic()
    }

    private fun observeRagState() {
        viewModelScope.launch {
            repository.vectorEngineState.collectLatest { state ->
                _uiState.update { it.copy(ragState = state) }
            }
        }
    }

    private fun observePromptQueue() {
        viewModelScope.launch {
            // Wait until both engines are READY
            kotlinx.coroutines.flow.combine(
                repository.engineState,
                repository.vectorEngineState
            ) { llama, _ ->
                // [Hardening] Llama is the critical brain. RAG is an enhancement.
                // We no longer block the prompt queue if RAG is still initializing/syncing.
                llama is InitializationState.Success
            }.collectLatest { ready ->
                if (ready) {
                    promptQueue.collect { (text, imageUri) ->
                        executeInference(text, imageUri, addUserMessage = false)
                    }
                }
            }
        }
    }

    private fun observeMemoryOptimization() {
        viewModelScope.launch {
            repository.memoryOptimizationActive.collectLatest { active ->
                _uiState.update { it.copy(isMemoryOptimized = active) }
            }
        }
    }

    fun dismissMemoryOptimization() {
        _uiState.update { it.copy(isMemoryOptimized = false) }
    }

    fun checkNotificationStatus() {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager
        
        val isSuppressed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel("solaris_neural_core")
            val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            
            // Suppressed if app-level notifications are off OR the specific channel is deleted/disabled/silenced
            !appNotificationsEnabled || channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE
        } else {
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        if (_uiState.value.isNotificationSuppressed != isSuppressed) {
            Timber.w("🛡️ [PHOENIX] Notification Guard Status Change: suppressed=$isSuppressed")
            _uiState.update { it.copy(isNotificationSuppressed = isSuppressed) }
        }
    }

    fun saveUserName(name: String) {
        vault.saveUserName(name)
        _uiState.update { it.copy(userName = name) }
    }

    private fun observeLiveEvents() {
        viewModelScope.launch {
            GlobalLiveEventBus.events.collectLatest { eventMsg ->
                _uiState.update { state ->
                    val newMsg = ChatMessageUiState(
                        text = eventMsg,
                        isUser = false,
                        isLoading = false
                    )
                    state.copy(messages = state.messages + newMsg)
                }
            }
        }
    }

    fun initializeEngines() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isReady = false) }
            
            // 🛡️ TRIPWIRE: Version-based Automatic Reset (Tripwire 2.0)
            try {
                val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
                val currentVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    pInfo.versionCode.toLong()
                }
                
                if (vault.getLastHwCheckVersion() != currentVersion) {
                    Timber.i("🛡️ TRIPWIRE: App update detected version=$currentVersion. Performing clean slate hardware reset.")
                    resetHardwareOverrides()
                    vault.saveLastHwCheckVersion(currentVersion)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check package version for Tripwire reset.")
            }

            val health = repository.checkSystemHealth(getApplication())
            val currentConfig = vault.loadConfig()
            

            val result = repository.initializeEngines(getApplication(), nCtx = currentConfig.contextWindow)
            
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(systemHealth = health) }

                if (result is Result.Success) {
                    // Default model loaded statically or dynamically by repository
                    val registry = com.scypheon.sdk.core.utils.AssetExtractor.discoverModels(getApplication())
                    val activeModelName = registry.universalModel ?: "gemma-2-2b-it-Q6_K.gguf"

                    // Check for Memory Guard Veto or substitution warnings
                    val warning = repository.getPendingInitializationWarning()
                    if (warning?.contains("VETO") == true) {
                        _uiState.update { it.copy(
                            systemWarning = "Memory Guard Alert: The selected model is too large. Attempting to load the largest safe fallback model instead.",
                            activeModelName = "$activeModelName (Fallback Active)"
                        ) }
                    } else if (warning == "OPENCL_PIVOT_TRIGGERED") {
                        _uiState.update { it.copy(systemWarning = "Memory Optimization: Using OpenCL to reduce VRAM pressure.") }
                    }

                    if (result.data == true) {
                        val hwStatus = repository.getHardwareStatus()
                        _uiState.update { 
                            it.copy(
                                activeModelName = activeModelName,
                                activeEngineType = if (hwStatus.contains("NPU") || hwStatus.contains("LiteRT")) "LiteRT" else "Llama",
                                isReady = true
                            ) 
                        }
                        resumeLastSessionOrStandby()
                    }
                } else if (result is Result.Error) {
                    _uiState.update { it.copy(
                        error = "Neural Engine Failure: ${result.exception.message}",
                        isReady = true
                    ) }
                }
            }
        }
    }

    suspend fun promoteToForeground(): Boolean {
        return repository.promoteSandboxToForeground()
    }

    fun dismissSystemWarning() {
        pendingModelFile = null
        _uiState.update { it.copy(systemWarning = null) }
    }

    private fun resumeLastSessionOrStandby() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = dualMemoryManager.getAllSessions()
            if (sessions.isNotEmpty()) {
                val lastId = sessions[0].id
                loadSession(lastId)
            } else {
                _uiState.update { it.copy(sessionHistory = emptyList(), currentSessionId = "") }
            }
        }
    }

    fun loadSessionHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = dualMemoryManager.getAllSessions()
            _uiState.update { it.copy(sessionHistory = sessions) }
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbMessages = dualMemoryManager.getMessagesForSession(sessionId)
            val uiMessages = dbMessages.map { 
                ChatMessageUiState(
                    text = it.text, 
                    isUser = it.isUser,
                    status = it.status,
                    isContextEligible = it.isContextEligible
                ) 
            }
            _uiState.update {
                it.copy(
                    currentSessionId = sessionId,
                    messages = uiMessages
                )
            }
            loadSessionHistory()
        }
    }

    fun showLocalModelPicker() {
        scanLocalModels()
        _uiState.update { it.copy(config = it.config.copy(isLocalModelPickerVisible = true)) }
    }

    fun hideLocalModelPicker() {
        _uiState.update { it.copy(config = it.config.copy(isLocalModelPickerVisible = false)) }
    }

    private fun scanLocalModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val internalDir = java.io.File(getApplication<Application>().filesDir, "models")
            val externalDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            
            val models = mutableListOf<java.io.File>()
            
            fun addFromDir(dir: java.io.File?) {
                dir?.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.endsWith(".task") || file.name.endsWith(".gguf") || file.name.endsWith(".litertlm"))) {
                        models.add(file)
                    }
                }
            }
            
            addFromDir(internalDir)
            addFromDir(externalDir)
            
            _uiState.update { it.copy(config = it.config.copy(localModels = models.distinctBy { it.absolutePath })) }
        }
    }

    fun hotswapLocalModel(file: java.io.File) {
        val report = com.scypheon.sdk.core.utils.MemoryGatekeeper.performPreflightCheck(getApplication(), file.length())
        if (report.stressLevel >= 2) {
            pendingModelFile = file
            _uiState.update { 
                it.copy(
                    config = it.config.copy(isLocalModelPickerVisible = false),
                    systemWarning = "This model requires more RAM than is currently available. Proceeding may cause a system-wide crash. Do you wish to force load it?"
                ) 
            }
            return
        }
        executeModelSwap(file)
    }

    fun confirmModelLoad() {
        val file = pendingModelFile
        if (file != null) {
            pendingModelFile = null
            _uiState.update { it.copy(systemWarning = null) }
            executeModelSwap(file)
        }
    }

    private fun executeModelSwap(file: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isReady = false, config = it.config.copy(isLocalModelPickerVisible = false)) }
            
            repository.releaseEngines()
            
            val isLiteRT = file.name.endsWith(".task") || file.name.endsWith(".litertlm")
            val engineType = if (isLiteRT) "LiteRT" else "Llama"
            
            val result = if (isLiteRT) {
                repository.initializeEngines(getApplication(), customElitePath = file.absolutePath)
            } else {
                repository.initializeEngines(getApplication(), customUniversalPath = file.absolutePath)
            }
            
            withContext(Dispatchers.Main) {
                if (result is Result.Success) {
                    _uiState.update { 
                        it.copy(
                            activeModelName = file.name, 
                            activeEngineType = engineType,
                            isReady = true
                        ) 
                    }
                    resumeLastSessionOrStandby()
                } else {
                    _uiState.update { it.copy(error = "Failed to switch to ${file.name}", isReady = true) }
                }
            }
        }
    }

    fun showSystemHealth() {
        viewModelScope.launch {
            val health = repository.checkSystemHealth(getApplication())
            _uiState.update { it.copy(systemHealth = health, isSystemHealthVisible = true) }
        }
    }

    fun hideSystemHealth() {
        _uiState.update { it.copy(isSystemHealthVisible = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun startNewSession() {
        val sessionId = "session_${System.currentTimeMillis()}"
        viewModelScope.launch {
            repository.createNewSession(sessionId, "New Session")
            _uiState.update {
                it.copy(
                    isReady = true,
                    currentSessionId = sessionId,
                    // Fix fragile string matching: We don't inject a fake visible message into the list anymore,
                    // we rely on the `messages.isEmpty()` check in the UI to show the greeting.
                    messages = emptyList()
                )
            }
            loadSessionHistory()
        }
    }

    fun showTelemetryDashboard() {
        val logs = blackBoxVault.dumpLogs()
        _uiState.update {
            it.copy(
                isTelemetryDashboardVisible = true,
                telemetryLogs = logs
            )
        }
    }

    fun hideTelemetryDashboard() {
        _uiState.update { it.copy(isTelemetryDashboardVisible = false) }
    }

    fun showGraphExplorer() {
        val rawGraph = dualMemoryManager.getRawKnowledgeGraph()
        val formattedGraph = rawGraph.map { RawGraphEdge(it.first, it.second, it.third) }

        _uiState.update {
            it.copy(
                isGraphExplorerVisible = true,
                graphData = formattedGraph
            )
        }
    }

    fun hideGraphExplorer() {
        _uiState.update { it.copy(isGraphExplorerVisible = false) }
    }

    // --- Model Hub Logic ---

    fun showModelHub() {
        _uiState.update { it.copy(isModelHubVisible = true) }
    }

    fun hideModelHub() {
        _uiState.update { it.copy(isModelHubVisible = false) }
    }

    fun toggleLiveMode() {
        _uiState.update { state ->
            val newState = !state.isLiveModeActive
            if (!newState) voiceEngine.stop() // Stop speaking if live mode turned off
            state.copy(isLiveModeActive = newState)
        }
    }

    fun setBackendMode(mode: Int) {
        _uiState.update { it.copy(config = it.config.copy(selectedBackendMode = mode)) }
        repository.setBackendMode(mode)
        
        // Hotswap: If model is already loaded, trigger a re-initialization with the new backend.
        // isReady is driven by engineState observer — do NOT manually set it true here.
        val currentModel = _uiState.value.activeModelName
        if (currentModel != "no models selected") {
            viewModelScope.launch {
                _uiState.update { it.copy(isReady = false) }
                // Await the result properly — isReady is set in observeEngineState via InitializationState.Success
                repository.initializeEngines(getApplication())
                // NOTE: isReady will be updated by observeEngineState when engineState emits Success
            }
        }
    }

    fun updateConfig(newConfig: ScypheonConfig) {
        val oldConfig = _uiState.value.config
        _uiState.update { it.copy(config = newConfig) }
        
        // Persist to vault
        vault.saveConfig(newConfig)
        
        // Update the engine backend mode directly if it changed
        repository.setBackendMode(newConfig.selectedBackendMode)
        
        // If context window or backend changed, we must reload the model to apply the new params.
        // Call initializeEngines() which will emit new engineState events. The observeEngineState
        // collector updates isReady when InitializationState.Success is received.
        if (oldConfig.contextWindow != newConfig.contextWindow || oldConfig.selectedBackendMode != newConfig.selectedBackendMode) {
            timber.log.Timber.i("[CONFIG] Critical parameter changed (ctx=${newConfig.contextWindow}, backend=${newConfig.selectedBackendMode}). Refreshing Neural Link.")
            initializeEngines()
        }
    }

    fun toggleConfigDialog(visible: Boolean) {
        if (visible) {
            loadBackendDiagnostics()
        }
        _uiState.update { it.copy(isConfigVisible = visible) }
    }

    fun saveHfToken(token: String) {
        vault.saveHfToken(token)
        _uiState.update { it.copy(hfToken = token) }
    }

    fun downloadModel(model: com.scypheon.sdk.core.provision.ModelMetadata) {
        val downloadId = modelProvisioner.downloadGatedModel(
            model.downloadUrl,
            model.fileName,
            model.sizeBytes
        )
        if (downloadId != -1L) {
            _uiState.update { it.copy(downloadingModelId = model.id) }
            // In a production app, we would start a Coroutine to poll the DownloadManager status
            // For this version, we'll assume success or rely on the system notification.
        }
    }

    fun tryModel(model: com.scypheon.sdk.core.provision.ModelMetadata) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isReady = false) }
            
            // Stop current engine
            repository.releaseEngines()
            
            // Re-initialize with new model
            val modelFile = modelProvisioner.getModelPath(model.fileName)
            if (modelFile.exists()) {
                val modelPath = modelFile.absolutePath
                val engineType = if (model.fileName.endsWith(".task")) "LiteRT" else "Llama"
                
                val result = if (engineType == "LiteRT") {
                    repository.initializeEngines(getApplication(), customElitePath = modelPath)
                } else {
                    repository.initializeEngines(getApplication(), customUniversalPath = modelPath)
                }

                if (result is Result.Success) {
                    _uiState.update { 
                        it.copy(
                            isModelHubVisible = false, 
                            activeModelName = model.title, 
                            activeEngineType = engineType,
                            isReady = true
                        ) 
                    }
                    resumeLastSessionOrStandby()
                } else {
                    _uiState.update { it.copy(error = "Failed to hot-swap to ${model.title}", isReady = true) }
                }
            } else {
                _uiState.update { it.copy(error = "Model file not found on disk.", isReady = true) }
            }
        }
    }

    fun isModelDownloaded(fileName: String): Boolean = modelProvisioner.isModelOnDisk(fileName)

    fun deleteModel(fileName: String) {
        modelProvisioner.deleteModel(fileName)
        _uiState.update { it.copy() } // Force recompose
    }

    fun enqueueBackgroundAgentTask() {
        val workRequest = OneTimeWorkRequestBuilder<VitreusFlowWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(getApplication()).enqueue(workRequest)
    }

    fun toggleFeature(featureName: String) {
        if (featureName == "AgentWorkerTest") {
            enqueueBackgroundAgentTask()
            return
        }

        if (featureName == "ScypheonLiveBridge") {
            val nextState = !_uiState.value.isLiveModeActive
            _uiState.update { it.copy(isLiveModeActive = nextState) }
            if (nextState) {
                startLiveBridge()
            } else {
                stopLiveBridge()
            }
            return
        }

        if (_uiState.value.activeFeature == featureName) {
            // Turn off feature
            stopAllFeatures()
            _uiState.update { it.copy(activeFeature = null) }
        } else {
            // Turn on feature
            stopAllFeatures()
            _uiState.update { it.copy(activeFeature = featureName) }
            startFeature(featureName)
        }
    }

    private fun startLiveBridge() {
        // Initialize and start Deaf/Mute accessibility bridge
        val modelPath = modelProvisioner.getModelPath("gesture_recognizer.task").absolutePath
        // SignLanguageBridge handles camera in some implementations, 
        // DeafEnvironmentGuardian handles mic.
        deafEnvironmentGuardian.startListening()
        // kineticGuardian.startMonitoring() // Optional for vibration-based touch feedback
        
        blackBoxVault.logEvent("LIVE_BRIDGE_START", "Scypheon Live Bridge (Deaf/Mute) Activated")
    }

    private fun stopLiveBridge() {
        deafEnvironmentGuardian.stopListening()
        kineticGuardian.stopMonitoring()
        blackBoxVault.logEvent("LIVE_BRIDGE_STOP", "Scypheon Live Bridge Deactivated")
    }

    private fun stopAllFeatures() {
        if (liveEnglishTutor.isListening) liveEnglishTutor.stopListening()
        if (reminiscenceCompanion.isListening) reminiscenceCompanion.stopListening()
        if (deafEnvironmentGuardian.isListening) deafEnvironmentGuardian.stopListening()
        kineticGuardian.stopMonitoring()
    }

    private fun startFeature(featureName: String) {
        when (featureName) {
            "LiveEnglishTutor" -> {
                liveEnglishTutor.warmUp()
                liveEnglishTutor.startListening()
            }
            "ReminiscenceCompanion" -> {
                reminiscenceCompanion.warmUp()
                reminiscenceCompanion.initiateTherapySession()
            }
            "DeafEnvironmentGuardian" -> {
                deafEnvironmentGuardian.warmUp()
                deafEnvironmentGuardian.startListening()
            }
            "GestureGuardian" -> {
                gestureGuardian.warmUp()
                gestureGuardian.initialize()
            }
            "KineticGuardian" -> {
                kineticGuardian.warmUp()
                kineticGuardian.startMonitoring()
            }
            // MedicineGuard, ScamGuard, SignLanguageBridge often require CameraX or call listeners setup in Activity/Service
        }
    }

    fun onConfirmStabilityWarning() {
        _uiState.update { it.copy(memoryStabilityState = MemoryStabilityState.IDLE) }
    }

    fun stopGeneration() {
        timber.log.Timber.i("🛑 [KILL SWITCH] User requested generation stop.")
        inferenceJob?.cancel()
        _uiState.update { it.copy(isAiGenerating = false) }
        
        // Finalize the last partial message in the UI so it doesn't stay 'Loading'
        _uiState.update { state ->
            val finalMessages = state.messages.toMutableList()
            if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                val partialText = finalMessages.last().text
                finalMessages.removeAt(finalMessages.size - 1)
                finalMessages.add(ChatMessageUiState(
                    text = if (partialText == "Processing...") "Generation stopped." else "$partialText [STOPPED]",
                    isUser = false,
                    status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SYSTEM
                ))
            }
            state.copy(messages = finalMessages)
        }
    }

    fun retryMessage(text: String, imageUri: Uri?) {
        executeInference(text, imageUri, addUserMessage = false, isRetry = true)
    }

    private var cooldownJob: kotlinx.coroutines.Job? = null
    private fun triggerStabilityInterceptor() {
        cooldownJob?.cancel()
        _uiState.update { it.copy(memoryStabilityState = MemoryStabilityState.WARNING_COOLDOWN, memoryWarningCooldown = 8) }
        
        cooldownJob = viewModelScope.launch {
            for (i in 7 downTo 0) {
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(memoryWarningCooldown = i) }
            }
            _uiState.update { it.copy(memoryStabilityState = MemoryStabilityState.READY_TO_FORCE) }
        }
    }

    fun sendMessage(text: String, imageUri: Uri?): Boolean {
        if (_uiState.value.isAiGenerating) return false
        if (_uiState.value.memoryStabilityState == MemoryStabilityState.CRASHED) return false

        // [MDRS 4.1] Stability Interceptor
        val report = repository.getMemoryReport(getApplication())
        if (report.stressLevel >= 2 && _uiState.value.memoryStabilityState == MemoryStabilityState.IDLE) {
            triggerStabilityInterceptor()
            return false
        }

        if (_uiState.value.memoryStabilityState == MemoryStabilityState.WARNING_COOLDOWN) {
            return false // Block until cooldown finishes and user acknowledges
        }
        // RAG is a memory enhancement. Blocking on ragReady causes messages to be
        // silently queued forever if vectorEngineState gets stuck at Initializing
        // after a re-init cycle, since the queue drain also requires Llama Success.
        val llamaReady = _uiState.value.engineState is InitializationState.Success

        if (!llamaReady) {
            // Silent Queuing: Add to UI immediately so the user feels "instant" response
            val displayMsg = if (imageUri != null) "[Image Attached] $text" else text
            _uiState.update { state -> 
                state.copy(messages = state.messages + ChatMessageUiState(text = displayMsg, isUser = true))
            }
            
            viewModelScope.launch {
                promptQueue.emit(text to imageUri)
            }
            return true
        }

        executeInference(text, imageUri, addUserMessage = true)
        return true
    }

    private fun executeInference(
        text: String, 
        imageUri: Uri?, 
        addUserMessage: Boolean = true,
        isRetry: Boolean = false
    ) {

        // JIT Session ID Generation (Synchronous UI part)
        if (_uiState.value.currentSessionId.isEmpty()) {
            val newId = "session_${System.currentTimeMillis()}"
            _uiState.update { it.copy(currentSessionId = newId) }
        }

        val finalSessionId = _uiState.value.currentSessionId
        val redactedText = AegisPrivacyShield.redact(text)

        // 孱・・GUARDRAIL: Multi-Layered Intent Scanning
        val guardrailStatus = AegisPrivacyShield.scanIntent(text)
        when (guardrailStatus) {
            AegisPrivacyShield.GuardrailViolation.SYSTEM_MALICIOUS -> {
                _uiState.update { state ->
                    state.copy(messages = state.messages + ChatMessageUiState(redactedText, isUser = true) +
                        ChatMessageUiState("🛡️ Access Denied: This request contains potentially malicious system commands.", isUser = false))
                }
                return
            }
            AegisPrivacyShield.GuardrailViolation.CRISIS_DETECTED -> {
                _uiState.update { state ->
                    state.copy(messages = state.messages + ChatMessageUiState(redactedText, isUser = true) +
                        ChatMessageUiState("💜 You are not alone. Please do not harm yourself. Support is available: Reach out to emergency services or a mental health professional immediately.", isUser = false))
                }
                return
            }
            AegisPrivacyShield.GuardrailViolation.JAILBREAK_ATTEMPT -> {
                _uiState.update { state ->
                    state.copy(messages = state.messages + ChatMessageUiState(redactedText, isUser = true) +
                        ChatMessageUiState("🛡️ Request Blocked: Prompt manipulation (Jailbreaking) is not permitted in this system.", isUser = false))
                }
                return
            }
            AegisPrivacyShield.GuardrailViolation.MEDICAL_ADVICE -> {
                // For medical advice, we allow the LLM to process it but we flag it so we can append a disclaimer later if needed.
                // Or simply let the LLM handle it, as the system prompt forces the disclaimer.
                timber.log.Timber.i("Medical query detected. System prompt will enforce disclaimer.")
            }
            AegisPrivacyShield.GuardrailViolation.NONE -> { /* Safe to proceed */ }
        }

        val displayMsg = if (imageUri != null) "[Image Attached] $redactedText" else redactedText

        // �､・REAL PUPPETMASTER TRIGGER
        // If the user uses the "open" or "automate" command, we intercept it before the LLM
        // and dispatch an Intent, proving the automation tier 1 fallback is wired up.
        if (text.lowercase().startsWith("/open ") || text.lowercase().startsWith("/automate ")) {
            val target = text.substringAfter(" ").trim()
            val puppetMasterIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            puppetMasterIntent.data = android.net.Uri.parse("market://search?q=$target") // Simulated deep link
            puppetMasterIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

            val intentResultMsg = try {
                getApplication<android.app.Application>().startActivity(puppetMasterIntent)
                "PuppetMaster: Executing automation for '$target' via DeepLink Intent."
            } catch (e: Exception) {
                "PuppetMaster: Failed to execute automation for '$target'."
            }

                _uiState.update { state ->
                    val newMessages = state.messages +
                        ChatMessageUiState(text = displayMsg, isUser = true) +
                        ChatMessageUiState(text = intentResultMsg, isUser = false)
                    state.copy(messages = newMessages)
                }
            return
        }

        // Add user message and loading state
        _uiState.update { state ->
            val newMessages = if (addUserMessage) {
                state.messages +
                ChatMessageUiState(text = displayMsg, isUser = true) +
                ChatMessageUiState(text = "Processing...", isUser = false, isLoading = true)
            } else if (isRetry) {
                // If retry, we assume the user message is already there, but we might need to remove a previous 'Failed' error bubble
                val filtered = state.messages.filter { it.status != com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED && it.status != com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SYSTEM }
                filtered + ChatMessageUiState(text = "Processing...", isUser = false, isLoading = true)
            } else {
                state.messages +
                ChatMessageUiState(text = "Processing...", isUser = false, isLoading = true)
            }
            state.copy(messages = newMessages, isAiGenerating = true)
        }

        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            // JIT DB Creation (Asynchronous part)
            val history = dualMemoryManager.getAllSessions()
            if (history.none { it.id == finalSessionId }) {
                repository.createNewSession(finalSessionId, "New Session")
            }

            // ML Kit Local OCR fallback for Vision Multimodal
            var finalPrompt = redactedText
            if (imageUri != null) {
                try {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val image = InputImage.fromFilePath(getApplication(), imageUri)
                    val result = recognizer.process(image).await()
                    val extractedText = result.text.replace("\n", " ")
                    finalPrompt = "I am attaching an image. I extracted this text from it using OCR: \"$extractedText\". \n\nMy question is: $redactedText"
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to extract text from image")
                    finalPrompt = "I attached an image but the OCR failed. Assume I attached an image related to my question: $redactedText"
                }
            }

            // Save user msg (SAR: Persist immediately for Outbox)
            if (addUserMessage) {
                repository.saveSessionMessage(finalSessionId, displayMsg, true)
            }

            // Dynamic session title update on first message
            if (_uiState.value.messages.size == 2) { // 1 user + 1 loading (greeting is no longer in the list)
                val title = if (text.length > 20) text.substring(0, 20) + "..." else text
                repository.updateSessionTitle(finalSessionId, title)
                loadSessionHistory()
            }

            // �ｧｬ ENTERPRISE HYBRID RAG INJECTION (Vector + Graph)

            // 1. Vector RAG: Semantic Reciprocal Rank Fusion (RRF) search over past history
            val vectorContext = dualMemoryManager.searchSimilarMemories(redactedText, limit = 2)

            // 2. Graph RAG: Exact logical Subject-Predicate-Object matching (Fixing naive extraction)
            val stopWords = setOf("i", "am", "the", "a", "an", "is", "are", "was", "were", "to", "for", "with", "hello", "hi", "hey", "please", "can", "you", "tell", "me", "about")
            val tokens = redactedText.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "") // Strip punctuation
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() && !stopWords.contains(it) } // Filter stop words

            val graphContext = mutableListOf<String>()
            // Query the top 3 most meaningful keywords
            tokens.take(3).forEach { keyword ->
                graphContext.addAll(graphMemoryManager.querySubject(keyword))
            }

            // 3. Session Dialogue Context (Conversational Memory)
            val historyTurns = _uiState.value.messages.takeLast(10).filter { msg ->
                !msg.isLoading && 
                msg.isContextEligible && 
                msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SUCCESS
            }.map { msg ->
                com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                    if (msg.isUser) com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.USER 
                    else com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.ASSISTANT,
                    msg.text
                )
            }


            // [ARCHITECT ALIGNMENT] Assemble the final Multi-Turn Neural Context
            val finalTurns = mutableListOf<com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn>()
            
            // 1. Core History
            finalTurns.addAll(historyTurns)

            // 2. RAG Injection (as a dedicated System Context turn before the final query)
            val combinedContext = mutableListOf<String>()
            if (vectorContext.isNotEmpty()) {
                combinedContext.add("Semantic Memory: [${vectorContext.joinToString(" | ")}]")
            }
            if (graphContext.isNotEmpty()) {
                combinedContext.add("Logical Facts: [${graphContext.distinct().joinToString(" | ")}]")
            }

            if (combinedContext.isNotEmpty()) {
                val rawContextString = combinedContext.joinToString("\n")
                val MAX_CONTEXT_CHARS = 3000
                val prunedContext = if (rawContextString.length > MAX_CONTEXT_CHARS) {
                    rawContextString.substring(0, MAX_CONTEXT_CHARS) + "... [TRUNCATED]"
                } else {
                    rawContextString
                }
                
                finalTurns.add(com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                    com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.SYSTEM,
                    "Relevant Contextual Knowledge (RAG):\n$prunedContext"
                ))
            }

            // 3. Final User Intent
            finalTurns.add(com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.USER,
                finalPrompt
            ))

            // Generate with Real-Time Streaming (Google AI Edge Style)
            var fullResponse = ""
            var hardwareStatus: String? = null

            try {
                val config = _uiState.value.config
                // 4. Generate with Real-Time Streaming
                repository.generateStreamingResponse(
                    finalTurns,
                    topK = _uiState.value.config.topK,
                    topP = _uiState.value.config.topP,
                    temp = _uiState.value.config.temperature,
                    enableThinking = _uiState.value.config.enableThinking
                ).collect { chunk ->
                    fullResponse += chunk
                    if (hardwareStatus == null && fullResponse.isNotEmpty()) {
                        hardwareStatus = repository.getHardwareStatus()
                    }

                    _uiState.update { state ->
                        val finalMessages = state.messages.toMutableList()
                        val sanitizedResponse = sanitizeResponse(fullResponse)
                        
                        // Loading -> Streaming transition
                        if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                            finalMessages.removeAt(finalMessages.size - 1)
                            finalMessages.add(ChatMessageUiState(text = sanitizedResponse, isUser = false, hardwareStatus = hardwareStatus))
                        } else if (finalMessages.isNotEmpty() && !finalMessages.last().isUser) {
                            // Update existing streaming bubble
                            finalMessages[finalMessages.size - 1] = ChatMessageUiState(text = sanitizedResponse, isUser = false, hardwareStatus = hardwareStatus)
                        } else {
                            // Fallback for unexpected states
                            finalMessages.add(ChatMessageUiState(text = sanitizedResponse, isUser = false, hardwareStatus = hardwareStatus))
                        }
                        state.copy(messages = finalMessages)
                    }
                }

                // AI Response Complete: Persist to Edge Storage.
                val sanitizedFinal = sanitizeResponse(fullResponse)
                val isEngineError = sanitizedFinal.startsWith("Error:") || sanitizedFinal.isBlank()
                
                if (!isEngineError) {
                    repository.saveSessionMessage(finalSessionId, sanitizedFinal, false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SUCCESS)
                } else {
                    repository.saveSessionMessage(finalSessionId, sanitizedFinal, false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED)
                }

                // 貯 LIVE VOICE FEEDBACK: Auto-speak in live mode
                if (_uiState.value.isLiveModeActive && !isEngineError) {
                    voiceEngine.speak(sanitizedFinal)
                }

                // Enterprise Edge Max: The Infinite Memory Illusion
                contextSummarizer.checkAndSummarizeSessionAsync(finalSessionId)

            } catch (e: Exception) {
                 timber.log.Timber.e(e, "Streaming generation failed")
                 
                 //  [SAR] Solaris Protocol: Save the failure so the UI can show 'Retry'
                 repository.saveSessionMessage(finalSessionId, "Error: ${e.message}", false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED)
                 
                 _uiState.update { state ->
                     val finalMessages = state.messages.toMutableList()
                     if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                         finalMessages.removeAt(finalMessages.size - 1)
                     }
                     finalMessages.add(ChatMessageUiState(text = "Error: ${e.message}", isUser = false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED))
                     state.copy(messages = finalMessages)
                 }
            } finally {
                _uiState.update { it.copy(isAiGenerating = false) }
            }
        }
    }

    fun loadBackendDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val filesDir = getApplication<Application>().filesDir
            val diagnostics = mutableListOf<ScypheonBackendDiagnostic>()

            // Check Vulkan
            val vkFile = java.io.File(filesDir, "vulkan_crash.json")
            if (vkFile.exists()) {
                try {
                    val json = vkFile.readText()
                    val obj = org.json.JSONObject(json)
                    diagnostics.add(ScypheonBackendDiagnostic(
                        backend = "Vulkan",
                        signal = obj.getInt("signal"),
                        signalName = obj.getString("signal_name"),
                        timestamp = obj.getString("timestamp")
                    ))
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to parse Vulkan diagnostic")
                }
            }

            // Check OpenCL
            val clFile = java.io.File(filesDir, "opencl_crash.json")
            if (clFile.exists()) {
                try {
                    val json = clFile.readText()
                    val obj = org.json.JSONObject(json)
                    diagnostics.add(ScypheonBackendDiagnostic(
                        backend = "OpenCL",
                        signal = obj.getInt("signal"),
                        signalName = obj.getString("signal_name"),
                        timestamp = obj.getString("timestamp")
                    ))
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to parse OpenCL diagnostic")
                }
            }

            _uiState.update { it.copy(config = it.config.copy(backendDiagnostics = diagnostics)) }
        }
    }

    fun resetHardwareOverrides() {
        viewModelScope.launch(Dispatchers.IO) {
            val filesDir = getApplication<Application>().filesDir
            
            // Clear Explicit Crash Reports
            java.io.File(filesDir, "VULKAN_crash.json").delete()
            java.io.File(filesDir, "OPENCL_crash.json").delete()
            
            // 🛡️ TRIPWIRE: Clear Tombstone Flags (Tripwire 2.0)
            java.io.File(filesDir, "VULKAN_TRYING.flag").delete()
            java.io.File(filesDir, "OPENCL_TRYING.flag").delete()

            _uiState.update { it.copy(config = it.config.copy(backendDiagnostics = emptyList())) }

            // Re-initialize engines to allow GPU attempts again
            initializeEngines()
            
            timber.log.Timber.i("🛡️ TRIPWIRE: Hardware blacklists and tombstone flags cleared successfully.")
        }
    }

    private fun sanitizeResponse(text: String): String {
        // 1. Strip common AI role prefixes that might leak from chat templates
        val prefixes = listOf("Assistant:", "assistant:", "model:", "AI:", "User:", "user:")
        // 2. Strip protocol markers that might leak into the output
        val structuralMarkers = listOf("<end_of_turn>", "<|im_end|>", "<|im_start|>", "<|eot_id|>", "</s>")
        
        var result = text.trim()
        
        // Clean Prefix
        for (prefix in prefixes) {
            if (result.startsWith(prefix, ignoreCase = true)) {
                val stripped = result.substring(prefix.length).trimStart()
                if (stripped.isNotEmpty()) result = stripped
            }
        }
        
        // Clean Suffix / Protocol Tokens
        for (marker in structuralMarkers) {
            if (result.contains(marker)) {
                result = result.substringBefore(marker).trimEnd()
            }
        }
        
        return result
    }

    /**
     * [v1.0.5-SAR] Pocket Agent Activation.
     * Performs a background warm-up to prepare the agent's engines without blocking the UI.
     */
    fun activateAgent(featureId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val agent = agents[featureId]
            if (agent != null && !agent.isReady()) {
                Timber.i(" [POCKET] Activating $featureId...")
                agent.warmUp()
            }
        }
    }

    /**
     * [MDRS] Proactive Resource Reclamation.
     * Shuts down all active auxiliary AI engines to free RAM for the main LLM.
     */
    fun unloadAllAgents() {
        Timber.w(" [MDRS] Unloading all auxiliary agents to reclaim RAM.")
        agents.values.forEach { agent ->
            try {
                if (agent.isReady()) {
                    agent.release()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to release agent")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unloadAllAgents()
        voiceEngine.shutdown()
    }
}
