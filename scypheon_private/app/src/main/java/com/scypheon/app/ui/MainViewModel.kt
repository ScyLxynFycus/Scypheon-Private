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
import com.scypheon.sdk.live.core.model.LiveState
import com.scypheon.sdk.core.live.ContinuousSpeechRecognizer
import com.scypheon.sdk.core.live.LiveVisionPipeline
import com.scypheon.sdk.core.live.LiveAudioPipeline
import com.scypheon.sdk.core.memory.Session
import com.scypheon.sdk.core.memory.ChatMessage
import com.scypheon.sdk.core.engine.InitializationState
import com.scypheon.sdk.core.security.AuditLogEntry
import android.net.Uri
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import com.scypheon.sdk.core.humanitarian.accessibility.GestureGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.KineticGuardian
import com.scypheon.sdk.core.model.ScypheonConfig
import com.scypheon.sdk.core.model.ScypheonBackendDiagnostic
import timber.log.Timber
import com.scypheon.app.domain.usecase.GetInferenceStreamUseCase
import com.scypheon.app.domain.usecase.InferenceEvent
import com.scypheon.app.domain.usecase.ManageResourceReclamationUseCase
import com.scypheon.app.domain.usecase.ChatSessionUseCase
import com.scypheon.app.domain.usecase.ModelManagementUseCase
import javax.inject.Inject
import kotlinx.coroutines.withContext

const val NO_MODEL_SELECTED = "no models selected"

data class ChatMessageUiState(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false,
    val thinkingText: String? = null,
    val hardwareStatus: String? = null,
    val source: String? = null, // e.g. "Clinical Database", "Neural Vault"
    val imageUri: android.net.Uri? = null,
    val status: Int = 0, // STATUS_SUCCESS
    val isContextEligible: Boolean = true,
    val disclaimerType: String? = null, // "MEDICAL" or "EDUCATION", null if none
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
    val activeModelName: String = NO_MODEL_SELECTED,
    val activeEngineType: String? = null,
    val hfToken: String = "",
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    val isDownloadPaused: Boolean = false,
    
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
    val liveState: LiveState = LiveState.Idle,
    val liveTranscript: List<com.scypheon.sdk.live.core.model.TranscriptEntry> = emptyList(),
    val liveAudioLevel: Float = 0f,
    
    // Waveform Animation Phase
    val voiceAmplitude: Float = 0f,
    
    // System Health / Diagnostics
    val systemHealth: SystemHealth? = null,
    val isSystemHealthVisible: Boolean = false,
    val systemWarning: String? = null,
    val showNoModelWarningDialog: Boolean = false,
    
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
    val isMemoryInconsistent: Boolean = false,
    
    // Dynamic Context Scaling
    val pendingContextScalingTokens: Int? = null,
    val pendingContextScalingReqRamMb: Long = 0L,
    val isRamCriticalForScaling: Boolean = false,
    
    // Live Tutor & Canvas DSL State
    val activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType = com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL,
    val canvasDsl: String? = null
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
    private val chatSessionUseCase: ChatSessionUseCase,
    private val liveEnglishTutor: dagger.Lazy<LiveEnglishTutor>,
    private val reminiscenceCompanion: dagger.Lazy<ReminiscenceCompanion>,
    private val deafEnvironmentGuardian: dagger.Lazy<DeafEnvironmentGuardian>,
    private val gestureGuardian: dagger.Lazy<GestureGuardian>,
    private val kineticGuardian: dagger.Lazy<KineticGuardian>,
    private val blackBoxVault: BlackBoxVault,
    private val graphMemoryManager: GraphMemoryManager,
    private val modelManagementUseCase: ModelManagementUseCase,
    private val vault: com.scypheon.sdk.core.security.AegisVault,
    private val sensoryHooks: com.scypheon.sdk.core.gateway.SensoryHooks,
    private val hardwarePrefs: com.scypheon.app.data.local.HardwarePreferences,
    private val thermalGovernor: com.scypheon.sdk.core.resilience.AegisThermalGovernor,
    private val promptBuilder: com.scypheon.sdk.core.safety.helios.PromptBuilder,
    private val safetyRouter: com.scypheon.sdk.core.safety.SafetyRouter,
    private val safetySeeder: com.scypheon.sdk.core.safety.helios.SafetyRuleSeeder,
    private val toolGateway: com.scypheon.sdk.core.safety.helios.ToolAuthorizationGateway,
    private val liveStateMachine: com.scypheon.sdk.live.core.domain.LiveStateMachine,
    private val safetyTrustLayer: com.scypheon.sdk.live.safety.SafetyTrustLayer,
    val liveSpeechRecognizer: ContinuousSpeechRecognizer,
    val liveVisionPipeline: LiveVisionPipeline,
    val liveAudioPipeline: LiveAudioPipeline,
    private val intentRouter: com.scypheon.sdk.core.agent.SkillIntentRouter,
    private val getInferenceStreamUseCase: GetInferenceStreamUseCase,
    private val manageResourceReclamationUseCase: ManageResourceReclamationUseCase,
    private val hardwareLeakDetector: com.scypheon.sdk.core.telemetry.HardwareLeakDetector
) : AndroidViewModel(application) {

    private val voiceEngine = com.scypheon.sdk.core.voice.AegisVoiceEngine(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pendingModelFile: java.io.File? = null

    private val promptQueue = kotlinx.coroutines.channels.Channel<Pair<String, Uri?>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var accumulatedSpeechText = ""
    internal var inferenceJob: kotlinx.coroutines.Job? = null
    internal var ttsJob: kotlinx.coroutines.Job? = null
    private val transcriptMutex = Mutex()

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
                    activeModelName = bestModel?.name?.let { "$it (STANDBY)" } ?: NO_MODEL_SELECTED,
                    isReady = true
                ) 
            }
            
            resumeLastSessionOrStandby()
            
            // [MEMORY GUARD] Concurrent purge of corrupted engine error messages.
            try {
                chatSessionUseCase.purgeEngineErrorMessages()
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
                    
                    val logEntry = "🛡️ ALERT: Sandbox Process CRASHED. Waiting for manual recovery..."
                    _uiState.update { s -> 
                        s.copy(
                            isSandboxAlive = false,
                            isAiGenerating = false,
                            error = "System Anomaly: The AI core has crashed. Please restart the engine manually.",
                            memoryStabilityState = MemoryStabilityState.CRASHED,
                            diagnosticLogs = s.diagnosticLogs + logEntry
                        )
                    }
                    
                    // Auto-restart is now disabled by default to prevent continuous crash loops.
                    // It will only be triggered as a fallback if manual restart fails.
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
                    
                    if (currentName == NO_MODEL_SELECTED) {
                        withContext(Dispatchers.IO) {
                            val model = hardwarePrefs.resolveBestFittingModel()
                            model?.let { m ->
                                _uiState.update { it.copy(activeModelName = m.name) }
                            }
                        }
                    }
                    
                    // Auto-start live mode if it was pending
                    if (_uiState.value.isLiveModeActive && _uiState.value.liveState is com.scypheon.sdk.live.core.model.LiveState.Idle) {
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
                        // [v1.5.4-SAR] UI PERSISTENCE GUARD:
                        // Find the message we already added to the UI in sendMessage()
                        // and update it to 'Processing...' isLoading state instead of adding a new one.
                        _uiState.update { s ->
                            val updatedMessages = s.messages.toMutableList()
                            val existingIdx = updatedMessages.indexOfLast { it.text == prompt.first && it.isUser }
                            if (existingIdx != -1) {
                                // Add a 'Processing...' bubble right after the user message if it's missing
                                if (existingIdx == updatedMessages.lastIndex || !updatedMessages[existingIdx + 1].isLoading) {
                                    updatedMessages.add(existingIdx + 1, ChatMessageUiState(text = "Processing...", isUser = false, isLoading = true))
                                }
                            }
                            s.copy(messages = updatedMessages)
                        }
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
                val currentVersion = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo)
                
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
                        
                        // [v1.5.4-SAR] UI PERSISTENCE GUARD: Resume session BEFORE setting isReady = true.
                        // If we set isReady first, a user might send a message while resumeLastSession
                        // is still running on IO, leading to the session load overwriting the new message.
                        resumeLastSessionOrStandby()

                        _uiState.update { 
                            it.copy(
                                activeModelName = bestModel.name,
                                activeEngineType = if (hwStatus.contains("NPU") || hwStatus.contains("LiteRT")) "LiteRT" else "Llama",
                                isReady = true
                            ) 
                        }
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

    fun dismissNoModelWarning() {
        _uiState.update { it.copy(showNoModelWarningDialog = false) }
    }

    fun triggerNoModelWarning() {
        _uiState.update { it.copy(showNoModelWarningDialog = true) }
    }

    fun isModelDownloadingOrPaused(fileName: String): Boolean {
        return modelManagementUseCase.isModelDownloadingOrPaused(fileName)
    }

    fun getCustomDownloadProgress(fileName: String): com.scypheon.sdk.core.provision.ModelProvisioner.DownloadProgress? {
        return modelManagementUseCase.getCustomDownloadProgress(fileName)
    }

    private fun resumeLastSessionOrStandby() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = chatSessionUseCase.getAllSessions()
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
            val sessions = chatSessionUseCase.getAllSessions()
            _uiState.update { it.copy(sessionHistory = sessions) }
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbMessages = chatSessionUseCase.getMessagesForSession(sessionId)
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
        _uiState.update {
            it.copy(
                isReady = true,
                currentSessionId = sessionId,
                messages = emptyList()
            )
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
            val rawGraph = chatSessionUseCase.getRawKnowledgeGraph()
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
        if (_uiState.value.activeModelName == NO_MODEL_SELECTED) {
            triggerNoModelWarning()
            return
        }
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
            is com.scypheon.sdk.live.core.model.LiveState.Idle -> {
                startLiveMode()
            }
            is com.scypheon.sdk.live.core.model.LiveState.Listening, is com.scypheon.sdk.live.core.model.LiveState.UserSpeaking -> {
                viewModelScope.launch {
                    val textToSubmit = sanitizeSpeechInput(accumulatedSpeechText)
                    accumulatedSpeechText = ""
                    if (textToSubmit.isNotBlank()) {
                        appendToTranscript(com.scypheon.sdk.live.core.model.TranscriptEntry(
                            text = textToSubmit,
                            isUser = true,
                            timestamp = System.currentTimeMillis()
                        ))
                        onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.SpeechCompleted(textToSubmit, java.util.UUID.randomUUID().toString()))
                    } else {
                        onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.PartialSpeechDetected(""))
                        liveSpeechRecognizer.startListening()
                    }
                }
            }
            is com.scypheon.sdk.live.core.model.LiveState.Speaking, is com.scypheon.sdk.live.core.model.LiveState.Thinking -> {
                voiceEngine.stop()
                onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.UserInterrupted(""))
            }
            is com.scypheon.sdk.live.core.model.LiveState.SafetyBlocked, is com.scypheon.sdk.live.core.model.LiveState.Interrupted, is com.scypheon.sdk.live.core.model.LiveState.Degraded -> {
                onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.StopSession(""))
            }
            else -> {}
        }
    }

    internal fun sanitizeSpeechInput(text: String, locale: Locale = Locale.getDefault()): String {
        // 1. Remove bracketed noise markers (ASR artifacts)
        val noBrackets = text.replace(Regex("\\[[^\\]]*\\]|\\([^)]*\\)"), " ")
        
        // 2. Locale-aware filler word removal (only if isolated, not substring)
        val fillers = when (locale.language) {
            "en" -> "\\b(um|uh|er|ah|hm|hmm|like|you\\s*know|I\\s*mean)\\b"
            "id" -> "\\b(um|anu|gitu|loh|deh|sih)\\b" // "eh" excluded to preserve "eh saya mau tanya"
            "ja" -> "\\b(ano|eto|maa)\\b"             // "eh" excluded
            "es" -> "\\b(uh|er|eh)\\b"                // "um" excluded
            else -> "\\b(um|uh|er|eh)\\b" // Conservative fallback
        }
        val noFillers = noBrackets.replace(Regex(fillers, RegexOption.IGNORE_CASE), " ")
        
        // 3. Remove repeated phonetic stuttering (e.g., "ha ha ha", "ho ho ho")
        // Only if 3+ repetitions of 1-2 char syllables
        val noStutter = noFillers.replace(Regex("\\b([a-z]{1,2})\\s+\\1\\s+\\1+\\b", RegexOption.IGNORE_CASE), "$1")
        
        // 4. Collapse whitespace and trim
        return noStutter.replace(Regex("\\s+"), " ").trim()
    }

    private suspend fun appendToTranscript(entry: com.scypheon.sdk.live.core.model.TranscriptEntry) {
        transcriptMutex.withLock {
            _uiState.update { current ->
                current.copy(liveTranscript = current.liveTranscript + entry)
            }
        }
    }

    internal suspend fun hardCancelLiveSession() {
        // 1. Cancel inference coroutine immediately
        inferenceJob?.cancelAndJoin() // Join ensures completion of cancellation
        
        // 2. Cancel tts job immediately
        ttsJob?.cancelAndJoin()

        // 3. Stop TTS immediately (bypass queue)
        voiceEngine.stop() // Must be synchronous or use callback to confirm stop
        
        // 4. Cancel speech recognizer to prevent pending results
        liveSpeechRecognizer.cancel()
        
        // 5. Clear all pending state
        _uiState.update { current ->
            current.copy(
                liveTranscript = emptyList(),
                liveAudioLevel = 0f,
                liveState = com.scypheon.sdk.live.core.model.LiveState.Idle,
                canvasDsl = null,
                activeSkillType = com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL
            )
        }
        accumulatedSpeechText = ""
    }

    internal fun startLiveMode() {
        Timber.i("🎙️ [LIVE] Starting Scypheon Live Mode...")
        _uiState.update { it.copy(
            isLiveModeActive = true,
            liveTranscript = emptyList(),
            canvasDsl = null,
            activeSkillType = com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL
        ) }

        val llamaReady = _uiState.value.engineState is InitializationState.Success
        if (!llamaReady) {
            ensureEngineLoaded()
            return 
        }

        startLiveSessionMvi()
        
        liveSpeechRecognizer.initialize()
        accumulatedSpeechText = ""
        liveSpeechRecognizer.onPartialResult = { partial ->
            val sanitized = sanitizeSpeechInput(partial)
            accumulatedSpeechText = sanitized
            onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.PartialSpeechDetected(sanitized))
        }
        liveSpeechRecognizer.onFinalResult = { finalText ->
            viewModelScope.launch {
                val sanitized = sanitizeSpeechInput(finalText)
                if (sanitized.isNotBlank()) {
                    val traceId = java.util.UUID.randomUUID().toString()
                    appendToTranscript(com.scypheon.sdk.live.core.model.TranscriptEntry(
                        text = sanitized,
                        isUser = true,
                        timestamp = System.currentTimeMillis()
                    ))
                    accumulatedSpeechText = sanitized
                    onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.SpeechCompleted(sanitized, traceId))
                } else {
                    accumulatedSpeechText = ""
                    onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.PartialSpeechDetected(""))
                    liveSpeechRecognizer.startListening()
                }
            }
        }
        liveSpeechRecognizer.onRmsChanged = { rmsDb ->
            onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.AudioLevelChanged(rmsDb))
        }
        liveSpeechRecognizer.onError = { error ->
            Timber.w("🎤 [STT] Error in live mode: $error")
            if (_uiState.value.liveState is com.scypheon.sdk.live.core.model.LiveState.UserSpeaking) {
                liveSpeechRecognizer.startListening()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            liveVisionPipeline.initializeDetector()
            liveVisionPipeline.onSceneUpdated = { scene ->
                Timber.i("👁️ [VISION] Scene updated: ${scene.objectSummary}")
            }
            liveVisionPipeline.onKeyframeCaptured = { bitmap ->
                Timber.d("👁️ [VISION] Keyframe captured: ${bitmap.width}x${bitmap.height}")
            }
        }
<<<<<<< Updated upstream

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
        // [v1.5.1-SAR] Offload detector init to IO thread to prevent StrictMode DiskReadViolation
        // (native lib loading + getCacheDir() both perform disk I/O)
        viewModelScope.launch(Dispatchers.IO) {
            liveVisionPipeline.initializeDetector()
            // Wire callbacks after init completes (thread-safe: callbacks are invoked from analysis executor)
            liveVisionPipeline.onSceneUpdated = { scene ->
                liveOrchestrator.injectVisionContext(scene.toContextString())
            }
            liveVisionPipeline.onKeyframeCaptured = { bitmap ->
                liveOrchestrator.injectCameraFrame(bitmap)
            }
        }
        // Camera will be started from the UI (needs LifecycleOwner)

        // 7. Start Audio Pipeline (continuous mic → VAD → ambient context)
=======
        
>>>>>>> Stashed changes
        liveAudioPipeline.start()
        liveAudioPipeline.onAudioLevel = { level ->
            onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.AudioLevelChanged(level * 40f - 40f))
        }
    }

    private fun stopLiveMode() {
        Timber.i("🎙️ [LIVE] Stopping Scypheon Live Mode...")
        viewModelScope.launch {
            hardCancelLiveSession()
            liveSpeechRecognizer.release()
            liveVisionPipeline.stop()
            liveAudioPipeline.stop()
            _uiState.update { it.copy(isLiveModeActive = false) }
        }
    }

    fun setBackendMode(mode: Int) {
        _uiState.update { it.copy(config = it.config.copy(selectedBackendMode = mode)) }
        repository.setBackendMode(mode)
        
        // Hotswap: If model is already loaded, trigger a re-initialization with the new backend.
        // isReady is driven by engineState observer — do NOT manually set it true here.
        val currentModel = _uiState.value.activeModelName
        if (currentModel != NO_MODEL_SELECTED) {
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
<<<<<<< Updated upstream
        if (!modelProvisioner.hasSufficientSpace(model.sizeBytes)) {
=======
        if (!modelManagementUseCase.hasSufficientSpace(model.sizeBytes)) {
>>>>>>> Stashed changes
            _uiState.update { it.copy(error = "Cannot download: insufficient storage") }
            return
        }

        _uiState.update { it.copy(downloadingModelId = model.id, downloadProgress = 0f, isDownloadPaused = false) }

<<<<<<< Updated upstream
        modelProvisioner.resumeDownload(model) { progress ->
=======
        modelManagementUseCase.resumeDownload(model) { progress ->
>>>>>>> Stashed changes
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update { it.copy(downloadProgress = progress.percentage) }
                
                if (progress.isComplete) {
                    _uiState.update { it.copy(downloadingModelId = null, downloadProgress = 0f, isDownloadPaused = false) }
                    scanLocalModels()
                    Timber.i("📦 [DOWNLOAD] Complete: ${model.title}")
                } else if (progress.isFailed) {
                    _uiState.update { it.copy(downloadingModelId = null, isDownloadPaused = false, error = "Download failed: ${model.title}") }
                }
            }
        }
    }

    fun pauseModelDownload(model: com.scypheon.sdk.core.provision.ModelMetadata) {
<<<<<<< Updated upstream
        modelProvisioner.pauseDownload(model.fileName)
=======
        modelManagementUseCase.pauseDownload(model.fileName)
>>>>>>> Stashed changes
        _uiState.update { it.copy(isDownloadPaused = true) }
    }

    fun pauseCurrentDownload() {
        val downloadingId = _uiState.value.downloadingModelId ?: return
        
        // If it's already paused, we resume it
        if (_uiState.value.isDownloadPaused) {
            // Find model to resume
            val model = com.scypheon.sdk.core.provision.ModelHubSource.recommendedModels.find { it.id == downloadingId }
            if (model != null) {
                downloadModel(model)
            } else {
                // For HF models, they are triggered via confirmHfDownload
                // which uses pendingDownloadFile. If we still have it, we can resume.
                // Or just use the repoId and fileName from the downloadingId string
                if (downloadingId.contains("/")) {
                    val repoId = downloadingId.substringBeforeLast("/")
                    val fileName = downloadingId.substringAfterLast("/")
                    // To resume HF, we'd need to recreate the ModelMetadata
                    // For now, let's assume it's Recommended models only or 
                    // user can re-click the file in HF browser.
                }
            }
        } else {
            // Pause
            val model = com.scypheon.sdk.core.provision.ModelHubSource.recommendedModels.find { it.id == downloadingId }
            if (model != null) {
                pauseModelDownload(model)
            } else {
                // HF generic pause
                if (downloadingId.contains("/")) {
                    val fileName = downloadingId.substringAfterLast("/")
<<<<<<< Updated upstream
                    modelProvisioner.pauseDownload(fileName)
=======
                    modelManagementUseCase.pauseDownload(fileName)
>>>>>>> Stashed changes
                    _uiState.update { it.copy(isDownloadPaused = true) }
                }
            }
        }
    }

    fun cancelModelDownload(model: com.scypheon.sdk.core.provision.ModelMetadata) {
<<<<<<< Updated upstream
        modelProvisioner.pauseDownload(model.fileName)
        modelProvisioner.deleteModel(model.fileName)
=======
        modelManagementUseCase.pauseDownload(model.fileName)
        modelManagementUseCase.deleteModel(model.fileName)
>>>>>>> Stashed changes
        _uiState.update { it.copy(downloadingModelId = null, downloadProgress = 0f) }
        Timber.i("📦 [DOWNLOAD] Cancelled and deleted: ${model.title}")
    }

    fun cancelCurrentDownload() {
        val downloadingId = _uiState.value.downloadingModelId ?: return
        
        // We need the fileName to cancel in SDK. 
        // We can find it from recommendedModels or HF selection.
        // Or we can just use the currentDownloadId if we expose it in SDK.
        
        // [v1.5.3] For now, try to find in recommended models
        val model = com.scypheon.sdk.core.provision.ModelHubSource.recommendedModels.find { it.id == downloadingId }
        if (model != null) {
            cancelModelDownload(model)
        } else {
            // Fallback for HF downloads: currentDownloadId is stored
            currentDownloadId?.let { id ->
                // SDK change: I should add cancelById
                viewModelScope.launch(Dispatchers.IO) {
                    // Instead of finding fileName, let's just use the currentDownloadId directly if we can
                    // But I've already added cancelDownload(fileName) to SDK.
                    
                    // Actually, if it's from HF, the ID in uiState is repo/fileName
                    if (downloadingId.contains("/")) {
                        val fileName = downloadingId.substringAfterLast("/")
<<<<<<< Updated upstream
                        modelProvisioner.cancelDownload(fileName)
=======
                        modelManagementUseCase.cancelDownload(fileName)
>>>>>>> Stashed changes
                        currentDownloadId = null
                        _uiState.update { it.copy(downloadingModelId = null, downloadProgress = 0f) }
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
            val modelFile = modelManagementUseCase.getModelPath(model.fileName)
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

    fun isModelDownloaded(fileName: String): Boolean = modelManagementUseCase.isModelOnDisk(fileName)

    fun deleteModel(fileName: String) {
        modelManagementUseCase.deleteModel(fileName)
        // Rescan so the model disappears from "On Device"
        scanLocalModels()
    }

    // ═══════════════════════════════════════════════════════════════
    // HuggingFace Live Search
    // ═══════════════════════════════════════════════════════════════

    fun searchHuggingFace(query: String) {
        _uiState.update { it.copy(hfSearchQuery = query, hfSearchLoading = true, hfSelectedRepo = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val results = modelManagementUseCase.searchModels(query)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(hfSearchResults = results, hfSearchLoading = false) }
            }
        }
    }

    fun selectHfRepo(repoId: String) {
        _uiState.update { it.copy(hfSelectedRepo = repoId, hfFilesLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val files = modelManagementUseCase.fetchModelFiles(repoId)
            val detail = modelManagementUseCase.fetchModelDetail(repoId)
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

        if (_uiState.value.activeModelName == NO_MODEL_SELECTED) {
            if (_uiState.value.config.localModels.isEmpty()) {
                triggerNoModelWarning()
            } else {
                showLocalModelPicker()
            }
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
        val modelPath = modelManagementUseCase.getModelPath("gesture_recognizer.task").absolutePath
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
    private var currentDownloadId: Long? = null
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

        if (_uiState.value.activeModelName == NO_MODEL_SELECTED) {
            if (_uiState.value.config.localModels.isEmpty()) {
                triggerNoModelWarning()
            } else {
                _uiState.update { state ->
                    state.copy(
                        error = "No models found. Please download or select a model to start the session."
                    )
                }
                showLocalModelPicker()
            }
            return false
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
                state.copy(messages = state.messages + ChatMessageUiState(text = displayMsg, isUser = true, imageUri = imageUri))
            }
            
            viewModelScope.launch {
                promptQueue.send(text to imageUri)
            }
            return true
        }

        executeInference(text, imageUri, addUserMessage = true)
        return true
    }

    fun approveContextScaling(targetTokens: Int) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    config = state.config.copy(contextWindow = targetTokens),
                    pendingContextScalingTokens = null,
                    isAiGenerating = true
                )
            }
            // Hardware-aware engine restart with new context limit
            val newConfig = _uiState.value.config
            repository.initializeEngines(getApplication(), nCtx = targetTokens)
            
            // Re-trigger inference
            executeInference(isRetry = true)
        }
    }
    
    fun rejectContextScaling(proceedWithTruncation: Boolean) {
        if (proceedWithTruncation) {
            // Bypass the check by setting pending tokens to -1 (dummy value indicating bypassed)
            _uiState.update { it.copy(pendingContextScalingTokens = -1, isAiGenerating = true) }
            executeInference(isRetry = true)
        } else {
            // Abort entirely
            _uiState.update { state -> 
                val newMessages = state.messages.filter { !it.isLoading }
                state.copy(
                    pendingContextScalingTokens = null,
                    messages = newMessages,
                    isAiGenerating = false
                )
            }
        }
    }

        private fun executeInference(
        text: String = "",
        imageUri: android.net.Uri? = null,
        addUserMessage: Boolean = true,
        isRetry: Boolean = false
    ) {
        if (_uiState.value.currentSessionId.isEmpty()) {
            val newId = "session_${System.currentTimeMillis()}"
            _uiState.update { it.copy(currentSessionId = newId) }
        }

        val finalSessionId = _uiState.value.currentSessionId

        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            getInferenceStreamUseCase(
                text = text,
                imageUri = imageUri,
                sessionId = finalSessionId,
                config = _uiState.value.config,
                chatHistory = _uiState.value.messages,
                isRetry = isRetry
            ).collect { event ->
                when (event) {
                    is InferenceEvent.SecurityBlocked -> {
                        _uiState.update { state ->
                            val filteredMessages = state.messages.filter { !it.isLoading }
                            val hasUserMsg = filteredMessages.isNotEmpty() && filteredMessages.last().isUser
                            val newMessages = if (hasUserMsg) {
                                filteredMessages
                            } else {
                                filteredMessages + ChatMessageUiState(event.redactedText, isUser = true)
                            }
                            state.copy(
                                messages = newMessages + ChatMessageUiState("🛡️ Access Denied: ${event.reason}", isUser = false),
                                isAiGenerating = false
                            )
                        }
                        chatSessionUseCase.saveSessionMessage(finalSessionId, "🛡️ Access Denied: ${event.reason}", false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED)
                    }
                    is InferenceEvent.PuppetMasterIntercept -> {
                        val puppetMasterIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        puppetMasterIntent.data = android.net.Uri.parse("market://search?q=${event.target}")
                        puppetMasterIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

                        val intentResultMsg = try {
                            getApplication<android.app.Application>().startActivity(puppetMasterIntent)
                            "PuppetMaster: Executing automation for '${event.target}' via DeepLink Intent."
                        } catch (e: Exception) {
                            "PuppetMaster: Failed to execute automation for '${event.target}'."
                        }
                        _uiState.update { state ->
                            val displayMsg = if (imageUri != null) "[Image Attached] ${event.redactedText}" else event.redactedText
                            state.copy(messages = state.messages +
                                ChatMessageUiState(text = displayMsg, isUser = true, imageUri = imageUri) +
                                ChatMessageUiState(text = intentResultMsg, isUser = false))
                        }
                    }
                    is InferenceEvent.Initialized -> {
                        val displayMsg = if (imageUri != null) "[Image Attached] ${event.redactedText}" else event.redactedText
                        _uiState.update { state ->
                            val newMessages = if (addUserMessage) {
                                state.messages +
                                ChatMessageUiState(text = displayMsg, isUser = true, imageUri = imageUri) +
                                ChatMessageUiState(text = "Processing...", isUser = false, isLoading = true)
                            } else if (event.isRetry) {
                                val filtered = state.messages.filter { it.status != com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED && it.status != com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SYSTEM }
                                filtered + ChatMessageUiState(text = "Processing...", isUser = false, isLoading = true)
                            } else {
                                state.messages + ChatMessageUiState(text = "Processing...", isUser = false, isLoading = true)
                            }
                            state.copy(messages = newMessages, isAiGenerating = true)
                        }

                        val history = chatSessionUseCase.getAllSessions()
                        if (history.none { it.id == finalSessionId }) {
                            chatSessionUseCase.createNewSession(finalSessionId, "New Session")
                        }
                        if (addUserMessage) {
                            chatSessionUseCase.saveSessionMessage(finalSessionId, displayMsg, true)
                        }
                        if (_uiState.value.messages.size == 2) {
                            val title = if (text.length > 20) text.substring(0, 20) + "..." else text
                            chatSessionUseCase.updateSessionTitle(finalSessionId, title)
                            loadSessionHistory()
                        }
                    }
                    is InferenceEvent.ScalingRequired -> {
                        _uiState.update { state ->
                            state.copy(
                                isAiGenerating = false,
                                pendingContextScalingTokens = event.tokens,
                                pendingContextScalingReqRamMb = event.reqRamMb,
                                isRamCriticalForScaling = event.isCritical
                            )
                        }
                        timber.log.Timber.w("[SCALING] Prompt exceeded context window.")
                    }
                    is InferenceEvent.ThinkingChunk -> {
                        _uiState.update { state ->
                            val finalMessages = state.messages.toMutableList()
                            if (finalMessages.isNotEmpty() && !finalMessages.last().isUser) {
                                val lastMsg = finalMessages.last()
                                val newThinkingText = (lastMsg.thinkingText ?: "") + event.text
                                finalMessages[finalMessages.size - 1] = lastMsg.copy(
                                    thinkingText = newThinkingText,
                                    text = if (lastMsg.text == "Processing...") "Thinking..." else lastMsg.text,
                                    isLoading = true
                                )
                            }
                            state.copy(messages = finalMessages)
                        }
                    }
                    is InferenceEvent.Chunk -> {
                        _uiState.update { state ->
                            val finalMessages = state.messages.toMutableList()
                            val sanitizedChunk = sanitizeResponse(event.text)
                            
                            if (finalMessages.isNotEmpty() && !finalMessages.last().isUser) {
                                val lastMsg = finalMessages.last()
                                // If we were showing "Thinking..." and now we have real content, 
                                // we replace "Thinking..." with the first chunk of real text.
                                val baseText = if (lastMsg.text == "Processing..." || lastMsg.text == "Thinking...") "" else lastMsg.text
                                finalMessages[finalMessages.size - 1] = lastMsg.copy(
                                    text = baseText + sanitizedChunk,
                                    isLoading = true,
                                    hardwareStatus = event.hardwareStatus,
                                    disclaimerType = event.disclaimerType
                                )
                            } else {
                                finalMessages.add(ChatMessageUiState(
                                    text = sanitizedChunk,
                                    isUser = false,
                                    isLoading = true,
                                    hardwareStatus = event.hardwareStatus,
                                    disclaimerType = event.disclaimerType
                                ))
                            }
                            state.copy(messages = finalMessages)
                        }
                    }
                    is InferenceEvent.Success -> {
                        val sanitizedFinal = sanitizeResponse(event.fullResponse)
                        _uiState.update { state ->
                            val finalMessages = state.messages.toMutableList()
                            if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                                val lastMsg = finalMessages.last()
                                finalMessages[finalMessages.size - 1] = lastMsg.copy(
                                    text = sanitizedFinal,
                                    isLoading = false
                                )
                                if (event.fullResponse.isEmpty()) finalMessages.removeAt(finalMessages.size - 1)
                            }
                            state.copy(messages = finalMessages, isAiGenerating = false)
                        }
                        
                        if (!event.isEngineError) {
                            chatSessionUseCase.saveSessionMessage(finalSessionId, sanitizedFinal, false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SUCCESS)
                            if (_uiState.value.isLiveModeActive) voiceEngine.speak(sanitizedFinal)
                        } else {
                            val errorMsg = if (sanitizedFinal.isBlank()) "Error: Empty response" else sanitizedFinal
                            chatSessionUseCase.saveSessionMessage(finalSessionId, errorMsg, false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED)
                        }
                        chatSessionUseCase.checkAndSummarizeSessionAsync(finalSessionId)
                    }
                    is InferenceEvent.Error -> {
                         chatSessionUseCase.saveSessionMessage(finalSessionId, "Error: ${event.message}", false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED)
                         _uiState.update { state ->
                             val finalMessages = state.messages.toMutableList()
                             if (finalMessages.isNotEmpty() && finalMessages.last().isLoading) {
                                 finalMessages.removeAt(finalMessages.size - 1)
                             }
                             finalMessages.add(ChatMessageUiState(text = "Error: ${event.message}", isUser = false, status = com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED))
                             state.copy(messages = finalMessages, isAiGenerating = false)
                         }
                    }
                }
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
            
            // Fallback: If sandbox fails to restart manually after 10 seconds, try a forceful auto-reboot.
            delay(10000)
            if (!_uiState.value.isSandboxAlive) {
                timber.log.Timber.w("🔥 [PHOENIX] Manual restart failed to recover sandbox. Initiating fallback automatic reboot...")
                rebootEngine()
            }
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
            "\\[Tool Result:[^\\]]*\\]",
            "<(graph|geometry)\\b[^>]*?>",
            "</(graph|geometry)>"
        )
        
        for (pattern in toxicPatterns) {
            result = result.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }
        
        // 3. Strip role prefixes at the very beginning of the response (e.g. "AI: ", "Assistant : ")
        // [v1.4.0-SAR] Hardened: regex handles optional spaces and different separators
        val prefixRegex = Regex("^(?:Assistant|Model|AI|User|System|Human)\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE)
        result = result.replaceFirst(prefixRegex, "").trimStart()
        
        // 4. [v1.6.0-SAR] REASONING BLOCK SUPPRESSION (Zero-Leakage Protocol)
        // Aggressively remove both complete and unclosed thought blocks to prevent
        // reasoning text from flickering/leaking into the chat bubble during generation.
        result = result.replace(Regex("<thought>.*?</thought>", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("<thought>.*$", RegexOption.DOT_MATCHES_ALL), "")
        
        // 5. Final safety sweep for any remaining angle-bracket leaks (ChatML, etc.)
        result = result.replace(Regex("<\\|[^>]*\\|>"), "")
        result = result.replace(Regex("<\\|[^>]*$"), "") 
        
        // 6. Strip trailing bare role markers (Unsloth/Gemma emit "AI" or "User" as next-turn start)
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
        timber.log.Timber.d("🧹 [MainViewModel] onCleared triggered. Cleaning up Live Mode pipelines.")
        try {
            liveAudioPipeline.close()
            liveVisionPipeline.close()
            hardwareLeakDetector.auditHardwareState("MainViewModel.onCleared")
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error closing Live Mode pipelines")
        }
    }

    fun resetMemoryDatabase() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Nuclear Option: Delete all messages and clear FTS
                chatSessionUseCase.clearAllMemories() 
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

    // ═══════════════════════════════════════════════════════════════════
    // LIVE MODE: MVI Pipeline (Hardened Resilience Phase 1)
    // ═══════════════════════════════════════════════════════════════════
    
    fun startLiveSessionMvi() {
        val traceId = java.util.UUID.randomUUID().toString()
        Timber.i("🎙️ [LIVE] Session Started [TraceId: $traceId]")
        onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.StartSession(traceId))
    }

    fun onLiveIntent(intent: com.scypheon.sdk.live.core.model.LiveIntent) {
        if (intent is com.scypheon.sdk.live.core.model.LiveIntent.AudioLevelChanged) {
            val normalized = ((intent.level + 45f) / 55f).coerceIn(0f, 1f)
            _uiState.update { it.copy(liveAudioLevel = normalized) }
        }

        when (intent) {
            is com.scypheon.sdk.live.core.model.LiveIntent.StartSession -> {
                _uiState.update { it.copy(canvasDsl = null, activeSkillType = com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL) }
            }
            is com.scypheon.sdk.live.core.model.LiveIntent.PartialSpeechDetected -> {
                val currentState = _uiState.value.liveState
                if (currentState is com.scypheon.sdk.live.core.model.LiveState.Speaking || 
                    currentState is com.scypheon.sdk.live.core.model.LiveState.Thinking ||
                    _uiState.value.canvasDsl != null
                ) {
                    _uiState.update { it.copy(canvasDsl = null, activeSkillType = com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL) }
                }
            }
            is com.scypheon.sdk.live.core.model.LiveIntent.UserInterrupted -> {
                _uiState.update { it.copy(canvasDsl = null, activeSkillType = com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL) }
            }
            is com.scypheon.sdk.live.core.model.LiveIntent.SpeechCompleted -> {
                val routingResult = intentRouter.routeMissionSync(intent.text)
                val resolvedSkill = routingResult.second.maxByOrNull { it.value }?.key ?: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL
                _uiState.update { it.copy(activeSkillType = resolvedSkill) }
            }
            else -> {}
        }

        val (newState, sideEffects) = liveStateMachine.reduce(_uiState.value.liveState, intent)
        
        if (newState != _uiState.value.liveState) {
            _uiState.update { it.copy(liveState = newState) }
        }

        sideEffects.forEach { effect ->
            handleLiveSideEffect(effect)
        }
    }

    private fun handleLiveSideEffect(effect: com.scypheon.sdk.live.core.model.SideEffect) {
        when (effect) {
            is com.scypheon.sdk.live.core.model.SideEffect.EvaluateSafety -> {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val decision = safetyTrustLayer.evaluatePrompt(effect.text, effect.traceId)
                    if (decision.blocked) {
                        onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.SafetyCheckFailed(decision))
                    } else {
                        handleLiveSideEffect(com.scypheon.sdk.live.core.model.SideEffect.RunInference(effect.text, effect.traceId))
                    }
                }
            }
            is com.scypheon.sdk.live.core.model.SideEffect.StartListening -> {
                Timber.d("🎙️ [MVI SideEffect] -> StartListening")
                liveAudioPipeline.start()
            }
            is com.scypheon.sdk.live.core.model.SideEffect.StopListening -> {
                Timber.d("🎙️ [MVI SideEffect] -> StopListening")
                viewModelScope.launch(Dispatchers.IO) {
                    liveAudioPipeline.stop()
                }
            }
            is com.scypheon.sdk.live.core.model.SideEffect.SpeakResponse -> {
                Timber.d("🎙️ [MVI SideEffect] -> SpeakResponse: ${effect.text}")
                ttsJob?.cancel()
                ttsJob = viewModelScope.launch(Dispatchers.Main) {
                    try {
                        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                            voiceEngine.speak(effect.text) {
                                if (continuation.isActive) {
                                    continuation.resumeWith(kotlin.Result.success(Unit))
                                }
                            }
                            continuation.invokeOnCancellation {
                                voiceEngine.stop()
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "TTS failed")
                    } finally {
                        onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.TtsCompleted(effect.utteranceId))
                    }
                }
            }
            is com.scypheon.sdk.live.core.model.SideEffect.StopSpeaking -> {
                Timber.d("🎙️ [MVI SideEffect] -> StopSpeaking")
                viewModelScope.launch {
                    ttsJob?.cancelAndJoin()
                    inferenceJob?.cancelAndJoin()
                    voiceEngine.stop()
                    onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.TtsCompleted(effect.utteranceId))
                }
            }
            is com.scypheon.sdk.live.core.model.SideEffect.RunInference -> {
                Timber.d("🎙️ [MVI SideEffect] -> RunInference: ${effect.text}")
                val currentConfig = _uiState.value.config
                inferenceJob?.cancel()
                inferenceJob = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val currentTranscript = _uiState.value.liveTranscript
                        val turns = currentTranscript.map { entry ->
                            val role = if (entry.isUser) {
                                com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.USER
                            } else {
                                com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.ASSISTANT
                            }
                            com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(role, entry.text)
                        }
                        
                        val finalInferenceTurns = mutableListOf<com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn>()
                        val systemPrompt = """
                            [SYSTEM_MANDATE]
                            ROLE: You are Scypheon, a sentient humanitarian AI assistant.
                            MISSION: Help users with any task — conversation, creativity, roleplay, learning, and triage support.
                            PERSONALITY: Warm, intelligent, and versatile. You can engage in creative roleplay and storytelling.
                            SAFETY: You MUST refuse any request involving real violence, self-harm, illegal activity, or sexually explicit content.
                            OUTPUT RULE: Never emit structural tokens like <eos>, <start_of_turn>, User:, AI:, or any role markers in your response.
                            LANGUAGE RULE: Always respond in the same language as the user's query (e.g., if the user asks in English, reply in English; if the user asks in Indonesian, reply in Indonesian).
                            VERIFICATION: ShieldGemma-verified.
                            [/SYSTEM_MANDATE]
                        """.trimIndent()
                        
                        finalInferenceTurns.add(com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn(
                            com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.SYSTEM,
                            systemPrompt
                        ))
                        finalInferenceTurns.addAll(turns)
                        
                        var fullResponse = ""
                        repository.generateStreamingResponse(
                            finalInferenceTurns,
                            topK = currentConfig.topK,
                            topP = currentConfig.topP,
                            temp = currentConfig.temperature,
                            maxTokens = currentConfig.maxTokens,
                            enableThinking = currentConfig.enableThinking,
                            allowNetwork = currentConfig.enableOnlineSearch
                        ).collect { chunk ->
                            fullResponse += chunk
                            val dslMatch = Regex("<(graph|geometry)\\b[^>]*?(?:/>|>)").find(fullResponse)
                            if (dslMatch != null) {
                                val matchedTag = dslMatch.value
                                if (_uiState.value.canvasDsl != matchedTag) {
                                    _uiState.update { it.copy(canvasDsl = matchedTag) }
                                }
                            }
                        }
                        
                        val sanitizedResponse = sanitizeResponse(fullResponse)
                        appendToTranscript(com.scypheon.sdk.live.core.model.TranscriptEntry(
                            text = sanitizedResponse,
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        ))
                        onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.InferenceCompleted(sanitizedResponse, effect.traceId))
                    } catch (e: Exception) {
                        Timber.e(e, "Live inference failed")
                        onLiveIntent(com.scypheon.sdk.live.core.model.LiveIntent.InferenceFailed(e.message ?: "Unknown error", effect.traceId))
                    }
                }
            }
            is com.scypheon.sdk.live.core.model.SideEffect.LogAudit -> {}
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                chatSessionUseCase.deleteSession(sessionId)
                loadSessionHistory()
                if (_uiState.value.currentSessionId == sessionId) {
                    startNewSession()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete session: $sessionId")
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                chatSessionUseCase.archiveSession(sessionId)
                loadSessionHistory()
                if (_uiState.value.currentSessionId == sessionId) {
                    resumeLastSessionOrStandby()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to archive session: $sessionId")
            }
        }
    }

    fun unarchiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                chatSessionUseCase.unarchiveSession(sessionId)
                loadSessionHistory()
            } catch (e: Exception) {
                Timber.e(e, "Failed to unarchive session: $sessionId")
            }
        }
    }

    fun setActiveSkillType(skillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType) {
        _uiState.update { it.copy(activeSkillType = skillType) }
    }

}
