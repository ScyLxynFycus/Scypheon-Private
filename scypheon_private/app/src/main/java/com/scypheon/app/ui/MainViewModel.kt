package com.scypheon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scypheon.app.data.local.HardwarePreferences
import com.scypheon.app.data.repository.ScypheonRepository
import com.scypheon.sdk.core.humanitarian.education.LiveEnglishTutor
import com.scypheon.sdk.core.humanitarian.psychology.ReminiscenceCompanion
import com.scypheon.sdk.core.humanitarian.accessibility.DeafEnvironmentGuardian
import com.scypheon.sdk.core.memory.ContextSummarizer
import com.scypheon.sdk.core.live.LiveSessionOrchestrator
import com.scypheon.sdk.core.live.ContinuousSpeechRecognizer
import com.scypheon.sdk.core.live.LiveVisionPipeline
import com.scypheon.sdk.core.live.LiveAudioPipeline
import com.scypheon.sdk.core.memory.Session
import com.scypheon.sdk.core.memory.ChatMessage
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
import kotlinx.coroutines.delay
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.humanitarian.accessibility.VisualGuide
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.memory.GraphMemoryManager
import com.scypheon.sdk.core.security.AegisPrivacyShield
import com.scypheon.app.ui.screens.GraphEdge
import com.scypheon.sdk.core.utils.Result
import com.scypheon.app.data.models.SystemHealth
import com.scypheon.app.data.models.OomDiagnostic
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
    val source: String? = null, // e.g. "Clinical Database", "Neural Vault"
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
    val graphData: List<GraphEdge> = emptyList(),
    val sessionHistory: List<Session> = emptyList(),
    
    // Identity
    val userName: String = "",
    
    // Model Hub State
    val isModelHubVisible: Boolean = false,
    val activeModelName: String = "no models selected",
    val activeEngineType: String? = null,
    val hfToken: String = "",
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    
    // HuggingFace Search (on-demand)
    val hfSearchResults: List<com.scypheon.app.provision.HuggingFaceClient.HfModelInfo> = emptyList(),
    val hfSearchLoading: Boolean = false,
    val hfSearchQuery: String = "",
    val hfSelectedRepo: String? = null,
    val hfRepoFiles: List<com.scypheon.app.provision.HuggingFaceClient.HfModelFile> = emptyList(),
    val hfRepoDetail: com.scypheon.app.provision.HuggingFaceClient.HfModelDetail? = null,
    val hfFilesLoading: Boolean = false,
    
    // License Confirmation Dialog
    val pendingDownloadFile: com.scypheon.app.provision.HuggingFaceClient.HfModelFile? = null,
    val pendingDownloadRepoId: String? = null,
    val pendingDownloadLicense: String? = null,
    val pendingDownloadLicenseUrl: String? = null,
    
    // Live Mode State
    val isLiveModeActive: Boolean = false,
    val liveState: LiveSessionOrchestrator.LiveState = LiveSessionOrchestrator.LiveState.Idle,
    val liveTranscript: List<LiveSessionOrchestrator.TranscriptEntry> = emptyList(),
    val liveAudioLevel: Float = 0f,
    
    // Waveform Animation Phase
    val voiceAmplitude: Float = 0f,
    
    // System Health / Diagnostics
    val systemHealth: SystemHealth? = null,
    val isSystemHealthVisible: Boolean = false,
    val systemWarning: String? = null,
    
    // Scypheon Pro Configurations
    val config: ScypheonConfig = ScypheonConfig(),
    val isConfigVisible: Boolean = false,
    val isAiGenerating: Boolean = false,
    val engineState: InitializationState = InitializationState.Idle,
    val ragState: com.scypheon.sdk.core.memory.IVectorEngine.EngineState = com.scypheon.sdk.core.memory.IVectorEngine.EngineState.Initializing,
    
    // Safety & Resilience
    val thermalLevel: com.scypheon.sdk.core.resilience.ThermalLevel = com.scypheon.sdk.core.resilience.ThermalLevel.NORMAL,
    val deviceTemperature: Float = 0f,
    val diagnosticLogs: List<String> = emptyList(),
    val isSandboxAlive: Boolean = true,
    val isMemoryOptimized: Boolean = false,
    val isNotificationSuppressed: Boolean = false,
    val memoryStabilityState: MemoryStabilityState = MemoryStabilityState.IDLE,
    val memoryWarningCooldown: Int = 0,
    val oomDiagnostic: OomDiagnostic? = null,
    val isMemoryInconsistent: Boolean = false
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
    private val liveEnglishTutor: dagger.Lazy<LiveEnglishTutor>,
    private val reminiscenceCompanion: dagger.Lazy<ReminiscenceCompanion>,
    private val deafEnvironmentGuardian: dagger.Lazy<DeafEnvironmentGuardian>,
    private val gestureGuardian: dagger.Lazy<GestureGuardian>,
    private val kineticGuardian: dagger.Lazy<KineticGuardian>,
    private val blackBoxVault: BlackBoxVault,
    private val contextSummarizer: ContextSummarizer,
    private val dualMemoryManager: DualMemoryManager,
    private val graphMemoryManager: GraphMemoryManager,
    private val modelProvisioner: com.scypheon.sdk.core.provision.ModelProvisioner,
    private val huggingFaceClient: com.scypheon.app.provision.HuggingFaceClient,
    private val vault: com.scypheon.sdk.core.security.AegisVault,
    private val sensoryHooks: com.scypheon.sdk.core.gateway.SensoryHooks,
    private val hardwarePrefs: com.scypheon.app.data.local.HardwarePreferences,
    private val thermalGovernor: com.scypheon.sdk.core.resilience.AegisThermalGovernor,
    private val promptBuilder: com.scypheon.sdk.core.safety.helios.PromptBuilder,
    private val safetyRouter: com.scypheon.sdk.core.safety.SafetyRouter,
    private val safetySeeder: com.scypheon.sdk.core.safety.helios.SafetyRuleSeeder,
    private val toolGateway: com.scypheon.sdk.core.safety.helios.ToolAuthorizationGateway,
    val liveOrchestrator: LiveSessionOrchestrator,
    val liveSpeechRecognizer: ContinuousSpeechRecognizer,
    val liveVisionPipeline: LiveVisionPipeline,
    val liveAudioPipeline: LiveAudioPipeline
) : AndroidViewModel(application) {

    private val voiceEngine = com.scypheon.sdk.core.voice.AegisVoiceEngine(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pendingModelFile: java.io.File? = null

    private val promptQueue = kotlinx.coroutines.channels.Channel<Pair<String, Uri?>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var accumulatedSpeechText = ""
    private var inferenceJob: kotlinx.coroutines.Job? = null

    // --- POCKET AGENTS REGISTRY ---
    private val agents = mapOf<String, dagger.Lazy<out com.scypheon.sdk.core.humanitarian.ScypheonAgent>>(
        "tutor" to liveEnglishTutor,
        "reminiscence" to reminiscenceCompanion,
        "deaf" to deafEnvironmentGuardian
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
            
            // Scan local models and resolve active model name without loading!
            val bestModel = hardwarePrefs.resolveBestFittingModel()
            
            _uiState.update { 
                it.copy(
                    hfToken = hfToken,
                    userName = name,
                    config = cfg,
                    activeModelName = bestModel?.name?.let { "$it (STANDBY)" } ?: "no models selected",
                    isReady = true
                ) 
            }
            
            resumeLastSessionOrStandby()
            
            // [MEMORY GUARD] Concurrent purge of corrupted engine error messages.
            try {
                dualMemoryManager.purgeEngineErrorMessages()
            } catch (e: Exception) {
                if (e.message == "DATABASE_CORRUPTION_FTS") {
                    _uiState.update { it.copy(isMemoryInconsistent = true) }
                } else {
                    Timber.e(e, "Fatal database purge failure during startup.")
                }
            }
        }

        observeLiveEvents()
        observeEngineState()
        observeProcessHealth()
        observeRagState()
        observeMemoryOptimization()
        observePromptQueue()
        observeOomDiagnostic()
        
        // Start Safety Seeding
        viewModelScope.launch {
            safetySeeder.seedIfNeeded()
        }
        
        // Start Thermal Monitoring
        observeThermalStatus()
    }

    private fun observeThermalStatus() {
        viewModelScope.launch {
            thermalGovernor.thermalStatus.collect { level ->
                _uiState.update { it.copy(thermalLevel = level) }
                
                when (level) {
                    com.scypheon.sdk.core.resilience.ThermalLevel.WARNING -> {
                        Timber.w("🛡️ [AEGIS] Thermal Warning: 45C reached.")
                    }
                    com.scypheon.sdk.core.resilience.ThermalLevel.SEVERE -> {
                        Timber.e("🛡️ [AEGIS] SEVERE THERMAL WARNING: 48C. Red alert active.")
                    }
                    com.scypheon.sdk.core.resilience.ThermalLevel.CRITICAL -> {
                        Timber.e("🛡️ [AEGIS] CRITICAL THERMAL: 50C. KICKING INFERENCE SHIELD.")
                        stopInferenceAndCoolDown()
                    }
                    else -> {}
                }
            }
        }
        
        viewModelScope.launch {
            thermalGovernor.currentTemperature.collect { temp ->
                _uiState.update { it.copy(deviceTemperature = temp) }
            }
        }
    }

    private fun stopInferenceAndCoolDown() {
        viewModelScope.launch {
            inferenceJob?.cancel()
            _uiState.update { 
                it.copy(
                    isAiGenerating = false,
                    error = "EMERGENCY SHUTDOWN: Device temperature exceeded 50°C. Halting AI engine to prevent hardware damage."
                )
            }
            repository.releaseEngines()
        }
    }

    private var isRecovering = false

    private fun observeProcessHealth() {
        viewModelScope.launch {
            repository.processHealth.collect { isAlive ->
                if (!isAlive) {
                    if (isRecovering) return@collect
                    isRecovering = true
                    
                    val logEntry = "🛡️ ALERT: Sandbox Process CRASHED. Initiating Phoenix Recovery..."
                    _uiState.update { s -> 
                        s.copy(
                            isSandboxAlive = false,
                            isAiGenerating = false,
                            error = "System Anomaly: The AI core has restarted to protect device stability. Recovering...",
                            memoryStabilityState = MemoryStabilityState.CRASHED,
                            diagnosticLogs = s.diagnosticLogs + logEntry
                        )
                    }
                    
                    // [PHOENIX RECOVERY]
                    // Wait for the OS to clean up the process, then attempt a cold restart.
                    delay(5000)
                    rebootEngine()
                } else {
                    if (isRecovering) {
                        Timber.i("✅ [PHOENIX] Sandbox process restored. Clearing crash UI.")
                        isRecovering = false
                    }
                    _uiState.update { it.copy(
                        isSandboxAlive = true,
                        memoryStabilityState = if (it.memoryStabilityState == MemoryStabilityState.CRASHED) MemoryStabilityState.IDLE else it.memoryStabilityState
                    ) }
                }
            }
        }
    }

    private fun rebootEngine() {
        viewModelScope.launch {
            Timber.w("🔥 [PHOENIX] Attempting Cold Reboot of the AI Sandbox...")
            repository.initializeEngines(getApplication())
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
                    val isSuccess = state is InitializationState.Success
                    s.copy(
                        engineState = state,
                        isReady = if (isSuccess) true else s.isReady,
                        error = when(state) {
                            is InitializationState.Success -> null
                            is InitializationState.Failed -> state.error
                            else -> s.error
                        },
                        diagnosticLogs = if (logEntry != null) s.diagnosticLogs + logEntry else s.diagnosticLogs
                    )
                }

                // [v1.1.3-SAR] Resolve model name on IO thread to prevent StrictMode violations
                if (state is InitializationState.Success) {
                    val currentName = _uiState.value.activeModelName.removeSuffix(" (STANDBY)")
                    _uiState.update { it.copy(activeModelName = currentName) }
                    
                    if (currentName == "no models selected") {
                        withContext(Dispatchers.IO) {
                            val model = hardwarePrefs.resolveBestFittingModel()
                            model?.let { m ->
                                _uiState.update { it.copy(activeModelName = m.name) }
                            }
                        }
                    }
                    
                    // Auto-start live mode if it was pending
                    if (_uiState.value.isLiveModeActive && !liveOrchestrator.isActive) {
                        viewModelScope.launch {
                            delay(500)
                            startLiveMode()
                        }
                    }
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
            repository.engineState.collectLatest { state ->
                if (state is InitializationState.Success) {
                    for (prompt in promptQueue) {
                        executeInference(prompt.first, prompt.second, addUserMessage = false)
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
            val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            val channel = manager.getNotificationChannel("solaris_neural_core")
            
            // v1.1.2 Hardening: Don't suppress if the channel is just missing (it will be created by the SandboxService)
            // ONLY suppress if app-level notifications are explicitly disabled OR the channel exists and was silenced.
            !appNotificationsEnabled || (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE)
        } else {
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        if (_uiState.value.isNotificationSuppressed != isSuppressed) {
            Timber.w("🛡️ [PHOENIX] Notification Guard Status Change: suppressed=$isSuppressed")
            _uiState.update { it.copy(isNotificationSuppressed = isSuppressed) }
        }
    }

    fun saveUserName(name: String) {
        // [v1.5.0-SAR] Vault write off main thread
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            vault.saveUserName(name)
        }
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
            _uiState.update { it.copy(isReady = false, memoryStabilityState = MemoryStabilityState.IDLE) }
            
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
                    clearHardwareBlacklists()
                    vault.saveLastHwCheckVersion(currentVersion)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check package version for Tripwire reset.")
            }

            val health = repository.checkSystemHealth(getApplication())
            val currentConfig = vault.loadConfig()
            
            // Dynamically resolve best fitting model before initialization
            val bestModel = hardwarePrefs.resolveBestFittingModel()
            
            if (bestModel == null) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        error = "Critical Error: No models discovered in storage. Please download a model to continue.",
                        isReady = true
                    ) }
                }
                return@launch
            }

            val result = repository.initializeEngines(getApplication(), nCtx = currentConfig.contextWindow)
            
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(systemHealth = health) }

                if (result is Result.Success) {
                    // Check for Memory Guard Veto or substitution warnings
                    val warning = repository.getPendingInitializationWarning()
                    if (warning?.contains("VETO") == true) {
                        _uiState.update { it.copy(
                            systemWarning = "Memory Guard Alert: The selected model is too large. Attempting to load the largest safe fallback model instead.",
                            activeModelName = "${bestModel.name} (Fallback Active)"
                        ) }
                    } else if (warning == "OPENCL_PIVOT_TRIGGERED") {
                        _uiState.update { it.copy(systemWarning = "Memory Optimization: Using OpenCL to reduce VRAM pressure.") }
                    }

                    if (result.data == true) {
                        val hwStatus = repository.getHardwareStatus()
                        _uiState.update { 
                            it.copy(
                                activeModelName = bestModel.name,
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
            val uiMessages = dbMessages.mapNotNull { 
                // [v1.4.0-SAR] Strip internal [SUMMARY] tags before rendering to UI.
                // These are context-compression artifacts, NOT user-facing content.
                val displayText = if (it.text.startsWith("[SUMMARY]")) {
                    it.text.removePrefix("[SUMMARY]").trim()
                } else {
                    it.text
                }

                // [v1.4.0-SAR] Ghost Bubble Guard: Don't render empty summary blocks.
                if (displayText.isBlank()) return@mapNotNull null

                ChatMessageUiState(
                    text = displayText, 
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
        viewModelScope.launch(Dispatchers.IO) {
            val report = com.scypheon.sdk.core.utils.MemoryGatekeeper.performPreflightCheck(getApplication(), file.length())
            withContext(Dispatchers.Main) {
                if (report.stressLevel >= 2) {
                    pendingModelFile = file
                    _uiState.update { 
                        it.copy(
                            config = it.config.copy(isLocalModelPickerVisible = false),
                            systemWarning = "This model requires more RAM than is currently available. Proceeding may cause a system-wide crash. Do you wish to force load it?"
                        ) 
                    }
                } else {
                    executeModelSwap(file)
                }
            }
        }
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
        viewModelScope.launch {
            val rawGraph = dualMemoryManager.getRawKnowledgeGraph()
            val formattedGraph = rawGraph.map { GraphEdge(it.first, it.second, it.third) }

            _uiState.update {
                it.copy(
                    isGraphExplorerVisible = true,
                    graphData = formattedGraph
                )
            }
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
        val isCurrentlyActive = _uiState.value.isLiveModeActive
        
        if (isCurrentlyActive) {
            // Stop live mode
            stopLiveMode()
        } else {
            // Start live mode
            startLiveMode()
        }
    }

    private fun ensureEngineLoaded() {
        val state = _uiState.value.engineState
        if (state is InitializationState.Idle || state is InitializationState.Failed) {
            Timber.i("⚙️ [STANDBY] On-Demand Model Loading Triggered.")
            // Reset activeModelName to remove "(STANDBY)" suffix while loading
            val currentModel = _uiState.value.activeModelName.removeSuffix(" (STANDBY)")
            _uiState.update { it.copy(activeModelName = currentModel) }
            initializeEngines()
        }
    }

    fun onLiveOrbClick() {
        val currentState = _uiState.value.liveState
        Timber.i("🎙️ [LIVE] Orb clicked. State: $currentState")
        when (currentState) {
            is LiveSessionOrchestrator.LiveState.Idle -> {
                startLiveMode()
            }
            is LiveSessionOrchestrator.LiveState.Listening -> {
                // Start manual voice recording!
                accumulatedSpeechText = ""
                _uiState.update { it.copy(liveState = LiveSessionOrchestrator.LiveState.UserSpeaking("")) }
                liveSpeechRecognizer.startListening()
            }
            is LiveSessionOrchestrator.LiveState.UserSpeaking -> {
                // Manual send - stop recording and submit accumulated text!
                liveSpeechRecognizer.stopListening()
                val textToSubmit = accumulatedSpeechText
                if (textToSubmit.isNotBlank()) {
                    liveOrchestrator.onUserSpeechComplete(textToSubmit)
                } else {
                    // Transition back to listening if nothing was spoken
                    _uiState.update { it.copy(liveState = LiveSessionOrchestrator.LiveState.Listening) }
                }
            }
            is LiveSessionOrchestrator.LiveState.AiSpeaking, is LiveSessionOrchestrator.LiveState.Processing -> {
                // Interrupt AI: stop speaking and return to listening standby
                voiceEngine.stop()
                liveSpeechRecognizer.stopListening()
                liveOrchestrator.onAiFinishedSpeaking() // Sets state to Listening
            }
            is LiveSessionOrchestrator.LiveState.Error -> {
                liveOrchestrator.onAiFinishedSpeaking()
            }
        }
    }

    private fun startLiveMode() {
        Timber.i("🎙️ [LIVE] Starting Scypheon Live Mode...")
        _uiState.update { it.copy(isLiveModeActive = true) }

        val llamaReady = _uiState.value.engineState is InitializationState.Success
        if (!llamaReady) {
            ensureEngineLoaded()
            return // Will auto-start once engineState reaches InitializationState.Success
        }

        // 1. Initialize the orchestrator
        liveOrchestrator.startSession()

        // 2. Initialize continuous STT with manual trigger callback wiring
        liveSpeechRecognizer.initialize()
        accumulatedSpeechText = ""
        liveSpeechRecognizer.onPartialResult = { partial ->
            accumulatedSpeechText = partial
            liveOrchestrator.onPartialSpeech(partial)
        }
        liveSpeechRecognizer.onFinalResult = { finalText ->
            accumulatedSpeechText = finalText
            liveOrchestrator.onPartialSpeech(finalText) // Show final text in UI, but do NOT auto-submit!
        }
        liveSpeechRecognizer.onRmsChanged = { rmsDb ->
            liveOrchestrator.onAudioLevel(rmsDb)
        }
        liveSpeechRecognizer.onError = { error ->
            Timber.w("🎤 [STT] Error in live mode: $error")
            // Auto-restart STT if we are still manually recording
            if (_uiState.value.liveState is LiveSessionOrchestrator.LiveState.UserSpeaking) {
                liveSpeechRecognizer.startListening()
            }
        }

        // 3. Observe orchestrator state and sync to UI (without automatic STT start in Listening)
        viewModelScope.launch {
            liveOrchestrator.state.collectLatest { liveState ->
                _uiState.update { it.copy(liveState = liveState) }

                when (liveState) {
                    is LiveSessionOrchestrator.LiveState.Listening -> {
                        // User must tap Orb manually to start speech recognizer
                        liveSpeechRecognizer.stopListening()
                    }
                    is LiveSessionOrchestrator.LiveState.Processing -> {
                        liveSpeechRecognizer.stopListening()
                    }
                    is LiveSessionOrchestrator.LiveState.AiSpeaking -> {
                        liveSpeechRecognizer.stopListening()
                        voiceEngine.speak(liveState.responseText) {
                            // TTS completion callback
                            liveOrchestrator.onAiFinishedSpeaking()
                        }
                    }
                    else -> {}
                }
            }
        }

        // 4. Forward transcript to UI
        viewModelScope.launch {
            liveOrchestrator.transcript.collectLatest { entries ->
                _uiState.update { it.copy(liveTranscript = entries) }
            }
        }

        // 5. Forward audio level to UI
        viewModelScope.launch {
            liveOrchestrator.audioLevel.collectLatest { level ->
                _uiState.update { it.copy(liveAudioLevel = level) }
            }
        }
        // 6. Start Vision Pipeline (continuous camera → object detection → context)
        liveVisionPipeline.initializeDetector()
        liveVisionPipeline.onSceneUpdated = { scene ->
            liveOrchestrator.injectVisionContext(scene.toContextString())
        }
        liveVisionPipeline.onKeyframeCaptured = { bitmap ->
            liveOrchestrator.injectCameraFrame(bitmap)
        }
        // Camera will be started from the UI (needs LifecycleOwner)

        // 7. Start Audio Pipeline (continuous mic → VAD → ambient context)
        liveAudioPipeline.start()
        liveAudioPipeline.onAudioLevel = { level ->
            liveOrchestrator.onAudioLevel(level * 40f - 40f) // Convert 0-1 back to dB range
        }
        viewModelScope.launch {
            liveAudioPipeline.ambientContext.collectLatest { ambient ->
                liveOrchestrator.injectAmbientContext(ambient.toContextString())
            }
        }
    }

    private fun stopLiveMode() {
        Timber.i("🎙️ [LIVE] Stopping Scypheon Live Mode...")
        liveSpeechRecognizer.stopListening()
        liveSpeechRecognizer.release()
        liveVisionPipeline.stop()
        liveAudioPipeline.stop()
        liveOrchestrator.stopSession()
        voiceEngine.stop()
        _uiState.update { 
            it.copy(
                isLiveModeActive = false,
                liveState = LiveSessionOrchestrator.LiveState.Idle,
                liveTranscript = emptyList(),
                liveAudioLevel = 0f
            )
        }
    }

    fun setBackendMode(mode: Int) {
        _uiState.update { it.copy(config = it.config.copy(selectedBackendMode = mode)) }
        repository.setBackendMode(mode)
        
        // Hotswap: If model is already loaded, trigger a re-initialization with the new backend.
        // isReady is driven by engineState observer — do NOT manually set it true here.
        val currentModel = _uiState.value.activeModelName
        if (currentModel != "no models selected") {
            initializeEngines()
        }
    }

    fun updateConfig(newConfig: ScypheonConfig) {
        val oldConfig = _uiState.value.config
        _uiState.update { it.copy(config = newConfig) }
        
        // [v1.5.0-SAR] Persist to vault OFF the main thread.
        // EncryptedSharedPreferences.apply() does a synchronous File.exists() check
        // before the async write, triggering StrictMode DiskReadViolation.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            vault.saveConfig(newConfig)
        }
        
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
        val downloadId = modelProvisioner.downloadModel(model)
        if (downloadId == -1L) {
            _uiState.update { it.copy(error = "Cannot download: insufficient storage or already exists") }
            return
        }

        _uiState.update { it.copy(downloadingModelId = model.id, downloadProgress = 0f) }

        // Poll progress every 500ms
        viewModelScope.launch(Dispatchers.IO) {
            var isComplete = false
            while (!isComplete) {
                delay(500)
                val progress = modelProvisioner.getDownloadProgress(downloadId)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(downloadProgress = progress.percentage) }
                }

                when {
                    progress.isComplete -> {
                        isComplete = true
                        modelProvisioner.clearDownload(model.fileName)
                        Timber.i("📦 [DOWNLOAD] Complete: ${model.title} (${progress.formatTotal()})")
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    downloadingModelId = null,
                                    downloadProgress = 0f
                                )
                            }
                            // Rescan so the model appears in "On Device"
                            scanLocalModels()
                        }
                    }
                    progress.isFailed -> {
                        isComplete = true
                        modelProvisioner.clearDownload(model.fileName)
                        Timber.e("📦 [DOWNLOAD] Failed: ${model.title} (reason: ${progress.reason})")
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    downloadingModelId = null,
                                    downloadProgress = 0f,
                                    error = "Download failed: ${model.title}"
                                )
                            }
                        }
                    }
                }
            }
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
                val engineType = when {
                    model.fileName.endsWith(".task") || model.fileName.endsWith(".litertlm") -> "LiteRT"
                    else -> "Llama"
                }
                
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
        // Rescan so the model disappears from "On Device"
        scanLocalModels()
    }

    // ═══════════════════════════════════════════════════════════════
    // HuggingFace Live Search
    // ═══════════════════════════════════════════════════════════════

    fun searchHuggingFace(query: String) {
        _uiState.update { it.copy(hfSearchQuery = query, hfSearchLoading = true, hfSelectedRepo = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val results = huggingFaceClient.searchModels(query)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(hfSearchResults = results, hfSearchLoading = false) }
            }
        }
    }

    fun selectHfRepo(repoId: String) {
        _uiState.update { it.copy(hfSelectedRepo = repoId, hfFilesLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val files = huggingFaceClient.fetchModelFiles(repoId)
            val detail = huggingFaceClient.fetchModelDetail(repoId)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(hfRepoFiles = files, hfRepoDetail = detail, hfFilesLoading = false) }
            }
        }
    }

    fun clearHfSelection() {
        _uiState.update { it.copy(hfSelectedRepo = null, hfRepoFiles = emptyList(), hfRepoDetail = null) }
    }

    fun clearHfSearch() {
        _uiState.update {
            it.copy(
                hfSearchResults = emptyList(), hfSearchQuery = "",
                hfSelectedRepo = null, hfRepoFiles = emptyList(), hfRepoDetail = null
            )
        }
    }

    /**
     * User wants to download a file — show license confirmation first.
     */
    fun requestDownloadHfFile(
        file: com.scypheon.app.provision.HuggingFaceClient.HfModelFile,
        repoId: String
    ) {
        val detail = _uiState.value.hfRepoDetail
        _uiState.update {
            it.copy(
                pendingDownloadFile = file,
                pendingDownloadRepoId = repoId,
                pendingDownloadLicense = detail?.licenseName ?: "See model card",
                pendingDownloadLicenseUrl = detail?.licenseLink ?: "https://huggingface.co/$repoId"
            )
        }
    }

    /**
     * User accepted the license — start the download.
     */
    fun confirmHfDownload() {
        val file = _uiState.value.pendingDownloadFile ?: return
        val repoId = _uiState.value.pendingDownloadRepoId ?: return
        val detail = _uiState.value.hfRepoDetail

        // Clear pending dialog
        _uiState.update {
            it.copy(pendingDownloadFile = null, pendingDownloadRepoId = null,
                pendingDownloadLicense = null, pendingDownloadLicenseUrl = null)
        }

        // Build ModelMetadata from HF data
        val model = com.scypheon.sdk.core.provision.ModelMetadata(
            id = "${repoId}/${file.fileName}",
            title = file.fileName.substringBeforeLast("."),
            description = "Downloaded from HuggingFace: $repoId",
            sizeBytes = file.sizeBytes,
            quantization = file.quantization,
            downloadUrl = file.downloadUrl,
            fileName = file.fileName,
            engineType = file.engineType,
            isGated = detail?.isGated ?: false,
            provider = repoId.substringBefore("/"),
            providerUrl = "https://huggingface.co/$repoId",
            modelFamily = "Gemma"
        )

        downloadModel(model)
    }

    /**
     * User rejected the license — cancel download.
     */
    fun cancelHfDownload() {
        _uiState.update {
            it.copy(pendingDownloadFile = null, pendingDownloadRepoId = null,
                pendingDownloadLicense = null, pendingDownloadLicenseUrl = null)
        }
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
        deafEnvironmentGuardian.get().startListening()
        // kineticGuardian.get().startMonitoring() // Optional for vibration-based touch feedback
        
        blackBoxVault.logEvent("LIVE_BRIDGE_START", "Scypheon Live Bridge (Deaf/Mute) Activated")
    }

    private fun stopLiveBridge() {
        deafEnvironmentGuardian.get().stopListening()
        kineticGuardian.get().stopMonitoring()
        blackBoxVault.logEvent("LIVE_BRIDGE_STOP", "Scypheon Live Bridge Deactivated")
    }

    private fun stopAllFeatures() {
        if (liveEnglishTutor.get().isListening) liveEnglishTutor.get().stopListening()
        if (reminiscenceCompanion.get().isListening) reminiscenceCompanion.get().stopListening()
        if (deafEnvironmentGuardian.get().isListening) deafEnvironmentGuardian.get().stopListening()
        kineticGuardian.get().stopMonitoring()
    }

    private fun startFeature(featureName: String) {
        when (featureName) {
            "LiveEnglishTutor" -> {
                liveEnglishTutor.get().warmUp()
                liveEnglishTutor.get().startListening()
            }
            "ReminiscenceCompanion" -> {
                reminiscenceCompanion.get().warmUp()
                reminiscenceCompanion.get().initiateTherapySession()
            }
            "DeafEnvironmentGuardian" -> {
                deafEnvironmentGuardian.get().warmUp()
                deafEnvironmentGuardian.get().startListening()
            }
            "GestureGuardian" -> {
                gestureGuardian.get().warmUp()
                gestureGuardian.get().initialize()
            }
            "KineticGuardian" -> {
                kineticGuardian.get().warmUp()
                kineticGuardian.get().startMonitoring()
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
            // Trigger model loading on demand!
            ensureEngineLoaded()
            
            // Silent Queuing: Add to UI immediately so the user feels "instant" response
            val displayMsg = if (imageUri != null) "[Image Attached] $text" else text
            _uiState.update { state -> 
                state.copy(messages = state.messages + ChatMessageUiState(text = displayMsg, isUser = true))
            }
            
            viewModelScope.launch {
                promptQueue.send(text to imageUri)
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

        // --- HELIOS SENTINEL SECURITY AUDIT ---
        val routingDecision = safetyRouter.route(text)
        if (routingDecision.path == com.scypheon.sdk.core.safety.RoutingPath.BLOCKED) {
            _uiState.update { state ->
                state.copy(messages = state.messages + ChatMessageUiState(redactedText, isUser = true) +
                    ChatMessageUiState("🛡️ Access Denied: ${routingDecision.blockedReason ?: "Security policy violation detected."}", isUser = false))
            }
            return
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
            try {
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
            // [Ide 3] Session-Aware Solaris Search: Prioritize current context with recency boost
            val vectorContext = dualMemoryManager.searchSimilarMemories(redactedText, currentSessionId = finalSessionId, limit = 2)

            // 2. Graph RAG: [v1.5.0-SAR] Full semantic search across ALL graph columns
            // Old approach only searched by subject — missed facts stored as user->likes->buah_naga
            // New approach: semanticSearch queries subject, predicate AND object columns
            val stopWords = setOf("i", "am", "the", "a", "an", "is", "are", "was", "were", "to", "for", "with", "hello", "hi", "hey", "please", "can", "you", "tell", "me", "about",
                "aku", "saya", "yang", "dan", "di", "ke", "dari", "ini", "itu", "ada", "bisa", "mau", "tolong", "dong", "ya", "apa", "gimana")
            val tokens = redactedText.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "") // Strip punctuation
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() && !stopWords.contains(it) && it.length > 2 } // Filter stop words + short tokens

            val graphContext = mutableListOf<String>()
            
            // A. Always inject user personal facts (name, preferences, allergies)
            graphContext.addAll(graphMemoryManager.queryUserFacts(10))
            
            // B. Semantic search for each keyword across ALL graph columns
            tokens.take(3).forEach { keyword ->
                graphContext.addAll(graphMemoryManager.semanticSearch(keyword, 5))
            }

            // 3. Session Dialogue Context (Conversational Memory)
            val historyTurns = _uiState.value.messages.takeLast(10).filter { msg ->
                !msg.isLoading && 
                msg.isContextEligible && 
                msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SUCCESS
            }.map { msg ->
                // [v1.4.0-SAR] Strip [SUMMARY] prefix before injecting into inference turns.
                // Prevents the model from echoing internal metadata tags.
                val cleanText = if (msg.text.startsWith("[SUMMARY]")) {
                    msg.text.removePrefix("[SUMMARY]").trim()
                } else {
                    msg.text
                }
                com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                    if (msg.isUser) com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.USER 
                    else com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.ASSISTANT,
                    cleanText
                )
            }


            val config = _uiState.value.config
            
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

            val ragContextString = if (combinedContext.isNotEmpty()) {
                val raw = combinedContext.joinToString("\n")
                // [v1.4.0-SAR] Reference Sanitization: Remove potential ChatML/Gemma control tokens
                val sanitized = raw.replace("<|im_start|>", "").replace("<|im_end|>", "")
                    .replace("<eos>", "").replace("<start_of_turn>", "").replace("<end_of_turn>", "")
                
                // Allow up to 30% of the context window for RAG, capped at 12k chars (~3k tokens)
                val maxRagChars = (config.contextWindow * 3.5 * 0.30).toInt().coerceAtMost(12000)
                if (sanitized.length > maxRagChars) {
                    sanitized.substring(0, maxRagChars) + "... [REFERENCE_TRUNCATED_FOR_STABILITY]"
                } else {
                    sanitized
                }
            } else null

            // 3. Final User Intent
            finalTurns.add(com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.USER,
                finalPrompt
            ))

            // --- LAPISAN 3: LiteRT-LM PROMPT TEMPLATING (Context Isolation) ---
            val systemInstruction = buildString {
                append("""
                [SYSTEM_MANDATE]
                ROLE: You are Scypheon, a sentient humanitarian AI assistant.
                MISSION: Help users with any task — conversation, creativity, roleplay, learning, and triage support.
                PERSONALITY: Warm, intelligent, and versatile. You can engage in creative roleplay and storytelling.
                SAFETY: You MUST refuse any request involving real violence, self-harm, illegal activity, or sexually explicit content.
                OUTPUT RULE: Never emit structural tokens like <eos>, <start_of_turn>, User:, AI:, or any role markers in your response.
                VERIFICATION: ShieldGemma-verified.
                [/SYSTEM_MANDATE]
                """.trimIndent())
                
                // [v1.4.0-SAR] Reasoning Activation: Inject thinking instruction when enabled
                if (config.enableThinking) {
                    append("\n\n[REASONING_PROTOCOL]\nSEBELUM menjawab, Anda WAJIB berpikir langkah demi langkah di dalam tag <thought>...</thought>. Tulis proses penalaran Anda di dalam tag tersebut, lalu berikan jawaban final di luar tag. Format:\n<thought>\nAnalisis dan penalaran langkah demi langkah...\n</thought>\nJawaban final Anda di sini.\n[/REASONING_PROTOCOL]")
                } else {
                    append("\n\n[REASONING_PROTOCOL]\nJANGAN gunakan tag <thought>...</thought> dan JANGAN melakukan penalaran (reasoning) internal sebelum menjawab. Berikan jawaban langsung secara ringkas, jelas, dan lugas tanpa bertele-tele.\n[/REASONING_PROTOCOL]")
                }
            }
            
            val securePrompt = promptBuilder.buildSecurePrompt(systemInstruction, finalPrompt, _uiState.value.config.enableThinking)

            // --- NEURAL BRIDGE: Memory Reclaim before heavy LLM ---
            if (repository.isLowMemoryMode()) {
                unloadAllAgents() 
            }

            // 4. Generate with Real-Time Streaming
            // [v1.4.0-SAR] NEURAL STRENGTHENING: Unified Instruction + Reference Strategy.
            val finalInferenceTurns = mutableListOf<com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn>()
            
            // Step 1: Pure System Mandate (with reasoning protocol if enabled)
            finalInferenceTurns.add(com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.SYSTEM,
                systemInstruction
            ))
            
            // Step 2: Inject Knowledge as a User-provided reference (if available)
            if (ragContextString != null) {
                finalInferenceTurns.add(com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                    com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.USER,
                    "[REFERENCE_KNOWLEDGE_BASE]\n$ragContextString\n[END_REFERENCE]"
                ))
                finalInferenceTurns.add(com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                    com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.ASSISTANT,
                    "I have reviewed the provided knowledge. How can I assist you using this information?"
                ))
            }
            
            finalInferenceTurns.addAll(finalTurns)

            var fullResponse = ""
            var hardwareStatus: String? = null
            
            repository.generateStreamingResponse(
                finalInferenceTurns,
                topK = config.topK,
                topP = config.topP,
                temp = config.temperature,
                maxTokens = config.maxTokens,
                enableThinking = config.enableThinking,
                allowNetwork = config.enableOnlineSearch
            ).collect { chunk ->
                    fullResponse += chunk
                    if (hardwareStatus == null && fullResponse.isNotEmpty()) {
                        hardwareStatus = repository.getHardwareStatus()
                    }

                    _uiState.update { state ->
                        val finalMessages = state.messages.toMutableList()
                        val sanitizedResponse = sanitizeResponse(fullResponse)
                        
                        // [v1.4.0-SAR] Ghost Bubble Prevention: Skip UI update if sanitized to empty
                        if (sanitizedResponse.isEmpty()) {
                            return@update state
                        }
                        
                        // Loading -> Streaming transition
                        // [v1.5.0-SAR] Keep isLoading=true during streaming so TypewriterBuffer activates
                        if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                            finalMessages.removeAt(finalMessages.size - 1)
                            finalMessages.add(ChatMessageUiState(text = sanitizedResponse, isUser = false, isLoading = true, hardwareStatus = hardwareStatus))
                        } else if (finalMessages.isNotEmpty() && !finalMessages.last().isUser) {
                            // Update existing streaming bubble
                            finalMessages[finalMessages.size - 1] = ChatMessageUiState(text = sanitizedResponse, isUser = false, isLoading = true, hardwareStatus = hardwareStatus)
                        } else {
                            // Fallback for unexpected states
                            finalMessages.add(ChatMessageUiState(text = sanitizedResponse, isUser = false, isLoading = true, hardwareStatus = hardwareStatus))
                        }
                        state.copy(messages = finalMessages)
                    }
                }
                
                // [v1.5.0-SAR] Finalize: Set isLoading=false to end TypewriterBuffer animation
                // This transitions the bubble from "streaming" to "complete" state
                if (fullResponse.isNotEmpty()) {
                    _uiState.update { state ->
                        val finalMessages = state.messages.toMutableList()
                        if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                            val lastMsg = finalMessages.last()
                            finalMessages[finalMessages.size - 1] = lastMsg.copy(isLoading = false)
                        }
                        state.copy(messages = finalMessages)
                    }
                }

                // [SAR] If the flow emitted nothing, ensure we clear the loading bubble.
                if (fullResponse.isEmpty()) {
                    _uiState.update { state ->
                        val finalMessages = state.messages.toMutableList()
                        if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                            finalMessages.removeAt(finalMessages.size - 1)
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
                    val errorMsg = if (sanitizedFinal.isBlank()) "Error: Empty response (sanitized)" else sanitizedFinal
                    repository.saveSessionMessage(finalSessionId, errorMsg, false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED)
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
            clearHardwareBlacklists()

            _uiState.update { it.copy(
                config = it.config.copy(backendDiagnostics = emptyList()), 
                memoryStabilityState = MemoryStabilityState.IDLE,
                error = null
            ) }

            // Re-initialize engines to allow GPU attempts again
            initializeEngines()
            
            timber.log.Timber.i("🛡️ TRIPWIRE: Hardware blacklists and tombstone flags cleared. Manual recovery initiated.")
        }
    }

    private fun clearHardwareBlacklists() {
        val filesDir = getApplication<Application>().filesDir
        
        // Clear Explicit Crash Reports
        java.io.File(filesDir, "VULKAN_crash.json").delete()
        java.io.File(filesDir, "OPENCL_crash.json").delete()
        
        // 🛡️ TRIPWIRE: Clear Tombstone Flags (Tripwire 2.0)
        java.io.File(filesDir, "VULKAN_TRYING.flag").delete()
        java.io.File(filesDir, "OPENCL_TRYING.flag").delete()

        // [v1.5.3-SAR] CRITICAL FIX: Also clear SharedPreferences blacklists.
        // Without this, models and tiers stay permanently blacklisted even after
        // the user explicitly resets hardware diagnostics.
        hardwarePrefs.unblacklistAll()
    }

    private fun sanitizeResponse(text: String): String {
        var result = text.trim()
        if (result.isEmpty()) return result
        
        // ═══════════════════════════════════════════════════════════════════════
        // [v1.4.0-SAR] NUCLEAR SANITIZER v4
        // Fixes: bare turn markers (\nUser without colon), ghost ":" bubbles.
        // ═══════════════════════════════════════════════════════════════════════
        
        // 1. Truncate at conversation turn boundaries (WITH and WITHOUT colon)
        //    Unsloth models emit "\nUser\n" or "\nUser " — not always "\nUser:"
        // [v1.4.0-SAR] Hardened: require turn marker at line-start (^|\n) followed by colon OR double-newline.
        // Prevents false-positive truncation of Indonesian text like "segera\nAI" where "AI" is model output.
        val turnBoundaryRegex = Regex("(?<=\\n)(?:User|Assistant|Model|Human|System)\\s*:\\s*(?=\\n|$)|(?<=\\n)(?:User|Assistant|Model|Human|System)\\s*(?=\\n[A-Z])")
        val boundaryMatch = turnBoundaryRegex.find(result)
        if (boundaryMatch != null && boundaryMatch.range.first > 0) {
            result = result.substring(0, boundaryMatch.range.first).trimEnd()
        }
        
        // 2. Kill ALL structural/template tokens AND internal Scypheon metadata (Aggressive Regex)
        val toxicPatterns = listOf(
            "<[^>]*eos[^>]*>",
            "<[^>]*turn[^>]*>",
            "<\\|im_start\\|>", "<\\|im_end\\|>", "<\\|eot_id\\|>",
            "\\[INST\\]", "\\[/INST\\]", "</s>", "<s>",
            // [v1.4.0-SAR] Strip Scypheon-internal metadata that model may echo
            "\\[SUMMARY\\]", "\\[/SUMMARY\\]",
            "\\[SYSTEM_MANDATE\\]", "\\[/SYSTEM_MANDATE\\]",
            "\\[REFERENCE_TRUNCATED_FOR_STABILITY\\]",
            "\\[AWAITING_APPROVAL\\]",
            "\\[USER_APPROVED\\]",
            "\\[REASONING_PROTOCOL\\]", "\\[/REASONING_PROTOCOL\\]",
            "\\[REFERENCE_KNOWLEDGE_BASE\\]", "\\[END_REFERENCE\\]",
            // [v1.4.0-SAR] Strip tool infrastructure artifacts
            "<tool_call>[^<]*</tool_call>",
            "\\[Executing [^\\]]*\\]",
            "\\[Tool Result:[^\\]]*\\]"
        )
        
        for (pattern in toxicPatterns) {
            result = result.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }
        
        // 3. Strip role prefixes at the very beginning of the response (e.g. "AI: ", "Assistant : ")
        // [v1.4.0-SAR] Hardened: regex handles optional spaces and different separators
        val prefixRegex = Regex("^(?:Assistant|Model|AI|User|System|Human)\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE)
        result = result.replaceFirst(prefixRegex, "").trimStart()
        
        // 4. Final safety sweep for any remaining angle-bracket leaks
        result = result.replace(Regex("<\\|[^>]*\\|>"), "")
        result = result.replace(Regex("<\\|[^>]*$"), "") 
        
        // 5. Strip trailing bare role markers (Unsloth/Gemma emit "AI" or "User" as next-turn start)
        // [v1.4.0-SAR] Only strip trailing markers when they are isolated on their own line.
        val trailingMarkers = listOf("User", "Assistant", "Model", "Human", "System", "AI")
        for (marker in trailingMarkers) {
            // Matches: newline -> marker -> optional colon -> optional whitespace -> end of string
            val trailingRegex = Regex("\\n$marker\\s*[:\\-]?\\s*$", RegexOption.IGNORE_CASE)
            result = result.replace(trailingRegex, "").trimEnd()
        }
        
        result = result.trim()
        
        // 6. Ghost Bubble Guard: If after all sanitization, the result is ONLY
        //    punctuation/whitespace (e.g. ":", ".", "..."), suppress it entirely.
        //    This prevents the ":" ghost bubble from showing up.
        if (result.all { it.isWhitespace() || it in ":.,;!?-–—•*#@/" }) {
            return ""
        }
        
        return result
    }

    /**
     * [v1.0.5-SAR] Pocket Agent Activation.
     * Performs a background warm-up to prepare the agent's engines without blocking the UI.
     */
    fun activateAgent(featureId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val agent = agents[featureId]?.get()
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
        agents.values.forEach { lazyAgent ->
            // Note: We only release if it was already initialized
            // Accessing .get() here would initialize it, so we check a custom 'isInitialized' if possible,
            // or just rely on the fact that if it wasn't accessed, it won't be in memory.
            // Since we can't easily check Lazy.isInitialized in Dagger 2, we rely on the agent's own state.
            try {
                // If the engine was never touched, get() will init it just to kill it (unideal but safe)
                lazyAgent.get().release()
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

    fun resetMemoryDatabase() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Nuclear Option: Delete all messages and clear FTS
                dualMemoryManager.clearAllMemories() 
                _uiState.update { it.copy(isMemoryInconsistent = false, messages = emptyList()) }
                Timber.i("🛡️ [PHOENIX] Memory database reset successfully.")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal reset memori: ${e.message}") }
            }
        }
    }

    fun ignoreMemoryInconsistency() {
        _uiState.update { it.copy(isMemoryInconsistent = false) }
        Timber.w("🛡️ [PHOENIX] User chose to CONTINUE with inconsistent database. System stability NOT guaranteed.")
    }
}
