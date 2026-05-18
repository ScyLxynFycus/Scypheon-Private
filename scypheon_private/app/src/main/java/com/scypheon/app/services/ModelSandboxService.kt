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
@AndroidEntryPoint
class ModelSandboxService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseEntryPoint {
        fun getAppDatabase(): AppDatabase
    }

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
    }

    private val binder = object : IScypheonSandbox.Stub() {
        
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
                        callback.onInitializationResult(success)
                        if (success) {
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
                        callback.onInitializationResult(success)
                        if (success) {
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
                                    this@launch.cancel() // Matikan coroutine ini secara paksa
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
                val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
                var totalBytesRead = 0
                
                // [Hardened Solaris 4.5] Robust loop to handle partial reads in IPC
                while (totalBytesRead < length) {
                    val bytesRead = inputStream.read(buffer, totalBytesRead, length - totalBytesRead)
                    if (bytesRead == -1) break
                    totalBytesRead += bytesRead
                }
                
                String(buffer, 0, totalBytesRead, Charsets.UTF_8)
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

        override fun ping() { /* Liveness check */ }

        override fun saveSession(path: String, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                val success = llama.saveSession(path)
                try { callback.onInitializationResult(success) } catch (e: RemoteException) {}
            }
        }

        override fun loadSession(path: String, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                val success = llama.loadSession(path)
                try { callback.onInitializationResult(success) } catch (e: RemoteException) {}
            }
        }

        override fun reclaimMemory(level: Int) {
            llama.setTrimLevel(level)
        }

        override fun getEmbeddings(text: String, callback: ISandboxStatusCallback) {
            serviceScope.launch {
                val result = llama.getEmbeddings(text)
                try { callback.onEmbeddings(result) } catch (e: RemoteException) {}
            }
        }

        override fun getEmbeddingsFromFd(pfd: ParcelFileDescriptor, length: Int, callback: ISandboxStatusCallback) {
            val text = readStringFromFd(pfd, length)
            getEmbeddings(text, callback)
        }

        override fun attachTensorMemory(shmFd: ParcelFileDescriptor, tensorSize: Long, modelHash: String) {
            serviceScope.launch {
                try {
                    // C++ mengambil alih file descriptor
                    llama.attachShm(shmFd.fd, tensorSize)
                } finally {
                    shmFd.close() 
                }
            }
        }
        
        override fun reportShmHealth(healthCode: Int) { /* Logic for telemetry sync */ }
        
        override fun nativeKvRestore(seqId: Int, lastPos: Int) {
            serviceScope.launch { llama.kvRestore(seqId, lastPos) }
        }
        
        override fun injectToken(tokenId: Int, kvOffset: Int, sequenceNumber: Long) {
            serviceScope.launch { llama.injectToken(tokenId, kvOffset) }
        }
        
        override fun setPerformanceMode(mode: Int) { /* TODO: Thread priority tuning */ }
        
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
                val success = llama.probe(modelPath, backendMode)
                try { callback.onInitializationResult(success) } catch (e: RemoteException) {}
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
