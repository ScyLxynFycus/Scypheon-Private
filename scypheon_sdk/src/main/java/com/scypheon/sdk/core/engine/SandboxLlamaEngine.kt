package com.scypheon.sdk.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SharedMemory
import android.os.ParcelFileDescriptor
import android.os.DeadObjectException
import android.os.RemoteException
import com.scypheon.sdk.core.model.ScypheonBackendDiagnostic
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.sandbox.IScypheonSandbox
import com.scypheon.sdk.core.sandbox.ISandboxStatusCallback
import com.scypheon.sdk.core.sandbox.IInferenceCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.CircuitBreakerOpenException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * SandboxLlamaEngine: The Hardened Solaris AI Client Proxy.
 * [SAR PHASE 2/3] Orchestrates zero-copy IPC and granular telemetry collection.
 * 
 * NOTE: Restored all original methods (FD, Session, Tensor, etc.) with IPC hardening.
 */
@Singleton
class SandboxLlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: com.scypheon.sdk.core.security.DatabaseKeyManager,
    private val clinicalValidator: com.scypheon.sdk.core.humanitarian.medical.ClinicalValidator,
    private val circuitBreaker: ResilienceCircuitBreaker,
    private val pqcKeyExchangeManager: com.scypheon.sdk.core.security.PqcKeyExchangeManager,
    private val blackBoxVault: com.scypheon.sdk.core.telemetry.BlackBoxVault
) : BaseAiEngine {

    override val engineId: String = "llama_sandbox"
    override var friendlyName: String = "Universal Llama (Isolated)"
    
    private var lastKnownHardware: String = "Isolated [Wait]"
    override val hardwareStatus: String get() = lastKnownHardware

    var currentModelPath: String = ""
        private set
    var currentLoadedCtx: Int = 4096
        private set
    var selectedBackendMode: Int = 0
        internal set
    
    val isMaliDevice: Boolean get() {
        val hardware = android.os.Build.HARDWARE.lowercase()
        val board = android.os.Build.BOARD.lowercase()
        return hardware.contains("mali") || board.contains("exynos") || hardware.contains("kirin")
    }

    private val sandboxRef = AtomicReference<IScypheonSandbox?>(null)
    private val isBound = java.util.concurrent.atomic.AtomicBoolean(false)
    private val nativeModelLoadedInSandbox = java.util.concurrent.atomic.AtomicBoolean(false)
    
    private val _processHealth = MutableStateFlow(true)
    val processHealth = _processHealth.asStateFlow()

    private val _initializationState = MutableStateFlow<InitializationState>(InitializationState.Idle)
    val initializationState = _initializationState.asStateFlow()

    private val _hardwareStatus = MutableStateFlow("Isolated [Wait]")
    val hardwareStatusFlow = _hardwareStatus.asStateFlow()

    private val reconnectCount = AtomicInteger(0)
    private val MAX_RECONNECTS = 3

    private val serviceIntent = Intent().setComponent(
        ComponentName(context.packageName, "com.scypheon.app.services.ModelSandboxService")
    )

    // MUTEX / BARRIER UNTUK SERVICE BINDING
    private val bindDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val pendingLoadDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val pendingProbeDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)

    private val sandboxDeathRecipient = IBinder.DeathRecipient {
        Timber.e(" [PHOENIX] Binder Death Detected. Sandbox process terminated.")
        handleServiceDeath()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.i(" [SAR] Sandbox connected in process ${android.os.Process.myPid()}")
            val sandbox = IScypheonSandbox.Stub.asInterface(service)
            sandboxRef.set(sandbox)
            _processHealth.value = true
            reconnectCount.set(0)
            
            try {
                service?.linkToDeath(sandboxDeathRecipient, 0)
                val dbKey = keyManager.getDatabaseKey()
                
                // [v1.0.6-SAR] StrictMode override for service sync
                val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
                try {
                    Timber.d("🔐 [IPC] Initiating Kyber KEM key exchange...")
                    val publicKey = sandbox.kemPublicKey
                    val encapsulationResult = pqcKeyExchangeManager.encapsulate(publicKey) 
                        ?: throw IllegalStateException("Failed to encapsulate secret")
                    
                    val encryptedDbKey = pqcKeyExchangeManager.encryptAesGcm(dbKey, encapsulationResult.sharedSecret)
                    sandbox.initWithKem(context.filesDir.absolutePath, encapsulationResult.ciphertext, encryptedDbKey)
                    
                    // Zero out secrets
                    encapsulationResult.sharedSecret.fill(0)
                    dbKey.fill(0)
                    Timber.i("🎉 [IPC] Kyber KEM key exchange completed successfully")
                } finally {
                    android.os.StrictMode.setThreadPolicy(oldPolicy)
                }
                
                // Release the barrier: Resume all coroutines waiting for binding
                bindDeferred.getAndSet(null)?.complete(true)
            } catch (e: Exception) {
                Timber.e(e, "Sandbox: Failed to sync initial state or link to death")
                bindDeferred.getAndSet(null)?.complete(false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            handleServiceDeath()
        }
    }

    private fun handleServiceDeath() {
        Timber.e("🚨 [PHOENIX] Sandbox process DIED. Triggering emergency cleanup.")
        nativeModelLoadedInSandbox.set(false)
        
        // SAR HARDENING: Immediately inform UI of the failure
        _processHealth.value = false
        _initializationState.value = InitializationState.Failed("SANDBOX", "Process Terminated (Hardware/Driver Crash)")
        _hardwareStatus.value = "Crashed (Check Logs)"
        
        // [v1.0.6-SAR] Fail any pending load requests immediately
        pendingLoadDeferred.getAndSet(null)?.complete(false)
        // [v1.1.0-SAR] Fail any pending probe requests immediately (don't wait for timeout)
        pendingProbeDeferred.getAndSet(null)?.complete(false)
        
        sandboxRef.set(null)
        isBound.set(false)
        lastKnownHardware = "Process Crashed"
        
        // Release the barrier if someone is waiting for binding
        bindDeferred.getAndSet(null)?.complete(false) 
        
        try {
            context.unbindService(connection)
        } catch (e: Exception) {
            // Ignore if already unbound
        }
        
        Timber.w("🛡️ [PHOENIX] Cleanup complete. System ready for re-initialization.")
    }

    private suspend fun ensureServiceBound(): Boolean {
        if (sandboxRef.get() != null) return true
        
        val newDeferred = CompletableDeferred<Boolean>()
        if (bindDeferred.compareAndSet(null, newDeferred)) {
            try {
                val bindSuccess = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
                if (!bindSuccess) {
                    newDeferred.complete(false)
                    bindDeferred.set(null)
                    return false
                }
                isBound.set(true)
                val success = newDeferred.await()
                if (success) {
                    sandboxRef.get()?.let { sandbox ->
                        try {
                            val statusCallback = object : ISandboxStatusCallback.Stub() {
                                override fun onInitializationProgress(progress: Float) {}
                                override fun onInitializationResult(success: Boolean) {}
                                override fun onHardwareStatusUpdate(status: String) {
                                    lastKnownHardware = status
                                    _hardwareStatus.value = status
                                }
                                override fun onInternalError(error: String) {}
                                override fun onPollutionDetected(residualBytes: Long) {}
                                override fun onEmbeddings(embeddings: FloatArray) {}
                            }
                            sandbox.getHardwareStatus(statusCallback)
                        } catch (e: Exception) { Timber.e(e, "Pre-poll fail") }
                    }
                }
                return success
            } catch (e: Exception) {
                newDeferred.complete(false)
                bindDeferred.set(null)
                return false
            }
        } else {
            return bindDeferred.get()?.await() ?: (sandboxRef.get() != null)
        }
    }

    override suspend fun initialize(modelPath: String, nCtx: Int): Boolean {
        return loadWithMode(modelPath, selectedBackendMode, nCtx)
    }

    suspend fun loadFromFd(pfd: ParcelFileDescriptor, offset: Long, size: Long, mode: Int, nCtx: Int = 4096): Boolean {
        if (!ensureServiceBound()) return false
        val sandbox = sandboxRef.get() ?: return false
        val deferred = CompletableDeferred<Boolean>()
        
        val statusCallback = object : ISandboxStatusCallback.Stub() {
            override fun onInitializationProgress(progress: Float) {}
            override fun onInitializationResult(success: Boolean) { deferred.complete(success) }
            override fun onHardwareStatusUpdate(status: String) {
                lastKnownHardware = status
                _hardwareStatus.value = status
            }
            override fun onInternalError(error: String) { deferred.complete(false) }
            override fun onPollutionDetected(residualBytes: Long) { deferred.complete(false) }
            override fun onEmbeddings(embeddings: FloatArray) {}
        }

        return try {
            _initializationState.emit(InitializationState.Attaching)
            sandbox.loadFromFd(pfd, offset, size, mode, nCtx, statusCallback)
            val result = withTimeoutOrNull(5.seconds) { deferred.await() } ?: false
            if (result) {
                nativeModelLoadedInSandbox.set(true)
                _initializationState.emit(InitializationState.Success(lastKnownHardware))
            } else {
                nativeModelLoadedInSandbox.set(false)
                _initializationState.emit(InitializationState.Failed("Zero-Latency", "FD Attachment Failed"))
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: loadFromFd failed")
            false
        }
    }

    suspend fun attachTensorMemory(pfd: ParcelFileDescriptor, size: Long, modelHash: String): Boolean {
        if (!ensureServiceBound()) return false
        val sandbox = sandboxRef.get() ?: return false
        return try {
            sandbox.attachTensorMemory(pfd, size, modelHash)
            true
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: attachTensorMemory failed")
            false
        }
    }

    suspend fun nativeKvRestore(seqId: Int, lastPos: Int) {
        sandboxRef.get()?.nativeKvRestore(seqId, lastPos)
    }

    suspend fun injectToken(tokenId: Int, kvOffset: Int, sequenceNumber: Long) {
        sandboxRef.get()?.injectToken(tokenId, kvOffset, sequenceNumber)
    }

    suspend fun processImageTensor(buffer: android.hardware.HardwareBuffer, width: Int, height: Int): Boolean {
        if (!ensureServiceBound()) return false
        val sandbox = sandboxRef.get() ?: return false
        val deferred = CompletableDeferred<Boolean>()
        
        val callback = object : IInferenceCallback.Stub() {
            override fun onOutputSharedMemoryReady(pfd: ParcelFileDescriptor, size: Int) {}
            override fun onPhaseChanged(phase: Int) {
                if (phase == 2) deferred.complete(true)
            }
            override fun onTokenAvailable(count: Int) {}
            override fun onComplete(promptTokens: Int, genTokens: Int, ttftMs: Long, tps: Float) {}
            override fun onError(errorCode: Int, message: String) {
                deferred.complete(false)
            }
        }

        return try {
            sandbox.processImageTensor(buffer, width, height, callback)
            withTimeoutOrNull(30.seconds) { deferred.await() } ?: false
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: processImageTensor failed")
            false
        }
    }

    suspend fun getEmbeddings(text: String): FloatArray? {
        val sandbox = sandboxRef.get() ?: return null
        val deferred = CompletableDeferred<FloatArray?>()
        val statusCallback = object : ISandboxStatusCallback.Stub() {
            override fun onInitializationProgress(progress: Float) {}
            override fun onInitializationResult(result: Boolean) {}
            override fun onHardwareStatusUpdate(status: String) {}
            override fun onInternalError(error: String) { deferred.complete(null) }
            override fun onPollutionDetected(residualBytes: Long) {}
            override fun onEmbeddings(embeddings: FloatArray) { deferred.complete(embeddings) }
        }
    
        return try {
            if (text.length > 32768) {
                val shm = writeStringToShm(text)
                if (shm != null) {
                    try {
                        sandbox.getEmbeddingsFromFd(shm.first, shm.second, statusCallback)
                    } finally {
                        shm.first.close()
                    }
                } else {
                    sandbox.getEmbeddings(text, statusCallback)
                }
            } else {
                sandbox.getEmbeddings(text, statusCallback)
            }
            withTimeoutOrNull(5000L) { deferred.await() }
        } catch (e: Exception) { null }
    }

    private fun writeStringToShm(text: String): Pair<ParcelFileDescriptor, Int>? {
        return try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val rawFd = com.scypheon.sdk.core.utils.NativeSharedMemory.createNative(bytes.size.toLong())
            if (rawFd < 0) return null
            
            // [v1.3.1-SAR] adoptFd takes ownership of the native FD.
            val pfd = ParcelFileDescriptor.adoptFd(rawFd)
            
            // 🛡️ DUP IS MANDATORY: SharedMemory takes ownership. If we don't dup, 
            // the GC will eventually collect nativeShm and close our PFD!
            val nativeShm = SharedMemory.fromFileDescriptor(pfd.dup())
            val writeBuf = nativeShm.mapReadWrite()
            writeBuf.put(bytes)
            SharedMemory.unmap(writeBuf)
            
            // Explicitly close the SharedMemory object to release the duplicated FD instantly.
            nativeShm.close()
            
            pfd to bytes.size
        } catch (e: Exception) {
            Timber.e(e, "SHM Prompt allocation failed")
            null
        }
    }

    suspend fun loadWithMode(modelPath: String, mode: Int, nCtx: Int, onProgress: ((Float) -> Unit)? = null): Boolean {
        if (!ensureServiceBound()) return false
        
        val safeCtx = if (java.io.File(modelPath).exists()) {
            com.scypheon.sdk.core.utils.MemoryGatekeeper.calculateSafeKvCache(context, java.io.File(modelPath).length())
        } else 32768

        // [v1.3.1-SAR] STRICT CEILING: We now enforce the Gatekeeper's limit.
        // We no longer allow the 'x4' multiplier as it leads to Binder Death on 6GB devices.
        val actualCtx = if (nCtx > 0) {
            val hardLimit = 32768
            val physicalLimit = safeCtx.coerceAtMost(hardLimit)
            
            if (nCtx > physicalLimit) {
                Timber.w("⚠️ [MEMORY] Context $nCtx exceeds safe hardware limit ($physicalLimit). Capping.")
                physicalLimit
            } else {
                nCtx
            }
        } else safeCtx

        Timber.i("🔋 [SOLARIS] Backend Locked: $actualCtx tokens (Manual Override Active)")

        val currentState = _initializationState.value
        val modelMatch = currentModelPath == modelPath && actualCtx <= currentLoadedCtx
        if (modelMatch && nativeModelLoadedInSandbox.get() && (currentState is InitializationState.Success || currentState is InitializationState.Loading)) {
            return true
        }

        val sandbox = sandboxRef.get() ?: return false
        currentModelPath = modelPath
        currentLoadedCtx = actualCtx
         val attemptLabel = when(mode) { 0 -> "Auto"; 1 -> "CPU"; 2 -> "Vulkan"; else -> "OpenCL" }
        val deferred = CompletableDeferred<Boolean>()
        pendingLoadDeferred.set(deferred)
        
        val statusCallback = object : ISandboxStatusCallback.Stub() {
            override fun onInitializationProgress(progress: Float) {
                _initializationState.tryEmit(InitializationState.Loading(attemptLabel, progress))
                onProgress?.invoke(progress)
            }
            override fun onInitializationResult(result: Boolean) { 
                pendingLoadDeferred.compareAndSet(deferred, null)
                deferred.complete(result) 
            }
            override fun onHardwareStatusUpdate(status: String) {
                lastKnownHardware = status
                _hardwareStatus.value = status
            }
            override fun onInternalError(error: String) { 
                pendingLoadDeferred.compareAndSet(deferred, null)
                deferred.complete(false) 
            }
            override fun onPollutionDetected(residualBytes: Long) { 
                pendingLoadDeferred.compareAndSet(deferred, null)
                deferred.complete(false) 
            }
            override fun onEmbeddings(embeddings: FloatArray) {}
        }
    
        return try {
            // [v1.1.2-SAR] DYNAMIC CTX FALLBACK: Retry with halved context if load fails.
            // Prevents permanent failure on devices where the computed safeCtx is still too
            // optimistic. Min floor is 512 tokens to guarantee basic functionality.
            var retryCtx = actualCtx
            var success = false
            var isHardError = false
            while (!success && retryCtx >= 512) {
                val retryDeferred = CompletableDeferred<Boolean>()
                pendingLoadDeferred.set(retryDeferred)
                val retryCallback = object : ISandboxStatusCallback.Stub() {
                    override fun onInitializationProgress(progress: Float) {
                        _initializationState.tryEmit(InitializationState.Loading(attemptLabel, progress))
                        onProgress?.invoke(progress)
                    }
                    override fun onInitializationResult(result: Boolean) {
                        pendingLoadDeferred.compareAndSet(retryDeferred, null)
                        retryDeferred.complete(result)
                    }
                    override fun onHardwareStatusUpdate(status: String) { lastKnownHardware = status; _hardwareStatus.value = status }
                    override fun onInternalError(error: String) { 
                        if (error == "HARD_LOAD_ERROR") {
                            isHardError = true
                        }
                        pendingLoadDeferred.compareAndSet(retryDeferred, null)
                        retryDeferred.complete(false) 
                    }
                    override fun onPollutionDetected(residualBytes: Long) { pendingLoadDeferred.compareAndSet(retryDeferred, null); retryDeferred.complete(false) }
                    override fun onEmbeddings(embeddings: FloatArray) {}
                }

                try {
                    val currentSandbox = sandboxRef.get()
                    if (currentSandbox == null || !currentSandbox.asBinder().isBinderAlive) {
                        Timber.w("🛡️ [PHOENIX] Sandbox is DEAD or NULL. Re-binding...")
                        if (!ensureServiceBound()) {
                            Timber.e("💀 [PHOENIX] Re-bind failed. Aborting retry.")
                            break
                        }
                        delay(1000) // ⏱️ Stability Delay: Let the driver breathe
                    }
                    
                    val freshSandbox = sandboxRef.get()!!
                    
                    // 🛡️ RE-INITIALIZE KEYS: If process died, the new process has NO database key.
                    // We must re-send it before any load attempt.
                    val dbKey = keyManager.getDatabaseKey()
                    freshSandbox.init(context.filesDir.absolutePath, dbKey)
                    
                    if (retryCtx < actualCtx) {
                        Timber.w("🛡️ [PHOENIX-CTX] Load failed at n_ctx=$actualCtx. Retrying at n_ctx=$retryCtx...")
                        _initializationState.tryEmit(InitializationState.Loading(attemptLabel, 0f))
                    }
                    
                    freshSandbox.load(modelPath, mode, retryCtx, retryCallback)
                    success = withTimeoutOrNull(45.seconds) { retryDeferred.await() } ?: false
                } catch (e: android.os.DeadObjectException) {
                    Timber.e("🚨 [PHOENIX] DeadObjectException during load! Sandbox process DIED. Aborting GPU retry loop.")
                    sandboxRef.set(null) // Reset proxy
                    success = false
                    isHardError = true // Process death is a hard load/driver failure
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // [v1.5.3-SAR] CRITICAL FIX: Don't retry on scope cancellation.
                    // When Activity is destroyed, the ViewModel scope gets cancelled.
                    // Retrying is futile and creates false blacklist entries.
                    Timber.w("🛡️ [PHOENIX] Coroutine scope cancelled. Aborting retry loop (not a hardware failure).")
                    throw e // Re-throw to properly cancel the coroutine
                } catch (e: Exception) {
                    Timber.e(e, "Sandbox: unexpected error during load retry")
                    success = false
                }

                if (!success) {
                    if (isHardError) {
                        Timber.w("🛡️ [PHOENIX] Hard loading or driver failure detected. Aborting retry loop.")
                        break
                    }
                    retryCtx /= 2
                }
            }

            currentLoadedCtx = retryCtx
            if (success) {
                nativeModelLoadedInSandbox.set(true)
                _initializationState.emit(InitializationState.Success(lastKnownHardware))
            } else {
                nativeModelLoadedInSandbox.set(false)
                _initializationState.emit(InitializationState.Failed(attemptLabel, "Initialization Failed (all ctx sizes exhausted)"))
            }
            success
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: loadWithMode failed")
            nativeModelLoadedInSandbox.set(false)
            pendingLoadDeferred.compareAndSet(deferred, null)
            false
        }
    }

    suspend fun probeBackend(modelPath: String, mode: Int): Boolean {
        if (!ensureServiceBound()) return false
        val sandbox = sandboxRef.get() ?: return false
        val deferred = CompletableDeferred<Boolean>()
        // Register so handleServiceDeath() can cancel us immediately
        pendingProbeDeferred.set(deferred)
        
        val statusCallback = object : ISandboxStatusCallback.Stub() {
            override fun onInitializationProgress(progress: Float) {}
            override fun onInitializationResult(success: Boolean) { deferred.complete(success) }
            override fun onHardwareStatusUpdate(status: String) {}
            override fun onInternalError(error: String) { deferred.complete(false) }
            override fun onPollutionDetected(residualBytes: Long) { deferred.complete(false) }
            override fun onEmbeddings(embeddings: FloatArray) {}
        }

        return try {
            sandbox.probe(modelPath, mode, statusCallback)
            // [v1.1.0-SAR] Probe is CPU-only so ~2-8s is expected for large models, but large 5GB+ models can take up to 15s.
            // 30s timeout is safe and prevents premature failure on slower disks/devices.
            withTimeoutOrNull(30.seconds) { deferred.await() } ?: false
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: probeBackend failed")
            false
        } finally {
            pendingProbeDeferred.compareAndSet(deferred, null)
        }
    }

    suspend fun saveSession(path: String): Boolean {
        val sandbox = sandboxRef.get() ?: return false
        val deferred = CompletableDeferred<Boolean>()
        val statusCallback = object : ISandboxStatusCallback.Stub() {
            override fun onInitializationProgress(progress: Float) {}
            override fun onInitializationResult(success: Boolean) { deferred.complete(success) }
            override fun onHardwareStatusUpdate(status: String) {}
            override fun onInternalError(error: String) { deferred.complete(false) }
            override fun onPollutionDetected(residualBytes: Long) { deferred.complete(false) }
            override fun onEmbeddings(embeddings: FloatArray) {}
        }
        return try {
            sandbox.saveSession(path, statusCallback)
            withTimeoutOrNull(5.seconds) { deferred.await() } ?: false
        } catch (e: Exception) { false }
    }

    suspend fun loadSession(path: String): Boolean {
        val sandbox = sandboxRef.get() ?: return false
        val deferred = CompletableDeferred<Boolean>()
        val statusCallback = object : ISandboxStatusCallback.Stub() {
            override fun onInitializationProgress(progress: Float) {}
            override fun onInitializationResult(success: Boolean) { deferred.complete(success) }
            override fun onHardwareStatusUpdate(status: String) {}
            override fun onInternalError(error: String) { deferred.complete(false) }
            override fun onPollutionDetected(residualBytes: Long) { deferred.complete(false) }
            override fun onEmbeddings(embeddings: FloatArray) {}
        }
        return try {
            sandbox.loadSession(path, statusCallback)
            withTimeoutOrNull(10.seconds) { deferred.await() } ?: false
        } catch (e: Exception) { false }
    }

    fun reclaimMemory(level: Int) {
        try { sandboxRef.get()?.reclaimMemory(level) } catch (e: Exception) {}
    }

    private fun getTimeoutMs(): Long {
        val isCpuMode = selectedBackendMode == 1 || lastKnownHardware.contains("CPU", ignoreCase = true)
        // Prevent timeout during long prefills by granting a 15-minute window for all backends
        return 15 * 60 * 1000L
    }

    override fun generateResponse(
        prompt: String,
        topK: Int,
        topP: Float,
        temp: Float,
        maxTokens: Int,
        enableThinking: Boolean
    ): Flow<String> = flow {
        val requestId = UUID.randomUUID().toString()
        var attempt = 0
        val maxAttempts = 2
        var success = false

        while (attempt < maxAttempts && !success) {
            attempt++
            if (!ensureServiceBound()) {
                if (attempt == maxAttempts) {
                    emit("Error: Solaris Core unavailable.")
                }
                continue
            }

            val currentSandbox = sandboxRef.get()
            if (currentSandbox == null) {
                if (attempt == maxAttempts) {
                    emit("Error: Link lost.")
                }
                continue
            }

            if (!isReady()) {
                if (currentModelPath.isEmpty()) {
                    Timber.e("💀 [PHOENIX] Cannot infer: No model path registered. Engine must be initialized first.")
                    if (attempt == maxAttempts) emit("Error: Engine not initialized.")
                    break
                }
                Timber.w("🛡️ [PHOENIX] Sandbox was restarted. Re-loading model $currentModelPath...")
                val loadSuccess = loadWithMode(currentModelPath, selectedBackendMode, if (currentLoadedCtx > 0) currentLoadedCtx else 4096)
                if (!loadSuccess) {
                    Timber.e("💀 [PHOENIX] Model re-load failed. Aborting inference.")
                    if (attempt == maxAttempts) {
                        emit("Error: Failed to re-load model after sandbox restart.")
                    }
                    continue
                }
            }

            // [v1.2.5-SAR] CONTEXT GUARD: Truncate prompt if it exceeds context limit.
            val activeCtx = if (currentLoadedCtx >= 512) currentLoadedCtx else 4096
            val maxInputTokens = (activeCtx * 0.75).toInt()
            val estimatedPromptTokens = (prompt.length / 3.5).toInt()
            
            val safePrompt = if (estimatedPromptTokens > maxInputTokens) {
                val keepChars = (maxInputTokens * 3.5).toInt()
                Timber.w("⚠️ [CONTEXT] Prompt too long ($estimatedPromptTokens tokens). Truncating to ~$maxInputTokens tokens to fit n_ctx=$activeCtx.")
                prompt.takeLast(keepChars)
            } else {
                prompt
            }

            val resultFlow = callbackFlow {
                var mappedBuffer: java.nio.ByteBuffer? = null
                var sharedMemory: android.os.SharedMemory? = null
                var currentTokenIndex = 0

                val callback = object : IInferenceCallback.Stub() {
                    override fun onOutputSharedMemoryReady(pfd: ParcelFileDescriptor, size: Int) {
                        try {
                            mappedBuffer?.let { android.os.SharedMemory.unmap(it) }
                            sharedMemory?.close()
                            
                            sharedMemory = android.os.SharedMemory.fromFileDescriptor(pfd)
                            mappedBuffer = sharedMemory?.mapReadOnly()?.apply {
                                order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            }
                        } catch (e: Exception) { 
                            timber.log.Timber.e(e, "SHM Mapping Failed") 
                        } finally { 
                            pfd.close() 
                        }
                    }

                    override fun onPhaseChanged(phase: Int) {
                        if (phase == 0) trySend("<system:prefill>")
                    }

                    override fun onTokenAvailable(count: Int) {
                        val sandboxLive = sandboxRef.get()
                        if (sandboxLive == null || !_processHealth.value || !sandboxLive.asBinder().isBinderAlive) return

                        val buffer = mappedBuffer ?: return
                        if (!buffer.isDirect) return

                        while (currentTokenIndex < count) {
                            try {
                                val offset = 4 + (currentTokenIndex * 256)
                                if (offset + 256 > buffer.capacity()) break

                                buffer.position(offset)
                                val tokenId = buffer.getInt()
                                val confidence = buffer.getFloat()
                                val length = buffer.getInt()
                                
                                val textBytes = ByteArray(244)
                                buffer.position(offset + 12)
                                buffer.get(textBytes)
                                val nullIndex = textBytes.indexOf(0.toByte())
                                val validLength = if (nullIndex == -1) 244 else nullIndex
                                val text = String(textBytes, 0, validLength, Charsets.UTF_8).replace("\u2581", " ")
                                
                                if (text.isNotEmpty()) {
                                    trySend(text)
                                }
                                currentTokenIndex++
                            } catch (e: Exception) { break }
                        }
                    }

                    override fun onComplete(promptTokens: Int, genTokens: Int, ttftMs: Long, tps: Float) {
                        close()
                    }

                    override fun onError(errorCode: Int, message: String) {
                        close(Exception("Error $errorCode: $message"))
                    }
                }

                val healthJob = CoroutineScope(Dispatchers.Default).launch {
                    processHealth.collect { isAlive ->
                        if (!isAlive) close(DeadObjectException("Sandbox process terminated"))
                    }
                }

                try {
                    val freshSandbox = sandboxRef.get()
                    if (freshSandbox == null || !freshSandbox.asBinder().isBinderAlive) {
                        close(DeadObjectException("Sandbox binder is not alive"))
                        return@callbackFlow
                    }

                    if (safePrompt.length > 32768) {
                        val shm = writeStringToShm(safePrompt)
                        if (shm != null) {
                            try {
                                freshSandbox.sendFromFd(shm.first, shm.second, topK, topP, temp, maxTokens, enableThinking, callback)
                            } finally {
                                shm.first.close()
                            }
                        } else {
                            freshSandbox.sendWithTracing(safePrompt, topK, topP, temp, maxTokens, enableThinking, requestId, callback)
                        }
                    } else {
                        freshSandbox.sendWithTracing(safePrompt, topK, topP, temp, maxTokens, enableThinking, requestId, callback)
                    }
                } catch (e: Exception) { 
                    close(e) 
                }
                
                awaitClose { 
                    healthJob.cancel()
                    mappedBuffer?.let { android.os.SharedMemory.unmap(it) }
                    sharedMemory?.close()
                    mappedBuffer = null
                    sharedMemory = null
                    try { sandboxRef.get()?.cancelInference() } catch (e: Exception) {}
                }
            }.flowOn(Dispatchers.IO)

            try {
                var hasPrefillProgress = false
                circuitBreaker.execute("llama_engine") {
                    val responseBuffer = StringBuilder()
                    
                    // Use withTimeoutOrNull so we can distinguish OUR timeout from the caller's timeout
                    val timedOut = withTimeoutOrNull(getTimeoutMs()) {
                        resultFlow.cancellable().collect {
                            if (it == "<system:prefill>") {
                                hasPrefillProgress = true
                            } else {
                                if (it.isNotEmpty()) {
                                    hasPrefillProgress = true
                                }
                                responseBuffer.append(it)
                                emit(it)
                            }
                        }
                        true // Indicate completion
                    } == null
                    
                    if (timedOut) {
                        if (hasPrefillProgress) {
                            Timber.i(" [SAR] Internal timeout reached, but active progress detected. Clean exit.")
                        } else {
                            Timber.e("❌ [SAR] Internal timeout reached with zero progress! Engine is frozen.")
                            throw Exception("Engine hung: Internal timeout with zero progress")
                        }
                    }
                    
                    val fullResponse = responseBuffer.toString()
                    if (fullResponse.isNotEmpty()) {
                        val audit = withContext(Dispatchers.Default) { clinicalValidator.validateResponse(fullResponse) }
                        if (!audit.isSafe && audit.alertMessage != null) emit("\n\n${audit.alertMessage}")
                    }
                }
                success = true
            } catch (e: CircuitBreakerOpenException) {
                emit("⚠️ RESILIENCE ALERT: Engine is currently cooling down due to previous failures.")
                success = true
            } catch (e: CancellationException) {
                // If caller (e.g. DualMemoryManager extraction) times out, it throws CancellationException.
                // We MUST let it propagate cleanly WITHOUT killing the engine!
                Timber.i(" [SAR] Inference cancelled cleanly by parent scope. Engine remains healthy.")
                throw e 
            } catch (e: Exception) {
                Timber.e(e, "Inference attempt $attempt failed")
                handleServiceDeath()
                if (attempt == maxAttempts) {
                    emit("Error: AI engine link failed.")
                } else {
                    Timber.w("🛡️ [PHOENIX] Reconnecting and retrying inference...")
                    delay(1500)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun release() {
        nativeModelLoadedInSandbox.set(false)
        currentModelPath = ""
        currentLoadedCtx = 0
        if (isBound.get()) {
            try {
                sandboxRef.get()?.unload()
                context.unbindService(connection)
            } catch (e: Exception) {}
            finally {
                sandboxRef.set(null)
                isBound.set(false)
            }
        }
    }

    suspend fun promoteToForeground(): Boolean {
        if (!ensureServiceBound()) return false
        val sandbox = sandboxRef.get() ?: return false
        return try {
            sandbox.promoteToForeground()
            true
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: promoteToForeground failed")
            false
        }
    }

    override fun isReady(): Boolean = sandboxRef.get() != null && _initializationState.value is InitializationState.Success && nativeModelLoadedInSandbox.get()
}

