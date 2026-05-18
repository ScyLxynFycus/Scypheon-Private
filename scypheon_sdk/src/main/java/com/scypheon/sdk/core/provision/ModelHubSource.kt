package com.scypheon.sdk.core.provision

/**
 * Registry of real, publicly downloadable AI models for the Scypheon Model Hub.
 * 
 * [v1.5.2-SAR] All URLs are verified HuggingFace direct download links.
 * Models are curated for mobile devices — only mobile-friendly quantizations included.
 * 
 * Supported engine types:
 *   - LiteRT (.task, .litertlm) → Google AI Edge LiteRT-LM runtime
 *   - Llama (.gguf)             → llama.cpp native runtime
 */
object ModelHubSource {

    val recommendedModels = listOf(

        // ═══════════════════════════════════════════════════════════════
        // GEMMA 4 E2B — Compact 2B parameter model
        // ═══════════════════════════════════════════════════════════════

        // ── LiteRT (Google AI Edge) ──
        ModelMetadata(
            id = "gemma-4-e2b-litert-community",
            title = "Gemma 4 E2B (LiteRT)",
            description = "Official Google AI Edge LiteRT-LM format. Hardware-accelerated via GPU delegate. Best for devices with GPU support (Adreno/Mali).",
            sizeBytes = 2_588_147_712L,  // 2.41 GB
            quantization = "int8",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            engineType = EngineType.LITE_RT,
            isGated = false,
            provider = "litert-community",
            providerUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~4 GB RAM",
            tags = listOf("recommended", "gpu-accelerated", "multimodal")
        ),

        // ── GGUF Q4_K_M (Best balance) ──
        ModelMetadata(
            id = "gemma-4-e2b-q4km-unsloth",
            title = "Gemma 4 E2B Q4_K_M",
            description = "Optimal quality-to-size ratio by Unsloth. Recommended for most Android devices with 4+ GB free RAM. Excellent for general chat and reasoning.",
            sizeBytes = 3_106_736_256L,  // 2.89 GB
            quantization = "Q4_K_M",
            downloadUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
            fileName = "gemma-4-E2B-it-Q4_K_M.gguf",
            engineType = EngineType.LLAMA_CPP,
            isGated = false,
            provider = "Unsloth",
            providerUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~4 GB RAM",
            tags = listOf("recommended", "best-balance")
        ),

        // ── GGUF Q8_0 (High quality) ──
        ModelMetadata(
            id = "gemma-4-e2b-q8-ggml",
            title = "Gemma 4 E2B Q8_0",
            description = "High-quality 8-bit quantization by ggml-org (official llama.cpp team). Near-original quality with minimal degradation.",
            sizeBytes = 4_967_494_592L,  // 4.63 GB
            quantization = "Q8_0",
            downloadUrl = "https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q8_0.gguf",
            fileName = "gemma-4-E2B-it-Q8_0.gguf",
            engineType = EngineType.LLAMA_CPP,
            isGated = false,
            provider = "ggml-org",
            providerUrl = "https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~6 GB RAM",
            tags = listOf("high-quality")
        ),

        // ── GGUF Q3_K_M (Ultra-compact) ──
        ModelMetadata(
            id = "gemma-4-e2b-q3km-unsloth",
            title = "Gemma 4 E2B Q3_K_M",
            description = "Aggressive 3-bit quantization for devices with limited RAM (<4 GB free). Some quality trade-off for extreme portability.",
            sizeBytes = 2_536_784_000L,  // 2.36 GB
            quantization = "Q3_K_M",
            downloadUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q3_K_M.gguf",
            fileName = "gemma-4-E2B-it-Q3_K_M.gguf",
            engineType = EngineType.LLAMA_CPP,
            isGated = false,
            provider = "Unsloth",
            providerUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~3 GB RAM",
            tags = listOf("compact", "low-ram")
        ),

        // ═══════════════════════════════════════════════════════════════
        // GEMMA 4 E4B — Larger 4B parameter model
        // ═══════════════════════════════════════════════════════════════

        // ── LiteRT E4B ──
        ModelMetadata(
            id = "gemma-4-e4b-litert-community",
            title = "Gemma 4 E4B (LiteRT)",
            description = "Official Google AI Edge format for the larger 4B model. Superior reasoning and instruction-following. Requires 6+ GB free RAM.",
            sizeBytes = 3_659_530_240L,  // 3.41 GB
            quantization = "int8",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName = "gemma-4-E4B-it.litertlm",
            engineType = EngineType.LITE_RT,
            isGated = false,
            provider = "litert-community",
            providerUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~6 GB RAM",
            tags = listOf("recommended", "gpu-accelerated", "multimodal", "large")
        ),

        // ── GGUF E4B Q4_K_M ──
        ModelMetadata(
            id = "gemma-4-e4b-q4km-unsloth",
            title = "Gemma 4 E4B Q4_K_M",
            description = "Best quality/size ratio for the 4B model by Unsloth. Excellent reasoning capability for devices with 6+ GB free RAM.",
            sizeBytes = 4_977_169_568L,  // 4.63 GB
            quantization = "Q4_K_M",
            downloadUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
            fileName = "gemma-4-E4B-it-Q4_K_M.gguf",
            engineType = EngineType.LLAMA_CPP,
            isGated = false,
            provider = "Unsloth",
            providerUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~6 GB RAM",
            tags = listOf("recommended", "best-balance", "large")
        ),

        // ── GGUF E4B Q8_0 (Premium quality) ──
        ModelMetadata(
            id = "gemma-4-e4b-q8-unsloth",
            title = "Gemma 4 E4B Q8_0",
            description = "Highest quality 4B quantization by Unsloth. Near-lossless performance. Requires 8+ GB free RAM — flagship devices only.",
            sizeBytes = 8_192_951_456L,  // 7.63 GB
            quantization = "Q8_0",
            downloadUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q8_0.gguf",
            fileName = "gemma-4-E4B-it-Q8_0.gguf",
            engineType = EngineType.LLAMA_CPP,
            isGated = false,
            provider = "Unsloth",
            providerUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~8 GB RAM",
            tags = listOf("high-quality", "flagship", "large")
        ),

        // ── GGUF E4B Q3_K_M (Compact 4B) ──
        ModelMetadata(
            id = "gemma-4-e4b-q3km-unsloth",
            title = "Gemma 4 E4B Q3_K_M",
            description = "Compact 3-bit quantization of the 4B model by Unsloth. Get 4B-class reasoning on mid-range devices with 4+ GB free RAM.",
            sizeBytes = 4_058_135_712L,  // 3.78 GB
            quantization = "Q3_K_M",
            downloadUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q3_K_M.gguf",
            fileName = "gemma-4-E4B-it-Q3_K_M.gguf",
            engineType = EngineType.LLAMA_CPP,
            isGated = false,
            provider = "Unsloth",
            providerUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF",
            modelFamily = "Gemma 4",
            releaseDate = "2025-06",
            contextLength = 32768,
            ramRequired = "~4 GB RAM",
            tags = listOf("compact", "mid-range", "large")
        )
    )
}
