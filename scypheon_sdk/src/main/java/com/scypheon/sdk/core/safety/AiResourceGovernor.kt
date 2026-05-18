package com.scypheon.sdk.core.safety

import com.scypheon.sdk.core.annotations.SafetyCritical
import kotlinx.coroutines.flow.StateFlow

/**
 * AiResourceGovernor: Monitors system health to protect device longevity.
 * Implementation resides in the host app to access Android System Services.
 * 
 * ENTERPRISE: Use event-driven updates to minimize battery impact.
 */
@SafetyCritical
interface AiResourceGovernor : AutoCloseable {
    enum class DeviceState { 
        NORMAL,     // Full performance
        THROTTLED,  // Thermal/Battery warning: Execute backoff + fallback
        CRITICAL    // Emergency: Deny execution
    }

    val state: StateFlow<DeviceState>
}
