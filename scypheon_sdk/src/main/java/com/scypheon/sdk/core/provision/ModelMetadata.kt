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
 * Each entry maps to a real, publicly downloadable model from HuggingFace.
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
    val isGated: Boolean = false,       // true = requires HF token (private/gated models)
    val provider: String = "Unknown",   // e.g. "unsloth", "ggml-org", "litert-community"
    val providerUrl: String = "",       // Link to the model card page
    val modelFamily: String = "Gemma",  // Model family (Gemma 4, Gemma 3n, etc.)
    val releaseDate: String = "",       // ISO date or "2025-06" style
    val contextLength: Int = 32768,     // Max context window in tokens
    val ramRequired: String = "",       // e.g. "4 GB RAM", "6 GB RAM"
    val tags: List<String> = emptyList() // e.g. ["recommended", "small", "multimodal"]
)
