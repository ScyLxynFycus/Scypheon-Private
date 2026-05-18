package com.scypheon.sdk.core.utils

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import com.scypheon.sdk.core.engine.HardwareConfigProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwarePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : HardwareConfigProvider {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("hardware_prefs", Context.MODE_PRIVATE)
    }

    data class LlamaParams(
        val modelPath: String,
        val nCtx: Int,
        val useMmap: Boolean,
        val useMlock: Boolean,
        val nThreads: Int
    )

    fun getStableMemoryClass(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val heapLimitMb = am.largeMemoryClass.toLong()
        val nativeOverheadMb = 1024L // Buffering for OS and other app layers
        return (heapLimitMb - nativeOverheadMb).coerceAtLeast(1024L)
    }

    // HardwareConfigProvider implementation
    override fun getStableMemoryMb(): Long = getStableMemoryClass()

    override fun isMemoryOptimized(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return !am.isLowRamDevice
    }

    /**
     * Enterprise Native Config: Strict mlock=false for Android.
     * Android's LMK is aggressive with mlock-ed processes.
     */
    fun buildNativeParams(modelPath: String, nCtx: Int): LlamaParams {
        // Android big.LITTLE: usually 2-4 performance cores.
        // Cap at 4 or (cores / 2) to leave headroom for UI/System tasks.
        val optimalThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
        
        return LlamaParams(
            modelPath = modelPath,
            nCtx = nCtx,
            useMmap = true,
            useMlock = false,
            nThreads = optimalThreads
        )
    }

    fun blacklist(tier: Int) {
        if (tier == 1) return 
        val current = prefs.getStringSet("blacklist", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(tier.toString())
        prefs.edit().putStringSet("blacklist", current).apply()
    }

    fun blacklistModel(modelPath: String) {
        val current = prefs.getStringSet("blacklisted_models", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(modelPath)
        prefs.edit().putStringSet("blacklisted_models", current).apply()
    }

    fun isBlacklisted(tier: Int): Boolean {
        return prefs.getStringSet("blacklist", emptySet())?.contains(tier.toString()) == true
    }

    fun isModelBlacklisted(modelPath: String): Boolean {
        return prefs.getStringSet("blacklisted_models", emptySet())?.contains(modelPath) == true
    }

    fun isMdrsEnabled(): Boolean {
        return prefs.getBoolean("mdrs_enabled", true)
    }

    fun isForceDegraded(): Boolean {
        return prefs.getBoolean("force_degraded", false)
    }

    fun unblacklistAll() {
        prefs.edit()
            .remove("blacklist")
            .remove("blacklisted_models")
            .putBoolean("force_degraded", false)
            .apply()
    }
}
