package com.scypheon.sdk.core.utils

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.annotation.TargetApi
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Manages SharedMemory (ashmem) for AI model tensors.
 * This ensures "Zero-Latency Handoff" by keeping model weights resident in RAM
 * across process crashes.
 */
object ModelSharedMemoryManager {
    private var modelPfd: ParcelFileDescriptor? = null
    private var modelSize: Long = 0

    fun loadToSharedMemory(modelPath: String): ParcelFileDescriptor? {
        return try {
            val file = File(modelPath)
            if (!file.exists()) return null
            
            modelSize = file.length()
            Timber.i("🛰️ [SAR] Orchestrating Native Vault Residency: ${modelSize / (1024 * 1024)} MB")
            
            // 🛡️ High-Performance NDK Residency (Supports > 2GB)
            val pfd = NativeSharedMemory.loadToVault(modelPath)
            if (pfd == null) {
                Timber.e("Native Vault allocation failed.")
                return null
            }
            
            modelPfd = pfd
            pfd
        } catch (e: Exception) {
            Timber.e(e, "Failed to load model into Native Vault")
            null
        }
    }

    fun getParcelFileDescriptor(): ParcelFileDescriptor? {
        return try {
            modelPfd?.dup() // Return a copy for the AIDL transport
        } catch (e: Exception) {
            null
        }
    }

    fun getModelSize(): Long = modelSize

    fun isLoaded(): Boolean = modelPfd != null

    fun purge() {
        try {
            modelPfd?.close()
        } catch (e: Exception) {
            Timber.e(e, "Failed to purge Native Vault")
        }
        modelPfd = null
        modelSize = 0
    }
}
