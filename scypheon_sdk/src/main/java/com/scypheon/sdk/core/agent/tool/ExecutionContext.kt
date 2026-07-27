package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.config.PowerProfile
import javax.inject.Inject
import com.scypheon.sdk.core.agent.ooda.ThermalStatus
import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.EnvironmentConstraint

/**
 * Hardware-aware execution context. Concrete data class.
 */
data class ExecutionContext(
    val sessionId: String,
    val toolTimeoutMs: Long = 5000L,
    val maxConcurrency: Int = 3,
    val allowNetwork: Boolean = true,
    val powerProfile: PowerProfile = PowerProfile.BALANCED,
    val thermalProfile: ThermalStatus = ThermalStatus.NORMAL
)

/**
 * Factory for creating hardware-aware contexts.
 */
interface ExecutionContextFactory {
    fun create(
        sessionId: String,
        environment: DeviceEnvironment,
        constraint: EnvironmentConstraint
    ): ExecutionContext
}

class DefaultExecutionContextFactory @Inject constructor() : ExecutionContextFactory {
    override fun create(
        sessionId: String,
        environment: DeviceEnvironment,
        constraint: EnvironmentConstraint
    ): ExecutionContext {
        return ExecutionContext(sessionId)
    }
}
