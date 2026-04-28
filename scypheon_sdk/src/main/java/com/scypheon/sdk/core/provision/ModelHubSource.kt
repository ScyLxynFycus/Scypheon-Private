package com.scypheon.sdk.core.provision

/**
 * Registry of recommended AI models for the Scypheon Model Hub.
 * Focused on Gemma 4 and Gemma 3n variants for the hackathon.
 */
object ModelHubSource {
    val recommendedModels = listOf(
        // Gemma 4 Elite Series
        ModelMetadata(
            id = "gemma-4-e4b-it-int8",
            title = "Gemma 4 E4B IT int8",
            description = "High-performance variant of Gemma 4 E4B ready for deployment via LiteRT-LM. Supports 32K context.",
            sizeBytes = 3_600_000_000L, // 3.6 GB
            quantization = "int8",
            downloadUrl = "https://huggingface.co/scypheon-ai/gemma-4-e4b-it-int8/resolve/main/gemma-4-e4b-it-int8.task",
            fileName = "gemma-4-e4b-it-int8.task",
            engineType = EngineType.LITE_RT
        ),
        ModelMetadata(
            id = "gemma-4-e4b-it-int4",
            title = "Gemma 4 E4B IT int4",
            description = "Optimized Gemma 4 E4B for low-memory devices. Fast inference with low footprint.",
            sizeBytes = 2_100_000_000L, // 2.1 GB
            quantization = "int4",
            downloadUrl = "https://huggingface.co/scypheon-ai/gemma-4-e4b-it-int4/resolve/main/gemma-4-e4b-it-int4.task",
            fileName = "gemma-4-e4b-it-int4.task",
            engineType = EngineType.LITE_RT
        ),
        ModelMetadata(
            id = "gemma-4-e2b-it-int8",
            title = "Gemma 4 E2B IT int8",
            description = "Balanced Gemma 4 E2B variant. Excellent for general purpose agentic tasks.",
            sizeBytes = 2_500_000_000L, // 2.5 GB
            quantization = "int8",
            downloadUrl = "https://huggingface.co/scypheon-ai/gemma-4-e2b-it-int8/resolve/main/gemma-4-e2b-it-int8.task",
            fileName = "gemma-4-e2b-it-int8.task",
            engineType = EngineType.LITE_RT
        ),

        // Gemma 3n Series
        ModelMetadata(
            id = "gemma-3n-e4b-it-int8",
            title = "Gemma 3n E4B IT int8",
            description = "Reliable Gemma 3n variant for legacy device compatibility and stable performance.",
            sizeBytes = 3_700_000_000L, // 3.7 GB
            quantization = "int8",
            downloadUrl = "https://huggingface.co/scypheon-ai/gemma-3n-e4b-it-int8/resolve/main/gemma-3n-e4b-it-int8.task",
            fileName = "gemma-3n-e4b-it-int8.task",
            engineType = EngineType.LITE_RT
        ),
        ModelMetadata(
            id = "gemma-3n-e2b-it-int8",
            title = "Gemma 3n E2B IT int8",
            description = "Efficient Gemma 3n E2B for edge devices with very limited RAM.",
            sizeBytes = 2_400_000_000L, // 2.4 GB
            quantization = "int8",
            downloadUrl = "https://huggingface.co/scypheon-ai/gemma-3n-e2b-it-int8/resolve/main/gemma-3n-e2b-it-int8.task",
            fileName = "gemma-3n-e2b-it-int8.task",
            engineType = EngineType.LITE_RT
        )
    )
}
