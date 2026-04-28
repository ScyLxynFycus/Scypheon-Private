package com.scypheon.sdk.core.sandbox;

import android.os.ParcelFileDescriptor;

/**
 * Interface for the persistent :loader process.
 * This process holds the model tensors in SharedMemory (ashmem)
 * to allow zero-latency handoffs to the inference sandbox.
 */
interface IModelLoader {
    /**
     * Loads the model from disk into SharedMemory.
     * @return fd The file descriptor of the shared memory.
     */
    ParcelFileDescriptor loadModel(String modelPath);

    /**
     * Checks if a model is already resident in memory.
     */
    boolean isModelLoaded();

    /**
     * Returns the size of the loaded model.
     */
    long getModelSize();

    /**
     * Purges the model from RAM.
     */
    void purge();
}
