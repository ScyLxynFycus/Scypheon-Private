package com.scypheon.sdk.core.model

import java.io.File

/**
 * ScypheonConfig holds the session-level AI tuning parameters.
 * Moved to the SDK to allow Secure Vault persistence without App-module dependencies.
 */
data class ScypheonConfig(
    val maxTokens: Int = 4096,
    val contextWindow: Int = 4096,
    val topK: Int = 51,
    val topP: Float = 0.95f,
    val temperature: Float = 0.8f,
    val selectedBackendMode: Int = 0, // 0=Auto, 1=CPU, 2=Vulkan, 3=OpenCL
    val enableThinking: Boolean = true,
    val enableOnlineSearch: Boolean = true,
    val performanceMode: Boolean = true,
    val enableZeroLatency: Boolean = true,
    val localModels: List<File> = emptyList(),
    val isLocalModelPickerVisible: Boolean = false,
    val backendDiagnostics: List<ScypheonBackendDiagnostic> = emptyList()
)

/**
 * Captures hardware failure events (SIGSEGV/SIGILL) to inform the tiered fallback strategy.
 */
data class ScypheonBackendDiagnostic(
    val backend: String,
    val signal: Int = 0,
    val signalName: String = "",
    val timestamp: String,
    val status: String = "Disabled"
)
