package com.scypheon.sdk.core.engine

interface HardwareConfigProvider {
    fun getStableMemoryMb(): Long
    fun isMemoryOptimized(): Boolean
}
