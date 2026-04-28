package com.scypheon.sdk.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.scypheon.sdk.core.model.ScypheonBackendDiagnostic
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.sandbox.IScypheonSandbox
import com.scypheon.sdk.core.sandbox.IInferenceCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import com.scypheon.sdk.core.sandbox.ISandboxStatusCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * SandboxLlamaEngine is a proxy that delegates all AI operations 
 * to the :sandbox process via AIDL.
 * SAR Refinement: Now supports oneway asynchronous communication with timeouts.
 */
@Singleton
class SandboxLlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context
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
    private val isBound = AtomicReference(false)
    
    private val _processHealth = kotlinx.coroutines.flow.MutableStateFlow(true)
    val processHealth = _processHealth.asStateFlow()

    private val _initializationState = kotlinx.coroutines.flow.MutableStateFlow<InitializationState>(InitializationState.Idle)
    val initializationState = _initializationState.asStateFlow()

    private val _hardwareStatus = kotlinx.coroutines.flow.MutableStateFlow("Isolated [Wait]")
    val hardwareStatusFlow = _hardwareStatus.asStateFlow()

    private val reconnectCount = AtomicInteger(0)
    private val MAX_RECONNECTS = 3

    fun isProcessAlive(): Boolean = sandboxRef.get() != null

    private val serviceIntent = Intent().setComponent(
        ComponentName(context.packageName, "com.scypheon.app.services.ModelSandboxService")
    )

    private val sandboxDeathRecipient = IBinder.DeathRecipient {
        Timber.e(" [PHOENIX] Binder Death Detected. Sandbox process terminated.")
        _processHealth.value = false
        handleServiceDeath()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.i(" [SAR] Sandbox connected in process ${android.os.Process.myPid()}")
            val sandbox = IScypheonSandbox.Stub.asInterface(service)
            sandboxRef.set(sandbox)
            _processHealth.value = true
            reconnectCount.set(0) // Reset circuit breaker on success
            
            try {
                service?.linkToDeath(sandboxDeathRecipient, 0)
                sandbox.init(context.filesDir.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "Sandbox: Failed to sync initial state or link to death")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _processHealth.value = false
            handleServiceDeath()
        }
    }

    private fun handleServiceDeath() {
        Timber.e(" [SAR] Sandbox process DIED. Triggering emergency unbind.")
        sandboxRef.set(null)
        isBound.set(false)
        _processHealth.value = false
        lastKnownHardware = "Process Crashed"
        try {
            context.unbindService(connection)
        } catch (e: Exception) { /* Ignore */ }
    }

    private suspend fun ensureServiceBound(): Boolean {
        if (sandboxRef.get() != null) return true
        
        return suspendCancellableCoroutine { continuation ->
            try {
                val bindSuccess = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
                if (!bindSuccess) {
                    Timber.e("Sandbox: Failed to bind to ModelSandboxService")
                    continuation.resume(false)
                } else {
                    isBound.set(true)
                    // Polling hardware status immediately after bind
                    val sandbox = sandboxRef.get()
                    if (sandbox != null) {
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
                    continuation.resume(true) 
                }
            } catch (e: Exception) {
                Timber.e(e, "Sandbox: Error during bindService")
                continuation.resume(false)
            }
        }
    }

    override suspend fun initialize(modelPath: String, nCtx: Int): Boolean {
        return loadWithMode(modelPath, selectedBackendMode, nCtx)
    }

    /**
     * Specifically used by the triage loop to force a backend mode in the sandbox.
     */
    /**
     * SAR PHASE 3: Zero-Latency Handoff
     * Direct attachment to SharedMemory tensors.
     */
    suspend fun loadFromFd(pfd: android.os.ParcelFileDescriptor, offset: Long, size: Long, mode: Int): Boolean {
        if (!ensureServiceBound()) return false
        
        var bindWaitAttempts = 0
        while (sandboxRef.get() == null && bindWaitAttempts < 30) {
            kotlinx.coroutines.delay(100)
            bindWaitAttempts++
        }
        
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
            Timber.i(" [SAR] Dispatching loadFromFd command...")
            sandbox.loadFromFd(pfd, offset, size, mode, statusCallback)
            
            val result = withTimeoutOrNull(5.seconds) { // FD load should be VERY fast
                deferred.await()
            } ?: false
            
            if (result) {
                _initializationState.emit(InitializationState.Success(lastKnownHardware))
            } else {
                _initializationState.emit(InitializationState.Failed("Zero-Latency", "FD Attachment Failed"))
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: loadFromFd failed")
            false
        }
    }

    suspend fun attachTensorMemory(pfd: android.os.ParcelFileDescriptor, size: Long, modelHash: String): Boolean {
        if (!ensureServiceBound()) return false
        val sandbox = sandboxRef.get() ?: return false
        
        return try {
            Timber.i(" [SAR] Dispatching attachTensorMemory command...")
            sandbox.attachTensorMemory(pfd, size, modelHash)
            true // Oneway-ish or assuming success for now, health reported separately
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: attachTensorMemory failed")
            false
        }
    }

    suspend fun nativeKvRestore(seqId: Int, lastPos: Int) {
        val sandbox = sandboxRef.get() ?: return
        try {
            sandbox.nativeKvRestore(seqId, lastPos)
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: nativeKvRestore failed")
        }
    }

    suspend fun injectToken(tokenId: Int, kvOffset: Int, sequenceNumber: Long) {
        val sandbox = sandboxRef.get() ?: return
        try {
            sandbox.injectToken(tokenId, kvOffset, sequenceNumber)
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: injectToken failed")
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
            sandbox.getEmbeddings(text, statusCallback)
            withTimeoutOrNull(5000L) { deferred.await() }
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: getEmbeddings failed")
            null
        }
    }

    suspend fun loadWithMode(
        modelPath: String, 
        mode: Int, 
        nCtx: Int, 
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        if (!ensureServiceBound()) return false
        
        var bindWaitAttempts = 0
        while (sandboxRef.get() == null && bindWaitAttempts < 30) {
            kotlinx.coroutines.delay(100)
            bindWaitAttempts++
        }
        
        //  [MDRS 4.2] Standardize nCtx using the same logic as the service
        val modelFile = java.io.File(modelPath)
        val actualCtx = if (modelFile.exists()) {
            com.scypheon.sdk.core.utils.MemoryGatekeeper.calculateSafeKvCache(context, modelFile.length())
        } else nCtx

        //  [GUARD] Ignore redundant load requests if already active or loading same target
        val currentState = _initializationState.value
        val modelMatch = currentModelPath == modelPath && actualCtx <= currentLoadedCtx
        if (modelMatch && (currentState is InitializationState.Success || currentState is InitializationState.Loading)) {
            Timber.i(" [GUARD] Model already ${if (currentState is InitializationState.Success) "ACTIVE" else "LOADING"} with sufficient context ($currentLoadedCtx >= $actualCtx). Skipping redundant re-load.")
            return true
        }

        val sandbox = sandboxRef.get() ?: return false
        currentModelPath = modelPath
        currentLoadedCtx = actualCtx
        
        val attemptLabel = when(mode) {
            0 -> "Auto"
            1 -> "CPU"
            2 -> "Vulkan"
            else -> "OpenCL"
        }

        val deferred = CompletableDeferred<Boolean>()
        var isPolluted = false
        
        val statusCallback = object : ISandboxStatusCallback.Stub() {
            override fun onInitializationProgress(progress: Float) {
                _initializationState.tryEmit(InitializationState.Loading(attemptLabel, progress))
                onProgress?.invoke(progress)
            }
            override fun onInitializationResult(result: Boolean) {
                deferred.complete(result)
            }
            override fun onHardwareStatusUpdate(status: String) {
                lastKnownHardware = status
                _hardwareStatus.value = status
            }
            override fun onInternalError(error: String) {
                Timber.e(" Sandbox error: $error")
                deferred.complete(false)
            }
            override fun onPollutionDetected(residualBytes: Long) {
                Timber.e(" [SENTINEL] Pollution detected ($residualBytes bytes). MANDATORY RESET.")
                isPolluted = true
                deferred.complete(false)
            }
            override fun onEmbeddings(embeddings: FloatArray) {}
        }

        return try {
            Timber.i(" Sandbox: Initiating ONE-SHOT probe for Mode: $attemptLabel")
            sandbox.load(modelPath, mode, nCtx, statusCallback)
            
            val success = withTimeoutOrNull(30.seconds) {
                deferred.await()
            } ?: false
            
            if (success) {
                _initializationState.emit(InitializationState.Success(lastKnownHardware))
            } else {
                val errorMsg = if (isPolluted) "POLLUTION DETECTED" else "Initialization Failed"
                _initializationState.emit(InitializationState.Failed(attemptLabel, errorMsg))
            }
            success
        } catch (e: Exception) {
            Timber.e(e, "Sandbox load call failed")
            false
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

    /**
     * MDRS: Proactive memory reclamation.
     * Proxies the trim level to the sandbox process via AIDL.
     */
    fun reclaimMemory(level: Int) {
        try {
            sandboxRef.get()?.reclaimMemory(level)
        } catch (e: Exception) {
            Timber.e(e, "Sandbox: reclaimMemory failed")
        }
    }

    private fun getTimeoutMs(): Long {
        // Backend-aware timeout. CPU inference on 7.5B at 2-3 t/s = 5-10 minutes minimum.
        // GPU backends are faster but still need >1 minute for large contexts.
        val isCpuMode = selectedBackendMode == 1 ||
                        lastKnownHardware.contains("CPU", ignoreCase = true) ||
                        lastKnownHardware.contains("Mapped", ignoreCase = true)
        return if (isCpuMode) {
            15 * 60 * 1000L // 15 minutes for CPU mmap inference
        } else {
            3 * 60 * 1000L  // 3 minutes for GPU backends
        }
    }

    override fun generateResponse(
        prompt: String,
        topK: Int,
        topP: Float,
        temp: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        val requestId = UUID.randomUUID().toString()
        val startNs = System.nanoTime()
        Timber.i(" [REQ:$requestId] Starting inference via Solaris Core...")

        val sandbox = sandboxRef.get()
        if (sandbox == null) {
            emit("Error: Isolated process not bound.")
            return@flow
        }

        val resultFlow = callbackFlow {
            val callback = object : IInferenceCallback.Stub() {
                override fun onTokens(tokens: List<String>) {
                    tokens.forEach { trySend(it) }
                }

                override fun onError(message: String) {
                    val endNs = System.nanoTime()
                    Timber.e(" [REQ:$requestId] FAILED after ${(endNs - startNs) / 1_000_000}ms: $message")
                    close(Exception(message))
                }

                override fun onComplete() {
                    val endNs = System.nanoTime()
                    Timber.i(" [REQ:$requestId] COMPLETED successfully in ${(endNs - startNs) / 1_000_000}ms")
                    close()
                }
            } // Close IInferenceCallback.Stub() object
            
            //  [PHOENIX] Process Watchdog: Monitor health during inference
            val healthJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                processHealth.collect { isAlive ->
                    if (!isAlive) {
                        Timber.e(" [REQ:$requestId] SANDBOX DIED mid-inference. Aborting flow.")
                        close(android.os.DeadObjectException("Sandbox process terminated"))
                    }
                }
            }

            //  Dynamic n_len bounding to avert KV Cache exhaustion (n_kv_req > n_ctx)
            // Heuristic: 1 character ~ 0.5 tokens (conservative)
            val estimatedPromptTokens = (prompt.length * 0.5).toInt()
            val safeMaxTokens = (currentLoadedCtx - estimatedPromptTokens - 64).coerceAtLeast(32)
            val boundedMaxTokens = minOf(maxTokens, safeMaxTokens)
            Timber.i(" [KV-GUARD] Est. Prompt Tokens: $estimatedPromptTokens | Bounded Max Gen Tokens: $boundedMaxTokens (Ctx Limit: $currentLoadedCtx)")

            try {
                sandbox.sendWithTracing(prompt, topK, topP, temp, boundedMaxTokens, true, requestId, callback)
            } catch (e: android.os.DeadObjectException) {
                Timber.e(" [REQ:$requestId] Binder DIED mid-stream")
                close(e)
            } catch (e: Exception) {
                Timber.e(" [REQ:$requestId] Communication failure: ${e.message}")
                close(e)
            }
            awaitClose { healthJob.cancel() }
        }.flowOn(Dispatchers.IO)

        try {
            val timeoutMs = getTimeoutMs()
            withTimeout(timeoutMs) {
                resultFlow.cancellable().collect { emit(it) }
            }
        } catch (e: TimeoutCancellationException) {
            val timeoutMs = getTimeoutMs()
            Timber.e(" [REQ:$requestId] INFERENCE TIMEOUT after ${timeoutMs}ms (backend: ${if (selectedBackendMode == 1) "CPU" else "GPU"})")
            emit("Error: Response timeout. The AI is taking too long to respond.")
        } catch (e: Exception) {
            val count = reconnectCount.incrementAndGet()
            if (count < MAX_RECONNECTS) {
                Timber.w(" [REQ:$requestId] Circuit Breaker: Reconnect attempt $count/$MAX_RECONNECTS")
                handleServiceDeath()
                emit("Error: AI engine disconnected. Re-establishing link...")
            } else {
                Timber.e(" [REQ:$requestId] Circuit Breaker: MAX RECONNECTS EXCEEDED")
                emit("Error: AI engine unavailable after multiple attempts.")
            }
        }
    }.flowOn(Dispatchers.IO)


    override fun release() {
        if (isBound.get()) {
            try {
                sandboxRef.get()?.unload()
                context.unbindService(connection)
            } catch (e: Exception) {
                Timber.e(e, "Sandbox: Error during release")
            } finally {
                sandboxRef.set(null)
                isBound.set(false)
            }
        }
    }

    override fun isReady(): Boolean {
        // Solaris Refinement: Trust the internal Success state even if hardware status poll is lagging
        val state = _initializationState.value
        val isInitializingSuccess = state is InitializationState.Success
        
        return sandboxRef.get() != null && 
               (isInitializingSuccess || (lastKnownHardware != "Isolated [Wait]" && lastKnownHardware != "Process Crashed"))
    }
}
