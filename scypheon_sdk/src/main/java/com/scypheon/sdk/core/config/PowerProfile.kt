package com.scypheon.sdk.core.config

/**
 * Enterprise Power Profile: Defines the operational constraints of the AI engine
 * based on device hardware state (battery, thermal, RAM).
 */
enum class PowerProfile(val maxConcurrency: Int, val allowsNetwork: Boolean) {
    HIGH_PERFORMANCE(maxConcurrency = 4, allowsNetwork = true),
    BALANCED(maxConcurrency = 2, allowsNetwork = true),
    POWER_SAVER(maxConcurrency = 1, allowsNetwork = false),
    CRITICAL(maxConcurrency = 1, allowsNetwork = false)
}
