package com.scypheon.sdk.core.config

/**
 * Device power profile for execution planning. Concrete enum, no interface.
 */
enum class PowerProfile(val maxConcurrency: Int, val allowsNetwork: Boolean) {
    LOW(maxConcurrency = 1, allowsNetwork = false),
    NORMAL(maxConcurrency = 2, allowsNetwork = true),
    HIGH(maxConcurrency = 4, allowsNetwork = true)
}
