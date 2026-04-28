package com.scypheon.sdk.core.provision

/**
 * AI Engine types supported by Scypheon.
 */
enum class EngineType {
    LITE_RT,
    LLAMA_CPP
}

/**
 * Metadata for AI models available in the Scypheon Model Hub.
 */
data class ModelMetadata(
    val id: String,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val quantization: String,
    val downloadUrl: String,
    val fileName: String,
    val engineType: EngineType,
    val isGated: Boolean = true
)
