package com.scypheon.app.data.repository

import android.content.Context
import android.app.ActivityManager
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
import com.scypheon.sdk.core.utils.SolarisTelemetry
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
    private val hardwarePrefs: com.scypheon.sdk.core.utils.HardwarePreferences
) {
    // SAR PHASE 3: SHM & Recovery State
    private val replayBuffer = com.scypheon.sdk.core.utils.ContextReplayBuffer()
    private var currentTensorSize: Long = 0
    private var lastTensorsHash: String = ""

    private val cacheDir = context.cacheDir.absolutePath
    private val sessionCheckpointPath = "$cacheDir/last_session.gguf.state"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _engineState = MutableStateFlow<InitializationState>(InitializationState.Idle)
    val engineState = _engineState.asStateFlow()

    //  SOLARIS STATE MACHINE
    enum class TriageState { IDLE, RUNNING, READY, FAILED }
    private val triageState = AtomicReference(TriageState.IDLE)
    private val stateLock = Mutex()
    private val tombstoneLock = ReentrantLock()
    private val tombstoneFile = File(context.filesDir, "SANDBOX_TOMBSTONE.json")

    private val _oomDiagnostic = MutableStateFlow<OomDiagnostic?>(null)
    val oomDiagnostic = _oomDiagnostic.asStateFlow()

    data class OomDiagnostic(
        val modelName: String,
        val requiredGB: Float,
        val availableGB: Float,
        val backend: String
    )

    val processHealth = gateway.processHealth

    private val _memoryOptimizationActive = MutableStateFlow(false)
    val memoryOptimizationActive = _memoryOptimizationActive.asStateFlow()

    val vectorEngineState = vectorEngine.state

    fun getMemoryReport(context: Context): MemoryGatekeeper.MemoryReport {
        return MemoryGatekeeper.performPreflightCheck(context, currentTensorSize)
    }

    suspend fun promoteSandboxToForeground(attempt: Int = 1): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Timber.i(" [V.I.I.P] Promoting Sandbox Service to FOREGROUND (Attempt $attempt)...")
                val intent = Intent(context, com.scypheon.app.services.ModelSandboxService::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                // Allow some time for the service to actually promote
                delay(500)
                true
            } catch (e: Exception) {
                val isNotAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                    e is android.app.ForegroundServiceStartNotAllowedException
                
                if (isNotAllowed || e is IllegalStateException) {
                    if (attempt < 3) {
                        val backoff = 1000L * (1 shl (attempt - 1))
                        Timber.w(" [V.I.I.P] Promotion REFUSED. Retrying in ${backoff}ms...")
                        delay(backoff)
                        promoteSandboxToForeground(attempt + 1)
                    } else {
                        Timber.e(" [V.I.I.P] V.I.I.P Shield Promotion FATAL FAILURE after 3 attempts.")
                        false
                    }
                } else {
                    Timber.e(e, " [V.I.I.P] Unexpected promotion failure.")
                    false
                }
            }
        }
    }

    private var pendingWarning: String? = null
    
    fun getPendingInitializationWarning(): String? = pendingWarning
    private fun clearPendingWarning() { pendingWarning = null }
    data class SystemHealth(
        val isMemoryOk: Boolean,
        val isEliteOk: Boolean,
        val isUniversalOk: Boolean,
        val elitePath: String,
        val universalPath: String,
        val memoryPath: String
    )

    suspend fun checkSystemHealth(context: Context): SystemHealth = withContext(Dispatchers.IO) {
        val registry = AssetExtractor.discoverModels(context)
        
        val elitePath = registry.eliteModel?.let { AssetExtractor.getModelPath(context, it) } ?: ""
        val universalPath = registry.universalModel?.let { AssetExtractor.getModelPath(context, it) } ?: ""
        val memoryPath = registry.memoryModel?.let { AssetExtractor.getModelPath(context, it) } ?: ""

        val isMemoryOk = memoryPath.isNotEmpty() && File(memoryPath).exists()
        val isEliteOk = elitePath.isNotEmpty() && File(elitePath).exists()
        val isUniversalOk = universalPath.isNotEmpty() && File(universalPath).exists()
        
        if (!isMemoryOk || !isEliteOk || !isUniversalOk) {
            Timber.i(" Diagnostic Scan (Async): Memory=$isMemoryOk, Elite=$isEliteOk, Universal=$isUniversalOk")
        }
        
        SystemHealth(isMemoryOk, isEliteOk, isUniversalOk, elitePath, universalPath, memoryPath)
    }

    suspend fun initializeEngines(
        context: Context,
        customElitePath: String? = null,
        customUniversalPath: String? = null,
        nCtx: Int = 4096
    ): Result<Boolean> = stateLock.withLock {
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
            val finalUniversalPath = customUniversalPath ?: health.universalPath
            if (finalUniversalPath.isEmpty() || !File(finalUniversalPath).exists()) {
                return@withLock Result.Error(Exception("Universal model not found at $finalUniversalPath"))
            }

            val modelSize = File(finalUniversalPath).length()
            
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
                    Timber.e("🚨 [PHOENIX] OOM/Crash detected for model: ${tombstone.modelPath}")
                    hardwarePrefs.blacklistModel(tombstone.modelPath)
                    
                    val modelName = File(tombstone.modelPath).name
                    _oomDiagnostic.emit(OomDiagnostic(
                        modelName = modelName,
                        requiredGB = tombstone.modelSize.toFloat() / (1024 * 1024 * 1024),
                        availableGB = tombstone.availableRam.toFloat() / (1024 * 1024 * 1024),
                        backend = tombstone.backend
                    ))
                }
                hardwarePrefs.blacklist(getBackendCode(tombstone.backend))
            }

            val backends = when {
                report.isVetoRequired -> listOf(1)
                gateway.getBackendMode() == 0 -> listOf(2, 3, 1).filter { !hardwarePrefs.isBlacklisted(it) }
                gateway.getBackendMode() == 1 -> listOf(1)
                gateway.getBackendMode() == 2 -> listOf(2, 3)
                else -> listOf(1)
            }

            if (hardwarePrefs.isModelBlacklisted(finalUniversalPath)) {
                Timber.w(" [GUARD] Model $finalUniversalPath is blacklisted due to previous OOM. Skipping load.")
                _engineState.emit(InitializationState.Failed("MEMORY", "Model incompatible with device RAM"))
                return@withLock Result.Error(Exception("Model blacklisted due to OOM"))
            }

            var success = false
            // [MDRS 4.2] Dynamic Context Window Optimization
            var finalCtx = nCtx
            if (hardwarePrefs.isMdrsEnabled()) {
                val memAvailableMb = MemoryGatekeeper.performPreflightCheck(context, 0).availableMB
                if (memAvailableMb < 2048) { // < 2GB Available
                    finalCtx = minOf(nCtx, 2048) // Cap at standard stable floor
                    Timber.w("📉 [MDRS] Memory Pressure Detected ($memAvailableMb MB). Scaling Context: $nCtx -> $finalCtx")
                }
            }
            
            if (hardwarePrefs.isForceDegraded()) {
                Timber.w("🚨 [SAR] Emergency Rollback: Forcing DEGRADED mode.")
                _engineState.emit(InitializationState.Failed("CRITICAL", "Emergency Rollback Active"))
                return@withLock Result.Error(Exception("Emergency Rollback Active"))
            }
            for (currentTier in backends) {
                val tierLabel = getTierName(currentTier)
                var loadSuccess = false
                
                for (attempt in 1..2) {
                    // [PHOENIX] Re-check for new tombstones during fallback loop
                    val currentTombstone = checkHardwareTombstone()
                    if (currentTombstone != null && currentTombstone.signal == 6) {
                        hardwarePrefs.blacklist(getBackendCode(currentTombstone.backend))
                        if (getTierName(currentTier) == currentTombstone.backend) continue
                    }

                    _engineState.emit(InitializationState.Trying(tierLabel, attempt))
                    
                    //  [SBI] Phase 1: Probe
                    val probeOk = withContext(Dispatchers.IO) {
                        withTimeoutOrNull(500) {
                            NativeLibraryLoader.probeBackendNative(finalUniversalPath, currentTier)
                        } ?: false
                    }
                    
                    if (!probeOk) {
                        hardwarePrefs.blacklist(currentTier)
                        break
                    }

                    // Architect Directive: Wrap GGUF load in Dispatchers.IO and emit state
                    Timber.i(" [SBI] Loading LLM on $tierLabel...")
                    currentTensorSize = modelSize
                    
                    //  [SAR] Phase 3: Pre-allocate target SHM and apply V.I.I.P OOM protection
                    com.scypheon.sdk.core.utils.ShmLifecycleManager.acquire(context, currentTensorSize, shouldDup = false)

                    val loadResult = withContext(Dispatchers.IO) {
                        // Telemetry: Log MDRS scaling
                        val availableRam = MemoryGatekeeper.performPreflightCheck(context, 0).availableMB * 1024 * 1024
                        val requestedCtx = nCtx
                        val grantedCtx = MemoryGatekeeper.calculateSafeKvCache(context, modelSize)
                        
                        SolarisTelemetry.record("mdrs_context_scaled", 1, mapOf(
                            "available_ram_mb" to (availableRam / 1024 / 1024).toString(),
                            "requested_ctx" to requestedCtx.toString(),
                            "granted_ctx" to grantedCtx.toString(),
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
                    } else if (loadResult) {
                        releaseEngines()
                        kotlinx.coroutines.delay(1000L * attempt)
                    }
                }

                if (loadSuccess) {
                    success = true
                    triageState.set(TriageState.READY)
                    _engineState.emit(InitializationState.Success(gateway.getHardwareStatus()))
                    
                    // [SBI] Step 2: Wire up the vector embedder AFTER LLM success
                    withContext(Dispatchers.IO) {
                        initializeEmbedder(registry, health)
                    }
                    
                    break
                } else {
                    hardwarePrefs.blacklist(currentTier)
                    releaseEngines()
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
            memPath.endsWith(".tflite") || memPath.endsWith(".litertlm") -> {
                Timber.i("[HOTSWAP] Routing to LiteRT embedder: $memPath")
                vectorEngine.switchToLiteRtEmbedder(memPath)
            }
            else -> {
                Timber.i("[HOTSWAP] No dedicated embedder. Piggybacking on Universal.")
                vectorEngine.switchToLlamaEmbedder(null)
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
                val gson = com.google.gson.GsonBuilder().setLenient().create()
                val reader = com.google.gson.stream.JsonReader(java.io.StringReader(json))
                reader.isLenient = true
                val tombstone = gson.fromJson<HardwareTombstone>(reader, HardwareTombstone::class.java)
                
                if (tombstone != null) {
                    tombstoneFile.delete() // Always consume if parseable

                    // [SOLARIS] VALIDITY GATE: SIG=0 or unknown backend = stale/empty tombstone.
                    // Architect Directive: Ignore SIG=0/unknown and apply 24h TTL.
                    val isKnownBackend = tombstone.backend.uppercase() in setOf("CPU", "VULKAN", "OPENCL")
                    val isRealCrash = tombstone.signal > 0 && isKnownBackend
                    val isExpired = System.currentTimeMillis() - tombstone.timestamp > 24 * 60 * 60 * 1000
                    
                    if (!isRealCrash || isExpired) {
                        Timber.w(" [SOLARIS] Tombstone discarded (stale/empty/expired): SIG=${tombstone.signal} BACKEND=${tombstone.backend} AGE=${(System.currentTimeMillis() - tombstone.timestamp)/1000}s")
                        return null
                    }
                    
                    Timber.w(" [PHOENIX] Processed CRITICAL Tombstone: SIG=${tombstone.signal} BACKEND=${tombstone.backend}")
                    lastTombstone = tombstone // Store for UI/Telemetry
                }
                tombstone
            } catch (e: Exception) {
                Timber.e(e, " [SOLARIS] Tombstone CRITICAL Corruption. Purging...")
                if (tombstoneFile.exists()) tombstoneFile.delete()
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
        enableThinking: Boolean = true
    ): kotlinx.coroutines.flow.Flow<String> {
        return if (gateway.isReady()) {
            blackBoxVault.logEvent("AI_INFERENCE_STREAM", "Neural Gateway turn-based streaming requested (Turns=${history.size})")
            gateway.generateResponse(history, topK, topP, temp, 2048, enableThinking)
                .onEach { token ->
                    //  [SAR] Phase 3: Record tokens for possible recovery
                    // In a real implementation we would need to capture the KV offset from the stream
                    // Simulating for now using a counter as the engine doesn't yet transmit offsets per-token
                    // replayBuffer.record(tokenId, kvOffset) 
                }
                .catch { e ->
                    Timber.e(e, " [PHOENIX] Inference Stream CRASHED. Initiating Solaris Recovery.")
                    val recovered = attemptPhase3Recovery()
                    if (recovered) {
                        // Re-trigger generation or signal UI to wait
                    } else {
                        throw e
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

    /**
     *  [SAR] Phase 3: Zero-Latency Handoff Recovery
     */
    private suspend fun attemptPhase3Recovery(): Boolean {
        val startTime = System.currentTimeMillis()
        Timber.w(" [SOLARIS] Emergency Sandbox Resurrection Initiated...")
        
        val pfd = com.scypheon.sdk.core.utils.ShmLifecycleManager.acquire(context, currentTensorSize)
        if (pfd == null) {
            com.scypheon.sdk.core.utils.SolarisTelemetry.record("shm_fallback", 0, mapOf("reason" to "MEMFD_UNSUPPORTED"))
            return false
        }

        return try {
            // 1. Attach existing SHM tensors (Zero-Latency!)
            val attached = gateway.attachTensorMemory(pfd, currentTensorSize, lastTensorsHash)
            if (!attached) throw IOException("SHM Attachment Failed")

            // 3. Sync KV Cache position and re-inject tokens
            val snapshots = replayBuffer.snapshot()
            if (snapshots.isNotEmpty()) {
                gateway.nativeKvRestore(0, replayBuffer.lastPos())
                snapshots.forEach { 
                    gateway.injectToken(it.tokenId, it.kvOffset, it.sequenceNumber)
                }
            }

            val latency = System.currentTimeMillis() - startTime
            com.scypheon.sdk.core.utils.SolarisTelemetry.record("crash_to_ready_ready_ms", latency)
            true
        } catch (e: Exception) {
            Timber.e(e, " [SOLARIS] Phase 3 Recovery FAILED.")
            com.scypheon.sdk.core.utils.SolarisTelemetry.record("shm_fallback", 0, mapOf("reason" to "MMAP_ENOMEM"))
            false
        }
    }
}
