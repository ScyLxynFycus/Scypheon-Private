package com.scypheon.sdk.core.utils

import android.os.ParcelFileDescriptor

/**
 * Native bridge for Android Ashmem (Anonymous Shared Memory).
 * Bypasses the 2GB (Int.MAX_VALUE) limitation of the standard Java SharedMemory API.
 */
object NativeSharedMemory {
    
    init {
        System.loadLibrary("llama-android")
    }

    /**
     * Creates a native ashmem region, loads the model file into it, and returns a ParcelFileDescriptor.
     * @param modelPath The absolute path to the model file.
     * @return A ParcelFileDescriptor for the created memory region, or null on failure.
     */
    fun loadToVault(modelPath: String): ParcelFileDescriptor? {
        val fd = loadToVaultNative(modelPath)
        return if (fd >= 0) {
            ParcelFileDescriptor.fromFd(fd)
        } else {
            null
        }
    }

    /**
     * Unmaps a memory region without closing the file descriptor.
     * @param addr The address of the mapped region.
     * @param size The size of the region.
     */
    fun unmapVault(addr: Long, size: Long) {
        unmapVaultNative(addr, size)
    }

    private external fun loadToVaultNative(modelPath: String): Int
    private external fun createNative(size: Long): Int
    private external fun unmapVaultNative(addr: Long, size: Long)
}
