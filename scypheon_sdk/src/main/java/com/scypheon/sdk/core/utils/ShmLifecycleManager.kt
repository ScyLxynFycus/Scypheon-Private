package com.scypheon.sdk.core.utils

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 🛡️ [SAR] Phase 3: Shared Memory Lifecycle Manager
 * Handles refcounted memfd persistence and LMKD protection.
 */
object ShmLifecycleManager {
    private const val TAG = "ShmLifecycleManager"
    private val refCount = AtomicInteger(0)
    private var shmFd: ParcelFileDescriptor? = null
    
    // Placeholder for tensor size - in production this would be read from model metadata
    private var currentTensorSize: Long = 0

    fun acquire(context: android.content.Context, tensorSize: Long, shouldDup: Boolean = true): ParcelFileDescriptor? {
        if (refCount.incrementAndGet() == 1) {
            shmFd = createMemfd(tensorSize)
            currentTensorSize = tensorSize
            protectSharedMemory()
        }
        return if (shouldDup) shmFd?.dup() else null // 🛡️ Prevent leak if called only for setup
    }

    fun release() {
        if (refCount.decrementAndGet() <= 0) {
            Timber.i("♻️ [SAR] SHM Lifecycle: Final ref released. Closing memfd.")
            shmFd?.close()
            shmFd = null
            refCount.set(0)
        }
    }

    /**
     * Call on app foreground resume to detect zombie SHM from prior crash.
     */
    fun onForegroundResume() {
        if (refCount.get() == 0 && shmFd != null) {
            Timber.w("🚨 [SAR] Zombie SHM detected from prior session. Cleaning up.")
            shmFd?.close()
            shmFd = null
        }
    }

    private fun createMemfd(size: Long): ParcelFileDescriptor? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Timber.w("⚠️ memfd_create not supported on API < 26. Falling back to Phase 2.")
            return null
        }
        
        return try {
            // In a real implementation, we would use a JNI call to memfd_create
            // For now, we simulate the FD creation or use ASharedMemory as equivalent
            val fd = NativeLibraryLoader.createMemfdNative("solaris_tensors", size)
            if (fd >= 0) {
                ParcelFileDescriptor.adoptFd(fd)
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to create memfd")
            null
        }
    }

    private fun protectSharedMemory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // 🛡️ LMKD PROTECTION: Use native helper to bypass SDK restrictions
                val result = NativeLibraryLoader.setOomScoreNative(-1000)
                if (result == 0) {
                    Timber.i("🛡️ [SAR] SHM Protected: LMKD Score Set to -1000 via Native.")
                } else {
                    Timber.w("🛡️ [SAR] SHM Protection Warning: Native helper returned $result")
                }
            } catch (e: Exception) {
                Timber.w("Could not set OOM score adjustment: ${e.message}")
            }
        }
    }
}
