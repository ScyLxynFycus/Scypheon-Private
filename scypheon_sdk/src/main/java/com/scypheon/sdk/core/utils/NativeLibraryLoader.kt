package com.scypheon.sdk.core.utils

import android.content.Context
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NativeLibraryLoader ensures that the native library is loaded exactly once
 * per process. This is critical for multi-process stability in the SAR architecture.
 */
object NativeLibraryLoader {
    private val isLoaded = AtomicBoolean(false)
    private const val LIBRARY_NAME = "scypheon_native"
    
    init {
        loadSafely()
    }

    /**
     * Attempts to load the native library safely.
     * @return true if loaded successfully, false if fatal error occurred.
     */
    fun loadSafely(): Boolean {
        if (isLoaded.get()) return true

        return try {
            Timber.i("🛰️ SAR: Loading native library '$LIBRARY_NAME' in process ${android.os.Process.myPid()}")
            val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
            try {
                System.loadLibrary(LIBRARY_NAME)
            } finally {
                android.os.StrictMode.setThreadPolicy(oldPolicy)
            }
            isLoaded.set(true)
            Timber.i("✅ [SAR] Native library loaded successfully.")
            true
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "🚨 [SAR] FATAL: Failed to load native library '$LIBRARY_NAME'.")
            false
        } catch (e: Exception) {
            Timber.e(e, "🚨 [SAR] Unexpected error during library loading.")
            false
        }
    }

    @JvmStatic
    external fun createMemfdNative(name: String, size: Long): Int

    @JvmStatic
    external fun setOomScoreNative(score: Int): Int

    @JvmStatic
    external fun probeBackendNative(modelPath: String, backendType: Int): Boolean
}
