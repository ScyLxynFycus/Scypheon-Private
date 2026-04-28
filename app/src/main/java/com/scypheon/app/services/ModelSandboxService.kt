package com.scypheon.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import com.scypheon.sdk.core.sandbox.IModelSandbox
import com.scypheon.sdk.core.sandbox.ISandboxStatusCallback
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File

class ModelSandboxService : Service() {

    private var nativeModelPtr: Long = 0L
    private val inferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(4))
    private val callbacks = RemoteCallbackList<ISandboxStatusCallback>()
    private var activeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_LESS_FAVORABLE)
        startForegroundCompliant()
        
        // [HARDENING] Load library in isolated process context
        try {
            System.loadLibrary("llama-android")
            Timber.i("🛰️ [SANDBOX] libllama-android.so loaded successfully.")
        } catch (e: Exception) {
            Timber.e(e, "❌ [SANDBOX] Failed to load native library.")
        }
    }

    private val binder = object : IModelSandbox.Stub() {
        override fun initializeEngine(modelPath: String, nCtx: Int, useMmap: Boolean): Boolean {
            if (!isHeapSafe()) {
                broadcastError("❌ [SANDBOX] Init VETO: RSS threshold exceeded.")
                return false
            }
            
            return runCatching {
                val absPath = File(modelPath).absolutePath
                nativeModelPtr = nativeInit(absPath, nCtx, useMmap)
                if (nativeModelPtr == 0L) throw IllegalStateException("Native init NULL ptr")
                
                Timber.i("🛰️ [SANDBOX] Engine initialized at $nativeModelPtr")
                true
            }.onFailure { e ->
                broadcastError("❌ [SANDBOX] JNI Init failed: ${e.message}")
            }.getOrDefault(false)
        }

        override fun generateResponse(prompt: String, callback: ISandboxStatusCallback): Boolean {
            if (nativeModelPtr == 0L) {
                safeInvoke(callback) { it.onError("Engine not initialized") }
                return false
            }
            callbacks.register(callback)

            activeJob?.cancel()
            activeJob = inferenceScope.launch {
                try {
                    // [CRITICAL] Blocking JNI call that handles its own streaming via callback
                    nativeInference(nativeModelPtr, prompt, callback)
                    broadcastComplete()
                } catch (e: CancellationException) {
                    nativeCancelInference(nativeModelPtr)
                    Timber.d("Inference cancelled.")
                } catch (e: Exception) {
                    broadcastError("Inference failed: ${e.message}")
                }
            }
            return true
        }

        override fun unloadEngine() {
            activeJob?.cancel()
            if (nativeModelPtr != 0L) {
                nativeRelease(nativeModelPtr)
                nativeModelPtr = 0L
            }
            Timber.i("🛰️ [SANDBOX] Engine unloaded.")
        }

        override fun isHeapSafe(): Boolean {
            val rssMb = getProcessRssMb()
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalMemMb = memInfo.totalMem / (1024 * 1024)
            return rssMb < (totalMemMb * 0.60).toLong()
        }
    }

    // --- JNI EXTERNALS ---
    private external fun nativeInit(modelPath: String, nCtx: Int, useMmap: Boolean): Long
    private external fun nativeInference(modelPtr: Long, prompt: String, callback: ISandboxStatusCallback)
    private external fun nativeCancelInference(modelPtr: Long)
    private external fun nativeRelease(modelPtr: Long)

    // --- ACCURATE RSS TRACKING ---
    private fun getProcessRssMb(): Long = runCatching {
        val statm = File("/proc/self/statm").readText().split("\\s+".toRegex())
        (statm[1].toLong() * 4096L) / (1024L * 1024L)
    }.getOrNull() ?: 0L

    private fun broadcastComplete() = broadcast { it.onComplete() }
    private fun broadcastError(msg: String) = broadcast { it.onError(msg) }
    private fun broadcastWarning(msg: String) = broadcast { it.onMemoryWarning(msg) }

    private inline fun broadcast(crossinline action: (ISandboxStatusCallback) -> Unit) {
        val n = callbacks.beginBroadcast()
        try {
            for (i in 0 until n) {
                try { action(callbacks.getBroadcastItem(i)) }
                catch (e: RemoteException) { }
            }
        } finally { callbacks.finishBroadcast() }
    }

    private inline fun safeInvoke(cb: ISandboxStatusCallback, crossinline action: (ISandboxStatusCallback) -> Unit) {
        try { action(cb) } catch (e: RemoteException) { }
    }

    private fun startForegroundCompliant() {
        val channelId = "ai_sandbox"
        val channel = NotificationChannel(channelId, "AI Neural Sandbox", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Neural Core Active")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        inferenceScope.cancel()
        if (nativeModelPtr != 0L) {
            nativeRelease(nativeModelPtr)
        }
        callbacks.kill()
        super.onDestroy()
    }
}
