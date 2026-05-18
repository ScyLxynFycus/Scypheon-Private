package com.scypheon.sdk.core.engine

/**
 * Represents a model file discovered on the device that is ready for inference.
 *
 * @param id          Unique identifier (typically the filename without extension).
 * @param displayName Human-readable name shown in the UI (e.g. "Gemma 3 1B").
 * @param engine      Backend that will run this model (LiteRT or LLaMA.cpp).
 * @param sizeMb      File size in megabytes.
 * @param filePath    Absolute path to the model file on disk.
 */
data class DetectedModel(
    val id: String,
    val displayName: String,
    val engine: EngineType,
    val sizeMb: Long,
    val filePath: String
)
