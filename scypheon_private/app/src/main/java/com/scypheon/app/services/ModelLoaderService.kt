package com.scypheon.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.scypheon.sdk.core.sandbox.IModelLoader
import com.scypheon.sdk.core.utils.ModelSharedMemoryManager
import timber.log.Timber

/**
 * Service running in the ":loader" process.
 * Its sole purpose is to hold the model weights in SharedMemory (ashmem)
 * to prevent reload latency when the inference sandbox crashes.
 */
class ModelLoaderService : Service() {

    private val binder = object : IModelLoader.Stub() {
        override fun loadModel(modelPath: String): ParcelFileDescriptor? {
            Timber.i("🚀 [SAR] loader: Request to load model: $modelPath")
            return ModelSharedMemoryManager.loadToSharedMemory(modelPath)
        }

        override fun isModelLoaded(): Boolean {
            return ModelSharedMemoryManager.isLoaded()
        }

        override fun getModelSize(): Long {
            return ModelSharedMemoryManager.getModelSize()
        }

        override fun purge() {
            Timber.i("🗑️ [SAR] loader: Purging model from memory.")
            ModelSharedMemoryManager.purge()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("🛰️ [SAR] ModelLoaderService starting in process: ${android.os.Process.myPid()}")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky to keep the process alive
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.w("🛑 [SAR] ModelLoaderService being destroyed. Ashmem will be lost.")
        ModelSharedMemoryManager.purge()
    }
}
