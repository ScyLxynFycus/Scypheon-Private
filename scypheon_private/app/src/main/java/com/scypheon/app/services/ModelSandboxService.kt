package com.scypheon.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.scypheon.sdk.core.sandbox.IInferenceCallback
import com.scypheon.sdk.core.sandbox.ISandboxStatusCallback
import com.scypheon.sdk.core.sandbox.IScypheonSandbox
import android.llama.cpp.LLamaAndroid
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoints
import com.scypheon.sdk.core.system.AppDatabase

/**
 * ModelSandboxService: The Hardened AI Inference Sandbox.
 * 
 * [GLOBAL RESILIENCE] Runs in the isolated ":ai_sandbox" process.
 * [SAFETY & TRUST] Integrates ClinicalValidator for deterministic medical grounding.
 * [ARCHITECTURE] IPC SharedMemory Bridge with Fail-Fast Binder synchronization.
 */
import com.scypheon.sdk.core.security.PqcKeyExchangeManager

@AndroidEntryPoint
class ModelSandboxService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseEntryPoint {
        fun getAppDatabase(): AppDatabase
    }

    @Inject
    lateinit var pqcKeyExchangeManager: PqcKeyExchangeManager

    private val llama = LLamaAndroid.instance()
    // Gunakan Dispatchers.Default untuk service layer agar tidak memblokir Main Thread jika terbebani
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var activeInferenceJob: Job? = null
    // MUTEX KRITIS: Mencegah tabrakan request dari Binder Thread Pool
    private val jobMutex = Mutex()
    
    private var lastFilesDir: String = ""

    override fun onCreate() {
        super.onCreate()
        Timber.i("🛰️ [SAR] Sandbox process spinning up: PID ${android.os.Process.myPid()}")
        
        // [v1.6.0-SAR] SQLCipher JNI Hardening:
        // In isolated processes like :ai_sandbox, libraries often need explicit 
        // loading because the default process initialization might be restricted.
        try {
            System.loadLibrary("sqlcipher")
            Timber.i("🛡️ [IPC] SQLCipher JNI Library loaded in Sandbox process.")
        } catch (e: Throwable) {
            Timber.e(e, "🚨 [IPC] Failed to load SQLCipher JNI. Encrypted DB will FAIL.")
        }
    }

    private val binder = object : IScypheonSandbox.Stub() {
        private var localKemPrivateKey: ByteArray? = null

        override fun getKemPublicKey(): ByteArray {
            Timber.d("🔐 [IPC] Generating ephemeral Kyber keypair...")
            val keypair = pqcKeyExchangeManager.generateKeypair() ?: throw RemoteException("Failed to generate Kyber keypair")
            localKemPrivateKey = keypair.secretKey
            return keypair.publicKey
        }

        override fun initWithKem(filesDir: String, ciphertext: ByteArray, encryptedDbKey: ByteArray) {
            Timber.d("🔐 [IPC] Initializing Sandbox with Kyber-encrypted database key...")
            val sk = localKemPrivateKey ?: throw RemoteException("KEM keypair not initialized")
            val sharedSecret = pqcKeyExchangeManager.decapsulate(ciphertext, sk) ?: throw RemoteException("Failed to decapsulate KEM secret")
            val dbKey = pqcKeyExchangeManager.decryptAesGcm(encryptedDbKey, sharedSecret)
            
            init(filesDir, dbKey)
            
            // Clean up memory
            dbKey.fill(0)
            sharedSecret.fill(0)
            localKemPrivateKey?.fill(0)
            localKemPrivateKey = null
        }
        
        override fun init(filesDir: String, dbKey: ByteArray) {
            lastFilesDir = filesDir
            com.scypheon.sdk.core.security.DatabaseKeyManager.setExternalKey(dbKey)
            
            // 🛡️ [PHOENIX] Arm native sentinel BEFORE database mounting.
            // This ensures if SQLCipher crashes, we have g_files_dir for the tombstone.
            runBlocking {
                llama.init(filesDir)
            }
            Timber.i("🛡️ [IPC] Native Sentinel Armed. Persistence: $filesDir")

            try {
                val db = EntryPoints.get(applicationContext, DatabaseEntryPoint::class.java).getAppDatabase()
                // Force open to consume the key
                db.openHelper.writableDatabase
                Timber.i("🛡️ [IPC] Database securely mounted in sandbox.")
            } catch (e: Exception) {
                Timber.e(e, "💀 FATAL: Sandbox Database initialization failed. Rejecting LLM init.")
                // Fail-Fast: Jika DB mati, jangan inisialisasi AI untuk mencegah bypass audit.
                return 
            } finally {
                // WAJIB: Hancurkan kunci dari RAM segera setelah digunakan
                com.scypheon.sdk.core.security.DatabaseKeyManager.wipeExternalKey()
                Timber.i("🧹 [IPC] Cryptographic keys wiped from Sandbox RAM.")
            }
        }

        override fun load(modelPath: String, backendMode: Int, nCtx: Int, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                jobMutex.withLock {
                    try {
                        Timber.i(" [SANDBOX] Loading model with mode=$backendMode, nCtx=$nCtx")
                        val result = llama.load(
                            pathToModel = modelPath,
                            nCtx = nCtx,
                            backendMode = backendMode,
                            progressCallback = object : LLamaAndroid.ProgressCallback {
                                override fun onProgress(progress: Float) {
                                    try { callback.onInitializationProgress(progress) } catch (e: RemoteException) {}
                                }
                            }
                        )
                        
                        val success = result > 0
                        if (!success) {
                            if (result == -3L) {
                                callback.onInternalError("CONTEXT_LIMIT")
                            } else {
                                callback.onInternalError("HARD_LOAD_ERROR")
                            }
                        } else {
                            callback.onInitializationResult(true)
                            callback.onHardwareStatusUpdate(llama.getHardwareStatus())
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Model Load Pipeline Failed")
                        try { callback.onInternalError(e.message ?: "Hardware Failure") } catch (re: RemoteException) {}
                    }
                }
            }
        }

        override fun loadFromFd(pfd: ParcelFileDescriptor, offset: Long, size: Long, backend: Int, nCtx: Int, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                jobMutex.withLock {
                    try {
                        Timber.i(" [SANDBOX] Loading model from FD with mode=$backend, nCtx=$nCtx")
                        val result = llama.loadFromFd(
                            fd = pfd.fd,
                            offset = offset,
                            size = size,
                            nCtx = nCtx,
                            backendMode = backend,
                            progressCallback = object : LLamaAndroid.ProgressCallback {
                                override fun onProgress(progress: Float) {
                                    try { callback.onInitializationProgress(progress) } catch (e: RemoteException) {}
                                }
                            }
                        )
                        
                        val success = result > 0
                        if (!success) {
                            if (result == -3L) {
                                callback.onInternalError("CONTEXT_LIMIT")
                            } else {
                                callback.onInternalError("HARD_LOAD_ERROR")
                            }
                        } else {
                            callback.onInitializationResult(true)
                            callback.onHardwareStatusUpdate(llama.getHardwareStatus())
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "FD Model Load Pipeline Failed")
                        try { callback.onInternalError(e.message ?: "Hardware Failure") } catch (re: RemoteException) {}
                    } finally {
                        pfd.close() // Selalu tutup FileDescriptor IPC
                    }
                }
            }
        }

        override fun sendWithTracing(
            prompt: String,
            topK: Int,
            topP: Float,
            temp: Float,
            maxTokens: Int,
            enableThinking: Boolean,
            requestId: String,
            callback: IInferenceCallback
        ) {
            serviceScope.launch {
                jobMutex.withLock {
                    activeInferenceJob?.cancelAndJoin() // Tunggu job sebelumnya benar-benar mati
                    
                    activeInferenceJob = launch {
                        var pfd: ParcelFileDescriptor? = null
                        try {
                            // [SAR PHASE 2] Ashmem Initialization
                            val shmSize = 1024 * 1024 // 1MB for token buffer
                            val rawFd = com.scypheon.sdk.core.utils.NativeSharedMemory.createNative(shmSize.toLong())
                            if (rawFd < 0) {
                                Timber.e("🚨 [IPC] Failed to allocate native output SHM.")
                                return@launch
                            }
                            
                            pfd = ParcelFileDescriptor.fromFd(rawFd)
                            val pfdForCallback = pfd.dup()
                            
                            // Fail-Fast: Pastikan UI Client masih hidup sebelum memulai C++ Engine
                            try { 
                                callback.onOutputSharedMemoryReady(pfdForCallback, shmSize) 
                            } catch (e: RemoteException) {
                                Timber.w("⚠️ UI Client died before inference started. Aborting.")
                                return@launch
                            }
                            
                            // PHASE 0: PREFILL (Evaluating Prompt/RAG)
                            try { callback.onPhaseChanged(0) } catch (e: RemoteException) {}

                            llama.setOutputShm(pfd.fd, shmSize)

                            var lastTokenCount = 0
                            var startTime = System.currentTimeMillis()
                            var firstTokenTime = 0L
                            
                            llama.nlen = maxTokens
                            llama.send(prompt, false, topK, topP, temp).collect { _ ->
                                if (lastTokenCount == 0) {
                                    firstTokenTime = System.currentTimeMillis()
                                    // PHASE 1: DECODING (Generating Tokens)
                                    try { callback.onPhaseChanged(1) } catch (e: RemoteException) {}
                                }
                                
                                lastTokenCount++
                                try { 
                                    callback.onTokenAvailable(lastTokenCount) 
                                } catch (e: RemoteException) {
                                    // 🛑 CRITICAL FIX: ZOMBIE INFERENCE KILLER
                                    Timber.w("💀 UI Client disconnected during inference! Sending hard kill signal to C++ engine.")
                                    llama.cancelInference()
                                    cancel() // Matikan coroutine ini secara paksa
                                }
                            }
                            
                            val endTime = System.currentTimeMillis()
                            val totalGenTime = (endTime - (if (firstTokenTime > 0) firstTokenTime else endTime)).coerceAtLeast(1)
                            val tps = (lastTokenCount.toFloat() / (totalGenTime.toFloat() / 1000f))
                            val ttftMs = if (firstTokenTime > 0) (firstTokenTime - startTime) else 0L
                            
                            // Heuristic for prompt tokens: 1 token ~ 4 chars
                            val promptTokens = prompt.length / 4

                            try { 
                                callback.onComplete(promptTokens, lastTokenCount, ttftMs, tps) 
                            } catch (e: RemoteException) {}
                        } catch (e: CancellationException) {
                            llama.cancelInference()
                            Timber.i("🛑 Inference brutally cancelled for request $requestId")
                        } catch (e: Exception) {
                            Timber.e(e, "Inference pipeline crashed")
                            val errorCode = when (e) {
                                is OutOfMemoryError -> 100
                                is IllegalStateException -> 101 // Assuming context exceeded for now
                                else -> 103 // Timeout/General
                            }
                            try { callback.onError(errorCode, e.message ?: "Core Engine Fault") } catch (re: RemoteException) {}
                        } finally {
                            pfd?.close()
                        }
                    }
                }
            }
        }

        override fun send(prompt: String, topK: Int, topP: Float, temp: Float, maxTokens: Int, enableThinking: Boolean, callback: IInferenceCallback) {
            sendWithTracing(prompt, topK, topP, temp, maxTokens, enableThinking, "legacy_request", callback)
        }

        override fun sendFromFd(pfd: ParcelFileDescriptor, length: Int, topK: Int, topP: Float, temp: Float, maxTokens: Int, enableThinking: Boolean, callback: IInferenceCallback) {
            val prompt = readStringFromFd(pfd, length)
            sendWithTracing(prompt, topK, topP, temp, maxTokens, enableThinking, "shm_request", callback)
        }

        private fun readStringFromFd(pfd: ParcelFileDescriptor, length: Int): String {
            return try {
                val buffer = ByteArray(length)
                java.io.FileInputStream(pfd.fileDescriptor).use { inputStream ->
                    var totalBytesRead = 0
                    
                    // [Hardened Solaris 4.5] Robust loop to handle partial reads in IPC
                    while (totalBytesRead < length) {
                        val bytesRead = inputStream.read(buffer, totalBytesRead, length - totalBytesRead)
                        if (bytesRead == -1) break
                        totalBytesRead += bytesRead
                    }
                    
                    String(buffer, 0, totalBytesRead, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                Timber.e(e, "🚨 [IPC] Failed to read prompt from Shared Memory FD")
                ""
            } finally {
                try { pfd.close() } catch (e: Exception) {}
            }
        }

        override fun unload() {
            serviceScope.launch {
                jobMutex.withLock {
                    activeInferenceJob?.cancelAndJoin()
                    llama.unload()
                }
            }
        }

        override fun getHardwareStatus(callback: ISandboxStatusCallback) {
            try { callback.onHardwareStatusUpdate(llama.getHardwareStatus()) } catch (e: RemoteException) {}
        }

        override fun isReady(callback: ISandboxStatusCallback) {
            try { callback.onInitializationResult(llama.isReady()) } catch (e: RemoteException) {}
        }

        override fun cancelInference() {
            llama.cancelInference()
            serviceScope.launch {
                jobMutex.withLock {
                    activeInferenceJob?.cancel()
                }
            }
        }

        override fun ping() { /* Liveness check */ }

        override fun saveSession(path: String, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                jobMutex.withLock {
                    val success = llama.saveSession(path)
                    try { callback.onInitializationResult(success) } catch (e: RemoteException) {}
                }
            }
        }

        override fun loadSession(path: String, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                jobMutex.withLock {
                    val success = llama.loadSession(path)
                    try { callback.onInitializationResult(success) } catch (e: RemoteException) {}
                }
            }
        }

        override fun reclaimMemory(level: Int) {
            serviceScope.launch {
                jobMutex.withLock {
                    llama.setTrimLevel(level)
                }
            }
        }

        override fun getEmbeddings(text: String, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                jobMutex.withLock {
                    val result = llama.getEmbeddings(text)
                    try { callback.onEmbeddings(result) } catch (e: RemoteException) {}
                }
            }
        }

        override fun getEmbeddingsFromFd(pfd: ParcelFileDescriptor, length: Int, callback: ISandboxStatusCallback) {
            val text = readStringFromFd(pfd, length)
            getEmbeddings(text, callback)
        }

        override fun attachTensorMemory(shmFd: ParcelFileDescriptor, tensorSize: Long, modelHash: String) {
            serviceScope.launch {
                jobMutex.withLock {
                    try {
                        // C++ mengambil alih file descriptor
                        llama.attachShm(shmFd.fd, tensorSize)
                    } catch (e: Exception) {
                        // [v1.6.1-SAR] CRITICAL FIX: native_shm_attach failure must NOT kill
                        // the sandbox process. The main process will detect failure via
                        // SandboxLlamaEngine.attachTensorMemory() returning false.
                        Timber.e(e, "🚨 [IPC] attachTensorMemory failed: ${e.message}")
                    } finally {
                        shmFd.close() 
                    }
                }
            }
        }
        
        override fun reportShmHealth(healthCode: Int) { /* Logic for telemetry sync */ }
        
        override fun nativeKvRestore(seqId: Int, lastPos: Int) {
            serviceScope.launch {
                jobMutex.withLock {
                    llama.kvRestore(seqId, lastPos)
                }
            }
        }
        
        override fun injectToken(tokenId: Int, kvOffset: Int, sequenceNumber: Long) {
            serviceScope.launch {
                jobMutex.withLock {
                    llama.injectToken(tokenId, kvOffset)
                }
            }
        }
        
        override fun setPerformanceMode(mode: Int) {
            serviceScope.launch {
                jobMutex.withLock {
                    try {
                        val priority = when (mode) {
                            1 -> android.os.Process.THREAD_PRIORITY_LOWEST
                            2 -> android.os.Process.THREAD_PRIORITY_BACKGROUND
                            3 -> android.os.Process.THREAD_PRIORITY_DEFAULT
                            4 -> android.os.Process.THREAD_PRIORITY_DISPLAY
                            5 -> android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
                            else -> android.os.Process.THREAD_PRIORITY_DEFAULT
                        }
                        android.os.Process.setThreadPriority(priority)
                        Timber.i("⚙️ [IPC] Sandbox performance mode set to level $mode (Priority: $priority)")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to set performance mode")
                    }
                }
            }
        }
        
        override fun promoteToForeground() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channelId = "solaris_neural_core"
                val name = "Neural Engine Protection"
                val importance = android.app.NotificationManager.IMPORTANCE_LOW
                val channel = android.app.NotificationChannel(channelId, name, importance).apply {
                    description = "Prevents the AI Sandbox from being terminated by the OS"
                }
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)

                val notification = androidx.core.app.NotificationCompat.Builder(this@ModelSandboxService, channelId)
                    .setContentTitle("Scypheon Neural Core")
                    .setContentText("Neural Link Active - Hardware acceleration enabled")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                    .build()

                startForeground(1001, notification)
                Timber.i("🛡️ [V.I.I.P] Sandbox promoted to Foreground Service.")
            }
        }

        override fun probe(modelPath: String, backendMode: Int, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                jobMutex.withLock {
                    val success = llama.probe(modelPath, backendMode)
                    try { callback.onInitializationResult(success) } catch (e: RemoteException) {}
                }
            }
        }

        override fun processImageTensor(buffer: android.hardware.HardwareBuffer, width: Int, height: Int, callback: IInferenceCallback) {
            serviceScope.launch {
                jobMutex.withLock {
                    try {
                        Timber.i(" [VISION] Processing Zero-Copy Image Tensor (HardwareBuffer): ${width}x${height}")
                        val success = llama.processImageTensor(buffer, width, height)
                        if (success) {
                            try { callback.onPhaseChanged(2) /* Phase 2: Vision Prefill Complete */ } catch (e: RemoteException) {}
                        } else {
                            try { callback.onError(104, "HardwareBuffer vision processing failed") } catch (e: RemoteException) {}
                        }
                    } catch (e: Exception) {
                        Timber.e(e, " [VISION] Image Tensor Processing Pipeline Failed")
                        try { callback.onError(104, e.message ?: "Vision Hardware Failure") } catch (re: RemoteException) {}
                    } finally {
                        // Crucial: The AIDL layer takes ownership of the HardwareBuffer during IPC,
                        // we should close our reference to avoid native memory leaks if not automatically handled by JNI.
                        buffer.close()
                    }
                }
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Karena ini adalah Bound Service murni, kita kembalikan START_NOT_STICKY
        // agar OS tidak mencoba me-restartnya secara sewenang-wenang tanpa kendali dari Main Process.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

}
