package com.scypheon.sdk.core.memory

/**
 * ModelSharedMemoryManager provides the architectural foundation for ashmem-based
 * model loading. This allows the sandbox process to be killed and restarted
 * without reloading massive GGUF models from disk.
 */
interface ModelSharedMemoryManager {
    /**
     * Checks if a model is currently mapped into shared memory.
     */
    fun isModelMapped(modelPath: String): Boolean

    /**
     * Maps a model from disk into a shared memory fragment (Ashmem/MemoryFile).
     * @return File descriptor or ID for the mapping.
     */
    fun mapModel(modelPath: String): Int

    /**
     * Releases the shared memory mapping.
     */
    fun unmapModel(modelPath: String)
}

/**
 * Placeholder implementation for SAR Fase 1.
 */
class NoOpSharedMemoryManager : ModelSharedMemoryManager {
    override fun isModelMapped(modelPath: String): Boolean = false
    override fun mapModel(modelPath: String): Int = -1
    override fun unmapModel(modelPath: String) { /* No-op */ }
}
