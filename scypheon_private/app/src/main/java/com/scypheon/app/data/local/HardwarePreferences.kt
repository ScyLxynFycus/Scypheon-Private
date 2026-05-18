package com.scypheon.app.data.local

import java.io.File

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwarePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
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

    data class ModelSelection(val name: String, val path: String)

    fun resolveBestFittingModel(): ModelSelection? {
        val registry = com.scypheon.sdk.core.utils.AssetExtractor.discoverModels(context)
        val bestName = registry.universalModel ?: registry.eliteModel ?: return null
        val bestPath = com.scypheon.sdk.core.utils.AssetExtractor.getModelPath(context, bestName)
        if (bestPath.isEmpty()) return null
        return ModelSelection(bestName, bestPath)
    }

    fun blacklistModel(modelName: String) {
        val current = prefs.getStringSet("blacklisted_models", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(modelName)
        prefs.edit().putStringSet("blacklisted_models", current).apply()
    }

    fun isModelBlacklisted(modelName: String): Boolean {
        return prefs.getStringSet("blacklisted_models", emptySet())?.contains(modelName) == true
    }

    fun isBlacklisted(tier: Int): Boolean {
        return prefs.getStringSet("blacklist", emptySet())?.contains(tier.toString()) == true
    }
    
    fun isMdrsEnabled(): Boolean = prefs.getBoolean("mdrs_enabled", true)
    fun isForceDegraded(): Boolean = prefs.getBoolean("force_degraded", false)

    fun blacklist(tier: Int) {
        if (tier == 1) return 
        val current = prefs.getStringSet("blacklist", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(tier.toString())
        prefs.edit().putStringSet("blacklist", current).apply()
    }

    /**
     * [v1.5.3-SAR] Nuclear blacklist reset.
     * Clears both tier blacklists AND model-specific blacklists from SharedPreferences.
     * Called from Settings > Reset Hardware Diagnostics.
     */
    fun unblacklistAll() {
        prefs.edit()
            .remove("blacklist")
            .remove("blacklisted_models")
            .remove("force_degraded")
            .apply()
        timber.log.Timber.i("🛡️ [PHOENIX] All hardware blacklists cleared from SharedPreferences.")
    }
}
