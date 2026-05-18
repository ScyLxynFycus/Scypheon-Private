package com.scypheon.sdk.core.engine

/**
 * Identifies the inference backend used to run a model.
 *
 * LITE_RT   → Google LiteRT / TFLite (.tflite files). Runs on GPU/NPU via MediaPipe.
 *             Fast, low-latency, best for embedding and lightweight tasks.
 *
 * LLAMA_CPP → llama.cpp GGUF backend (.gguf files). Runs on CPU with optional Vulkan.
 *             Universal — works on any Android device regardless of NPU support.
 */
enum class EngineType {
    /** Google LiteRT / TFLite — GPU/NPU accelerated */
    LITE_RT,

    /** llama.cpp — CPU/Vulkan universal backend */
    LLAMA_CPP
}
