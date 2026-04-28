package com.scypheon.sdk.core.engine

import android.content.Context
import android.llama.cpp.LLamaAndroid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LlamaCppUniversalEngine provides a universal GGUF inference layer
 * for models like Qwen, Llama, and Mistral. It also serves as a
 * hardware-fallback for Gemma 4 on devices without LiteRT NPU support.
 */
@Singleton
class LlamaCppUniversalEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseAiEngine {
    override val engineId: String = "llama_cpp_universal"
    override var friendlyName: String = "Universal Llama (Llama.cpp)"
    
    private var lastHardwareStatus: String = "Unknown"
    override val hardwareStatus: String get() = lastHardwareStatus

    val isMaliDevice: Boolean get() {
        val hardware = android.os.Build.HARDWARE.lowercase()
        val board = android.os.Build.BOARD.lowercase()
        return hardware.contains("mali") || board.contains("exynos") || hardware.contains("kirin")
    }

    /**
     * Selected Backend Mode:
     * 0: AUTO (Smart Fallback: Vulkan -> OpenCL -> CPU)
     * 1: FORCE_CPU
     * 2: FORCE_VULKAN
     * 3: FORCE_OPENCL
     */
    var selectedBackendMode: Int = 0

    private val _state = MutableStateFlow<InitializationState>(InitializationState.Idle)
    val state = _state.asStateFlow()

    private val llamaAndroid = LLamaAndroid.instance()
    private var isNativeInitialized = false

    override suspend fun initialize(modelPath: String, nCtx: Int): Boolean {
        _state.emit(InitializationState.Analyzing("Harmonizing system buffers..."))
        
        // [QWEN CRITICAL FIX] Dynamic Context Scaling based on Memory Pressure
        val availableRamMb = getAvailableMemoryMb()
        val adjustedNCtx = when {
            availableRamMb < 1500 -> 1024.coerceAtMost(nCtx) // Critical: < 1.5GB RAM free
            availableRamMb < 3000 -> 2048.coerceAtMost(nCtx) // Warning: < 3GB RAM free
            else -> nCtx
        }
        
        if (adjustedNCtx < nCtx) {
            Timber.w("⚠️ Memory Pressure Detected (${availableRamMb}MB). Scaling n_ctx: $nCtx -> $adjustedNCtx")
        }

        return try {
            // 🛡️ Enterprise Tripwire: Initialize native backend with filesDir for crash logging
            if (!isNativeInitialized) {
                llamaAndroid.init(context.filesDir.absolutePath)
                isNativeInitialized = true
            }

            // Forward parameters to singleton bridge before loading
            llamaAndroid.nctx = adjustedNCtx
            
            val modeName = when(selectedBackendMode) {
                2 -> "Vulkan Acceleration"
                3 -> "OpenCL Core"
                1 -> "Safe CPU-Only"
                else -> "Auto-Hybrid"
            }
            
            _state.emit(InitializationState.Trying(modeName, 1))

            // Forward call to the stable native bridge with tiered backend logic
            llamaAndroid.load(modelPath, backendMode = selectedBackendMode)
            
            // Capture hardware status from the bridge
            lastHardwareStatus = llamaAndroid.getHardwareStatus()
            
            if (lastHardwareStatus.contains("Failed", ignoreCase = true) || lastHardwareStatus.contains("Error", ignoreCase = true)) {
                 _state.emit(InitializationState.Failed(modeName, lastHardwareStatus))
                 false
            } else {
                 _state.emit(InitializationState.Success(lastHardwareStatus))
                 true
            }
        } catch (e: Exception) {
            val error = e.message ?: "Native handshake failure"
            _state.emit(InitializationState.Failed("Requested Backend", error))
            lastHardwareStatus = "Initialization Failed"
            Timber.e(e, "Error initializing Llama.cpp via bridge")
            false
        }
    }

    private fun getAvailableMemoryMb(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }

    override fun generateResponse(
        prompt: String,
        topK: Int,
        topP: Float,
        temp: Float,
        maxTokens: Int
    ): Flow<String> {
        // Pass sampling parameters to the native bridge
        llamaAndroid.nlen = maxTokens
        return llamaAndroid.send(prompt, topK = topK, topP = topP, temp = temp)
    }

    override fun release() {
        // Async release via the bridge's dedicated run loop
        kotlinx.coroutines.MainScope().launch {
             try {
                 llamaAndroid.unload()
                 Timber.i("Llama.cpp Universal Engine resources released via bridge")
             } catch (e: Exception) {
                 Timber.e(e, "Error during Llama.cpp bridge unload")
             }
        }
    }

    override fun isReady(): Boolean = llamaAndroid.isReady()
}
