package com.scypheon.app.data.repository

import android.content.Context
import android.app.ActivityManager
import com.scypheon.app.data.models.SystemHealth
import com.scypheon.app.data.models.OomDiagnostic
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.memory.IVectorEngine
import com.scypheon.sdk.core.memory.VectorEngineRouter
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.memory.ScypheonDbHelper
import com.scypheon.sdk.core.utils.AssetExtractor
import com.scypheon.sdk.core.utils.MemoryGatekeeper
import com.scypheon.sdk.core.utils.Result
import com.scypheon.sdk.core.utils.SystemVitals
import com.scypheon.sdk.core.engine.InitializationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import android.os.Trace
import android.content.Intent
import android.os.Build
import com.scypheon.sdk.core.utils.NativeLibraryLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import com.google.gson.Gson

@Singleton
class ScypheonRepository @Inject constructor(
    private val gateway: NeuralGateway,
    private val memoryManager: DualMemoryManager,
    private val vectorEngine: IVectorEngine,
    private val blackBoxVault: BlackBoxVault,
    @ApplicationContext private val context: Context,
    private val hardwarePrefs: com.scypheon.app.data.local.HardwarePreferences,
    private val intentRouter: com.scypheon.sdk.core.agent.SkillIntentRouter,
    private val orchestrator: com.scypheon.sdk.core.agent.skills.AgenticSkillOrchestrator
) {
    // SAR PHASE 3: SHM & Recovery State
    private val replayBuffer = com.scypheon.sdk.core.utils.ContextReplayBuffer()
    private var currentTensorSize: Long = 0
    private var lastTensorsHash: String = ""

    init {
        memoryManager.setGateway(gateway)
    }

    private val cacheDir by lazy { context.cacheDir.absolutePath }
    private val sessionCheckpointPath by lazy { "$cacheDir/last_session.gguf.state" }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _engineState = MutableStateFlow<InitializationState>(InitializationState.Idle)
    val engineState = _engineState.asStateFlow()

    //  SOLARIS STATE MACHINE
    enum class TriageState { IDLE, RUNNING, READY, FAILED }
    private val triageState = AtomicReference(TriageState.IDLE)
    private val stateLock = Mutex()
    private val tombstoneLock = ReentrantLock()
    private val tombstoneFile by lazy { File(context.filesDir, "SANDBOX_TOMBSTONE.json") }

    private val _oomDiagnostic = MutableStateFlow<OomDiagnostic?>(null)
    val oomDiagnostic = _oomDiagnostic.asStateFlow()

    val processHealth = gateway.processHealth

    fun isLowMemoryMode(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.lowMemory
    }

    private val _memoryOptimizationActive = MutableStateFlow(false)
    val memoryOptimizationActive = _memoryOptimizationActive.asStateFlow()

    val vectorEngineState = vectorEngine.state

    fun getMemoryReport(context: Context): MemoryGatekeeper.MemoryReport {
        return MemoryGatekeeper.performPreflightCheck(context, currentTensorSize)
    }

    suspend fun promoteSandboxToForeground(): Boolean {
        return gateway.promoteToForeground()
    }

    private var pendingWarning: String? = null

    fun getPendingInitializationWarning(): String? = pendingWarning
    private fun clearPendingWarning() { pendingWarning = null }

    suspend fun checkSystemHealth(context: Context): SystemHealth = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val registry = AssetExtractor.discoverModels(context)

        val elitePath = registry.eliteModel?.let { AssetExtractor.getModelPath(context, it) } ?: ""
        val universalPath = registry.universalModel?.let { AssetExtractor.getModelPath(context, it) } ?: ""
        val memoryPath = registry.memoryModel?.let { AssetExtractor.getModelPath(context, it) } ?: ""

        val isMemoryOk = memoryPath.isNotEmpty() && File(memoryPath).exists()
        val isEliteOk = elitePath.isNotEmpty() && File(elitePath).exists()
        val isUniversalOk = universalPath.isNotEmpty() && File(universalPath).exists()
        val isPiggybacking = !isMemoryOk && isUniversalOk

        SystemHealth(
            ramUsedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024),
            ramTotalMb = memInfo.totalMem / (1024 * 1024),
            isLowMemory = memInfo.lowMemory,
            isMemoryOk = isMemoryOk,
            isPiggybacking = isPiggybacking,
            isEliteOk = isEliteOk,
            isUniversalOk = isUniversalOk,
            elitePath = elitePath,
            universalPath = universalPath,
            memoryPath = memoryPath,
            modelName = if (universalPath.isNotEmpty()) File(universalPath).name else "None",
            backend = getHardwareStatus()
        )
    }

    suspend fun initializeEngines(
        context: Context,
        customElitePath: String? = null,
        customUniversalPath: String? = null,
        nCtx: Int = 4096
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        stateLock.withLock {
            Trace.beginSection("solaris_initialize")
            val startTime = System.currentTimeMillis()
            try {
            triageState.set(TriageState.RUNNING)
            _engineState.emit(InitializationState.Analyzing("Waking Neural Gateway..."))

            //  [SBI] Step 0: Dynamic Discovery & Pre-Flight Validation
            val registry = AssetExtractor.discoverModels(context)
            if (!ensureAssetsReady(context, registry)) {
                Timber.e(" [SBI] Dynamic asset validation failed.")
                return@withLock Result.Error(Exception("Missing or corrupt model assets"))
            }

            val health = checkSystemHealth(context)

            // --- TIER 1: ELITE ENGINE (LiteRT) ---
            if (customElitePath != null) {
                _engineState.emit(InitializationState.Analyzing("Initializing Elite Engine..."))
<<<<<<< Updated upstream
                
                // [PHOENIX] Cross-Engine Unloading: Ensure Llama is unloaded to free up RAM/VRAM for LiteRT
                gateway.releaseLlama()
                
                val loadSuccess = gateway.initializeLiteRt(customElitePath, nCtx)
                return@withLock if (loadSuccess) {
=======

                // [PHOENIX] Cross-Engine Unloading: Ensure Llama is unloaded to free up RAM/VRAM for LiteRT
                gateway.releaseLlama()

                val eliteLoadSuccess = try {
                    gateway.initializeLiteRt(customElitePath, nCtx)
                } catch (e: Exception) {
                    Timber.e(e, "CRITICAL: LiteRT-LM JNI/Native Crash during init.")
                    false
                }

                if (eliteLoadSuccess) {
>>>>>>> Stashed changes
                    triageState.set(TriageState.READY)
                    scope.launch {
                        initializeEmbedder(registry, health)
                    }
                    _engineState.emit(InitializationState.Success(gateway.getHardwareStatus()))
                    return@withLock Result.Success(true)
                } else {
                    // CASCADING FALLBACK: Elite Engine failed. Attempting Universal Sandbox.
                    Timber.w("🛡️ [PHOENIX] Elite Engine failed. Attempting Cascading Fallback to Universal Sandbox...")
                    if (!health.isUniversalOk && customUniversalPath == null) {
                        triageState.set(TriageState.FAILED)
                        _engineState.emit(InitializationState.Failed("CRITICAL", "Elite Engine failed and no Universal fallback found."))
                        return@withLock Result.Error(Exception("Failed to initialize LiteRT-LM and no Universal fallback available"))
                    }
                    // Continue to Universal loading logic
                    _engineState.emit(InitializationState.Analyzing("Falling back to Universal Sandbox..."))
                }
            }

            // --- TIER 2: UNIVERSAL ENGINE (Llama Sandbox) ---
            val finalUniversalPath = customUniversalPath ?: health.universalPath
            if (finalUniversalPath.isEmpty() || !File(finalUniversalPath).exists()) {
                return@withLock Result.Error(Exception("Universal model not found at $finalUniversalPath"))
            }

            val modelSize = File(finalUniversalPath).length()
<<<<<<< Updated upstream
            
            // [PHOENIX] Cross-Engine Unloading: Ensure LiteRT is unloaded to free up RAM for Llama
            gateway.releaseLiteRt()
            
=======

            // [PHOENIX] Cross-Engine Unloading: Ensure LiteRT is unloaded to free up RAM for Llama
            gateway.releaseLiteRt()

>>>>>>> Stashed changes
            // [v1.0.5-SAR] Strict Memory Enforcement
            if (!MemoryGatekeeper.canLoadModel(context, modelSize)) {
                Timber.e(" [GUARD] Memory Gatekeeper VETO: Model too large for current available RAM.")
                _engineState.emit(InitializationState.Failed("MEMORY", "Insufficient RAM (Model + 2GB buffer required)"))
                return@withLock Result.Error(Exception("Insufficient RAM to load model safely"))
            }

            val report = MemoryGatekeeper.performPreflightCheck(context, modelSize, isCpuMode = gateway.getBackendMode() == 1)

            if (report.isVetoRequired) {
                Timber.w("[MDRS] VETO ACTIVE! RAM constraint detected. Enforcing CPU limits.")
            }

            // Solaris Protocol: Read + Consume tombstone
            val tombstone = checkHardwareTombstone()
            if (tombstone != null) {
                if (tombstone.signal == 6 || tombstone.signal == 0) { // SIGABRT (OOM) or Unexpected Death
                    Timber.e("﨟槫惺 [PHOENIX] OOM/Crash detected for model: ${tombstone.modelPath}")
                    hardwarePrefs.blacklistModel(tombstone.modelPath)

                    val modelName = File(tombstone.modelPath).name
                    _oomDiagnostic.emit(OomDiagnostic(
                        modelName = modelName,
                        requiredGB = tombstone.modelSize.toDouble() / (1024 * 1024 * 1024),
                        availableGB = tombstone.availableRam.toDouble() / (1024 * 1024 * 1024),
                        backend = tombstone.backend,
                        message = "Memory Overload Detected"
                    ))
                }
                hardwarePrefs.blacklist(getBackendCode(tombstone.backend))
            }

            val backends = when {
                report.isVetoRequired -> listOf(1)
                gateway.getBackendMode() == 0 -> listOf(2, 3, 1).filter { !hardwarePrefs.isBlacklisted(it) }
                gateway.getBackendMode() == 1 -> listOf(1)
                gateway.getBackendMode() == 2 -> listOf(2, 3, 1) // Allow fallback to OpenCL then CPU
                gateway.getBackendMode() == 3 -> listOf(3, 1)    // Allow fallback to CPU
                else -> listOf(1)
            }

            if (hardwarePrefs.isModelBlacklisted(finalUniversalPath)) {
                Timber.w(" [GUARD] Model $finalUniversalPath is blacklisted due to previous OOM. Skipping load.")
                _engineState.emit(InitializationState.Failed("MEMORY", "Model incompatible with device RAM"))
                return@withLock Result.Error(Exception("Model blacklisted due to OOM"))
            }

            // [PHOENIX] Step 1: Model Integrity Probe (Isolated Sandbox)
            // This validates GGUF structure and header before attempting driver init.
            _engineState.emit(InitializationState.Trying("PROBE", 1))
            val probeOk = gateway.probeBackend(finalUniversalPath, 1) // Always use CPU for probe

            if (!probeOk) {
                Timber.e("隨ｶ繝ｻ[PHOENIX] Integrity probe FAILED. Model file may be corrupt or too large.")
                _engineState.emit(InitializationState.Failed("CORRUPT", "Model integrity check failed"))
                return@withLock Result.Error(Exception("Model integrity check failed"))
            }
            Timber.i("隨ｨ繝ｻ[PHOENIX] Probe SUCCESS. Proceeding with hardware initialization.")

            var success = false
            var finalCtx = nCtx
            if (hardwarePrefs.isMdrsEnabled()) {
                val safeKvTokens = MemoryGatekeeper.calculateSafeKvCache(context, modelSize)
                finalCtx = minOf(nCtx, safeKvTokens)
                Timber.i("﨟樊ぞ [MDRS] Dynamic Context Scaling Applied. Requested: $nCtx -> Granted: $finalCtx")
            }

            if (hardwarePrefs.isForceDegraded()) {
                Timber.w("﨟槫惺 [SAR] Emergency Rollback: Forcing DEGRADED mode.")
                _engineState.emit(InitializationState.Failed("CRITICAL", "Emergency Rollback Active"))
                return@withLock Result.Error(Exception("Emergency Rollback Active"))
            }

            for (currentTier in backends) {
                val tierLabel = getTierName(currentTier)
                var loadSuccess = false

                for (attempt in 1..2) {
                    if (hardwarePrefs.isBlacklisted(currentTier)) {
                        Timber.w(" [PHOENIX] Skipping blacklisted tier: $tierLabel")
                        break
                    }

                    _engineState.emit(InitializationState.Trying(tierLabel, attempt))

                    // [SBI] Loading LLM on tierLabel...
                    Timber.i(" [SBI] Loading LLM on $tierLabel...")
                    currentTensorSize = modelSize

                    //  [SAR] Phase 3: Pre-allocate target SHM and apply V.I.I.P OOM protection
                    com.scypheon.sdk.core.utils.ShmLifecycleManager.acquire(context, currentTensorSize, shouldDup = false)

                    val loadResult = withContext(Dispatchers.IO) {
                        // Telemetry: Log MDRS scaling
                        val availableRam = MemoryGatekeeper.performPreflightCheck(context, 0).availableMB * 1024 * 1024
                        val requestedCtx = nCtx

                        blackBoxVault.record("mdrs_context_scaled", 1, mapOf(
                            "available_ram_mb" to (availableRam / 1024 / 1024).toString(),
                            "requested_ctx" to requestedCtx.toString(),
                            "granted_ctx" to finalCtx.toString(),
                            "quantization" to "Q4/Q8"
                        ))

                        //  [PHOENIX] Write 'LOADING' tombstone before native dispatch
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val memInfo = ActivityManager.MemoryInfo()
                        am.getMemoryInfo(memInfo)

                        writeTombstone(HardwareTombstone(
                            backend = tierLabel,
                            signal = 0, // 0 = IN_PROGRESS / LMKD Candidate
                            description = "Loading Model: ${File(finalUniversalPath).name}",
                            timestamp = System.currentTimeMillis(),
                            modelPath = finalUniversalPath,
                            modelSize = modelSize,
                            availableRam = memInfo.availMem
                        ))

                        gateway.llamaEngine.loadWithMode(finalUniversalPath, currentTier, finalCtx) { progress ->
                            scope.launch { _engineState.emit(InitializationState.Loading(tierLabel, progress)) }
                        }
                    }

                    if (loadResult && validatePostLoadStability(context)) {
                        Timber.i(" [SBI] Stabilized on $tierLabel")
                        // Clear tombstone on success
                        tombstoneLock.withLock { if (tombstoneFile.exists()) tombstoneFile.delete() }
                        loadSuccess = true
                        break
                    } else {
                        Timber.e("隨ｶ繝ｻ[PHOENIX] Load FAILED or UNSTABLE on $tierLabel. Result=$loadResult")
                        hardwarePrefs.blacklist(currentTier)

                        if (loadResult) {
                            releaseUniversalEngine()
                            kotlinx.coroutines.delay(1000L * attempt)
                        }
                    }
                }

                if (loadSuccess) {
                    success = true
                    
                    // [SBI] Step 2: Proactive wiring of Vector & Safety Embeddings
                    // We delay this briefly to ensure the LLM has finished its Prefill phase
                    // and doesn't get choked by simultaneous embedding requests.
                    scope.launch {
                        kotlinx.coroutines.delay(2000L) 
                        initializeEmbedder(registry, health)
                    }

                    triageState.set(TriageState.READY)
                    _engineState.emit(InitializationState.Success(gateway.getHardwareStatus()))
                    break
                } else {
                    hardwarePrefs.blacklist(currentTier)
                    releaseUniversalEngine()
                }
            }

            if (success) Result.Success(true)
            else Result.Error(Exception("All backends exhausted"))
        } catch (e: Exception) {
            triageState.set(TriageState.FAILED)
            _engineState.emit(InitializationState.Failed("CRITICAL", e.message ?: "Unknown error"))
            Result.Error(e)
        } finally {
            Trace.endSection()
        }
    }
}

    private suspend fun initializeEmbedder(registry: com.scypheon.sdk.core.utils.ModelRegistry, health: SystemHealth) {
        if (vectorEngine !is VectorEngineRouter) {
            vectorEngine.initialize(health.memoryPath)
            return
        }

        val memModelName = registry.memoryModel ?: ""
        val memPath = health.memoryPath.ifEmpty {
            if (memModelName.isNotEmpty()) AssetExtractor.getModelPath(context, memModelName) else ""
        }

        when {
            memModelName.isNotEmpty() && AssetExtractor.isGguf(context, memModelName) -> {
                Timber.i("[HOTSWAP] Piggybacking on Universal GGUF for embeddings.")
                vectorEngine.switchToLlamaEmbedder(null)
            }
            memPath.endsWith(".tflite") || memPath.endsWith(".litertlm") || memPath.endsWith(".bin") -> {
                Timber.i("[HOTSWAP] Routing to LiteRT embedder: $memPath")
                vectorEngine.switchToLiteRtEmbedder(memPath)
            }
            else -> {
                if (gateway.llamaEngine.isReady()) {
                    Timber.i("[HOTSWAP] No dedicated embedder. Piggybacking on Universal GGUF.")
                    vectorEngine.switchToLlamaEmbedder(null)
                } else {
                    Timber.i("[HOTSWAP] No dedicated embedder and Universal GGUF not active/ready. Falling back to LiteRT embedder.")
                    vectorEngine.switchToLiteRtEmbedder(null)
                }
            }
        }
    }


    private var lastTombstone: HardwareTombstone? = null

    fun dismissOomDiagnostic() {
        _oomDiagnostic.value = null
    }

    fun consumeLastTombstone(): HardwareTombstone? {
        val t = lastTombstone
        lastTombstone = null
        return t
    }

    // --- Fallback State Machine ---
    sealed class FallbackState {
        object Initial : FallbackState()
        data class Attempting(val tier: String, val attempt: Int) : FallbackState()
        data class Failed(val reason: String) : FallbackState()
        object Success : FallbackState()
    }

    private val _fallbackState = MutableStateFlow<FallbackState>(FallbackState.Initial)
    val fallbackState: StateFlow<FallbackState> = _fallbackState.asStateFlow()

    private fun validatePostLoadStability(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val threshold = memInfo.totalMem * 0.15
        val isStable = memInfo.availMem >= threshold

        if (!isStable) {
            Timber.w(" [SAR] Post-load memory VETO: ${memInfo.availMem / 1024 / 1024}MB available < 15% threshold.")
        }
        return isStable
    }

    private fun checkHardwareTombstone(): HardwareTombstone? {
        //  ATOMIC CONSUMPTION: Enforce single-reader via ReentrantLock
        return tombstoneLock.withLock {
            if (!tombstoneFile.exists()) return null
            try {
                val json = tombstoneFile.readText().trim()
                val gson = com.google.gson.Gson()
                val tombstone = gson.fromJson(json, HardwareTombstone::class.java)

                if (tombstone != null) {
                    tombstoneFile.delete() // Always consume if parseable

                    // [SOLARIS] VALIDITY GATE: SIG=0 or unknown backend = stale/empty tombstone.
                    // Architect Directive: Ignore SIG=0/unknown and apply 24h TTL.
                    val tsMs = if (tombstone.timestamp < 1000000000000L) tombstone.timestamp * 1000 else tombstone.timestamp
                    val ageS = (System.currentTimeMillis() - tsMs) / 1000
                    val isExpired = ageS > 24 * 60 * 60

                    // [PHOENIX] Fresh tombstone (even with SIG=0) implies a process hang/crash during init.
                    val isRecent = ageS < 60
                    val isKnownBackend = tombstone.backend.uppercase() in setOf("CPU", "VULKAN", "OPENCL")
                    val isRealCrash = (tombstone.signal > 0 || isRecent) && isKnownBackend

                    if (!isRealCrash || isExpired) {
                        Timber.w(" [SOLARIS] Tombstone discarded (stale/empty/expired): SIG=${tombstone.signal} BACKEND=${tombstone.backend} AGE=${ageS}s")
                        return null
                    }

                    Timber.w(" [PHOENIX] Processed CRITICAL Tombstone: SIG=${tombstone.signal} BACKEND=${tombstone.backend}")
                    
                    // 🛡️ Aegis/BlackBox Vault Logging
                    scope.launch {
                        blackBoxVault.record(
                            eventType = "HARDWARE_TOMBSTONE_CRASH",
                            traceId = UUID.randomUUID().toString(),
                            data = mapOf(
                                "signal" to tombstone.signal,
                                "backend" to tombstone.backend,
                                "model" to (tombstone.modelPath.takeIf { it.isNotEmpty() }?.let { File(it).name } ?: "unknown"),
                                "available_ram_mb" to (tombstone.availableRam / 1024 / 1024).toString(),
                                "reason" to "Sandbox process terminated unexpectedly (LMKD or Segfault)"
                            ),
                            severity = "CRITICAL"
                        )
                    }

                    lastTombstone = tombstone // Store for UI/Telemetry
                    return tombstone
                }
                tombstone
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse tombstone file. Purging corrupt artifact.")
                tombstoneFile.delete()
                null
            }
        }
    }

    private fun writeTombstone(tombstone: HardwareTombstone) {
        tombstoneLock.withLock {
            try {
                val json = Gson().toJson(tombstone)
                tombstoneFile.writeText(json)
            } catch (e: Exception) {
                Timber.e(e, "Failed to write hardware tombstone")
            }
        }
    }

    private fun getBackendCode(backend: String): Int = when(backend.uppercase()) {
        "CPU" -> 1
        "VULKAN" -> 2
        "OPENCL" -> 3
        else -> 1
    }

    private fun getTierName(tier: Int): String = when(tier) {
        1 -> "CPU"
        2 -> "VULKAN"
        3 -> "OPENCL"
        else -> "AUTO"
    }

    private suspend fun ensureAssetsReady(context: Context, registry: com.scypheon.sdk.core.utils.ModelRegistry): Boolean {
        return withContext(Dispatchers.IO) {
            Timber.i(" [SBI] Starting Dynamic Asset Validation...")

            val modelsToVerify = listOfNotNull(
                registry.eliteModel,
                registry.universalModel,
                registry.memoryModel
            )

            if (modelsToVerify.isEmpty()) {
                Timber.e(" [SBI] No models discovered in Assets or Downloads!")
                return@withContext false
            }

            var allOk = true
            for (filename in modelsToVerify) {
                val success = AssetExtractor.extractAndVerify(context, filename)
                if (success) {
                    Timber.d(" [SBI] Dynamic Asset Ready: $filename")
                } else {
                    Timber.e(" [SBI] Dynamic Asset FAILURE: $filename")
                    allOk = false
                }
            }
            allOk
        }
    }

    suspend fun releaseEngines() {
        withContext(Dispatchers.IO) {
            gateway.release()
        }
    }

    suspend fun releaseUniversalEngine() {
        withContext(Dispatchers.IO) {
            gateway.releaseLlama()
        }
    }

    suspend fun releaseEliteEngine() {
        withContext(Dispatchers.IO) {
            gateway.releaseLiteRt()
        }
    }

    data class HardwareTombstone(
        val backend: String,
        val signal: Int,
        val description: String,
        val timestamp: Long,
        val modelPath: String = "",
        val modelSize: Long = 0,
        val availableRam: Long = 0
    )

    fun generateStreamingResponse(
        history: List<com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn>,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        maxTokens: Int = 2048,
        enableThinking: Boolean = true,
        allowNetwork: Boolean = true
    ): kotlinx.coroutines.flow.Flow<String> {
        return if (gateway.isReady()) {
            val userQuery = history.lastOrNull { it.role == com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.USER }?.content ?: ""
            val (path, _) = intentRouter.routeMissionSync(userQuery)
            val sessionId = "active_stream_session" // Usually tracked by UI, using active string here as fallback context

            blackBoxVault.logEvent("AI_INFERENCE_STREAM", "Streaming requested. Path: $path (Turns=${history.size})")

            // [v1.4.0-SAR] UNIFIED AGENTIC PIPELINE: ALL queries go through the tool-aware
            // orchestrator. The LLM itself decides whether to invoke tools based on its own
            // reasoning 遯ｶ繝ｻjust like Claude Code, Cursor, and other agentic IDEs.
            // The SkillIntentRouter is kept for telemetry/logging only, not for gating tool access.
            val streamFlow = orchestrator.generateAgenticStream(
                sessionId, history, topK, topP, temp, maxTokens, enableThinking, allowNetwork
            )

            streamFlow
                .onEach { token ->
                    //  [SAR] Phase 3: Record tokens for possible recovery
                    // In a real implementation we would need to capture the KV offset from the stream
                    // Simulating for now using a counter as the engine doesn't yet transmit offsets per-token
                    // replayBuffer.record(tokenId, kvOffset)
                }
                .catch { e ->
                    Timber.e(e, " [PHOENIX] Inference Stream CRASHED. Initiating Solaris Recovery.")
                    val isOom = e is com.scypheon.sdk.core.gateway.PromptTooLongException
                    val crashReason = if (isOom) CrashReason.OOM_CONTEXT_OVERFLOW else CrashReason.UNKNOWN
                    
                    val crashedState = AgentState(
                        history = history,
                        physicalCtx = gateway.llamaEngine.currentLoadedCtx.let { if (it > 0) it else 4096 }
                    )
                    
                    when (val result = attemptPhase3Recovery(crashedState, crashReason)) {
                        is RecoveryResult.Success -> {
                            emit("\n\n[SYSTEM] Stream recovered from crash. Please send your message again to continue.")
                        }
                        is RecoveryResult.Abort -> {
                            emit("\n\n[SYSTEM ERROR] ${result.reason}")
                        }
                        is RecoveryResult.Retry -> {
                            throw e
                        }
                    }
                }
                .onCompletion {
                    // SAR PHASE 2: Checkpoint session after successful prompt processing
                    scope.launch {
                        val saved = gateway.llamaEngine.saveSession(sessionCheckpointPath)
                        if (saved) {
                            Timber.d(" [SAR] Session checkpoint saved to disk.")
                        }
                    }
                }
        } else {
            blackBoxVault.logEvent("AI_INFERENCE_ERROR", "Gateway not ready for streaming", "CRITICAL")
            kotlinx.coroutines.flow.flowOf("Error: AI engines failed to initialize.")
        }
    }
    fun getHardwareStatus(): String = gateway.getHardwareStatus()
    fun setBackendMode(mode: Int) = gateway.setBackendMode(mode)

    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (gateway.isReady()) {
                blackBoxVault.logEvent("AI_INFERENCE", "Neural Gateway generation requested")

                // Collect streaming response into a single string for legacy compatibility
                val builder = StringBuilder()
                gateway.routeRequest(prompt).collect { builder.append(it) }
                val fullResponse = builder.toString()

                Result.Success(fullResponse)
            } else {
                blackBoxVault.logEvent("AI_INFERENCE_ERROR", "Neural Gateway not ready", "CRITICAL")
                Result.Error(Exception("Gateway not ready"), "Error: AI engines failed to initialize.")
            }
        } catch (e: Exception) {
             blackBoxVault.logEvent("AI_INFERENCE_ERROR", "Generation failed: ${e.message}", "CRITICAL")
             Result.Error(e)
        }
    }

    suspend fun saveSessionMessage(
        sessionId: String,
        message: String,
        isUser: Boolean,
        status: Int = ScypheonDbHelper.STATUS_SUCCESS,
        isContextEligible: Boolean = true
    ) = withContext(Dispatchers.IO) {
        memoryManager.saveMessage(sessionId, message, isUser, status, isContextEligible)
    }

    suspend fun createNewSession(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        memoryManager.createSession(sessionId, title)
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        memoryManager.updateSessionTitle(sessionId, title)
    }

    suspend fun performTtlSweep(thirtyDaysAgo: Long) = withContext(Dispatchers.IO) {
        memoryManager.performTtlSweep(thirtyDaysAgo)
    }

    /**
     * MDRS: Reactive memory reclamation.
     * Orchestrates KV cache eviction across the neural pipeline when the system is under duress.
     */
    fun reclaimMemory(level: Int) {
        Timber.w(" [MDRS] Repository Memory Pressure Alarm: Level $level")
        gateway.llamaEngine.reclaimMemory(level)
    }

    suspend fun expireZombieTasks(fifteenMinsAgo: Long) = withContext(Dispatchers.IO) {
        memoryManager.expireAwaitingApprovalTasks(fifteenMinsAgo)
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        memoryManager.wipeSessionMemory(sessionId)
    }

    enum class CrashReason { OOM_CONTEXT_OVERFLOW, LMKD_KILL, SEGFAULT, UNKNOWN }

    data class AgentState(
        val history: List<com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn>,
        val physicalCtx: Int,
        val recoveryAttempts: Int = 0
    )

    sealed class RecoveryResult {
        data class Success(val state: AgentState) : RecoveryResult()
        data class Retry(val state: AgentState) : RecoveryResult()
        data class Abort(val reason: String) : RecoveryResult()
    }

    /**
     *  [SAR] Phase 3: Zero-Latency Handoff Recovery
     */
    private suspend fun attemptPhase3Recovery(
        crashedState: AgentState,
        crashReason: CrashReason
    ): RecoveryResult {
        val startTime = System.currentTimeMillis()
        Timber.w(" [SOLARIS] Emergency Sandbox Resurrection Initiated... Reason: $crashReason")

        return when (crashReason) {
            CrashReason.OOM_CONTEXT_OVERFLOW -> {
                Timber.w("[Recovery] OOM detected — forcing aggressive compact before restore")
                
                // Aggressive compact (keep only last 2 turns + system)
                val systemMessages = crashedState.history.filter { it.role == com.scypheon.sdk.core.gateway.NeuralGateway.NeuralTurn.Role.SYSTEM }
                val recentTurns = crashedState.history.takeLast(2)
                val compactedHistory = systemMessages + recentTurns
                
                val recoveredState = crashedState.copy(
                    history = compactedHistory,
                    recoveryAttempts = crashedState.recoveryAttempts + 1
                )
                
                if (recoveredState.recoveryAttempts >= 2) {
                    Timber.e("[Recovery] Max recovery attempts reached — graceful abort")
                    RecoveryResult.Abort("Multiple OOM recoveries. Please start new session.")
                } else {
                    RecoveryResult.Success(recoveredState)
                }
            }
            CrashReason.SEGFAULT, CrashReason.LMKD_KILL -> {
                RecoveryResult.Abort("Fatal engine error. Restart app.")
            }
            else -> {
                val pfd = com.scypheon.sdk.core.utils.ShmLifecycleManager.acquire(context, currentTensorSize)
                if (pfd == null) {
                    blackBoxVault.record("shm_fallback", 0, mapOf("reason" to "MEMFD_UNSUPPORTED"))
                    return RecoveryResult.Abort("MEMFD_UNSUPPORTED")
                }

                val success = pfd.use { fd ->
                    try {
                        val attached = gateway.attachTensorMemory(fd, currentTensorSize, lastTensorsHash)
                        if (!attached) return@use false
                        val snapshots = replayBuffer.snapshot()
                        if (snapshots.isNotEmpty()) {
                            gateway.nativeKvRestore(0, replayBuffer.lastPos())
                            snapshots.forEach {
                                gateway.injectToken(it.tokenId, it.kvOffset, it.sequenceNumber)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                if (success) {
                    val latency = System.currentTimeMillis() - startTime
                    blackBoxVault.record("crash_to_ready_ready_ms", latency)
                    RecoveryResult.Success(crashedState)
                } else {
                    RecoveryResult.Abort("SHM Attachment Failed")
                }
            }
        }
    }
}
