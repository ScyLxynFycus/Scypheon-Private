package com.scypheon.sdk.core.config

import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.EnvironmentConstraint
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ExecutionContextFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutionContextFactoryImpl @Inject constructor(
    private val settingsManager: SettingsManager
) : ExecutionContextFactory {
    override fun create(sessionId: String, environment: DeviceEnvironment, constraint: EnvironmentConstraint): ExecutionContext {
        val timeout = when (constraint) {
            EnvironmentConstraint.CRITICAL_LOW_POWER -> 3000L
            EnvironmentConstraint.THERMAL_THROTTLED -> 4000L
            else -> 5000L
        }
        val concurrency = if (constraint == EnvironmentConstraint.NORMAL) 3 else 1
        val powerProfile = when {
            environment.batteryPercent < 20 -> PowerProfile.LOW
            environment.batteryPercent < 50 -> PowerProfile.NORMAL
            else -> PowerProfile.HIGH
        }

        return ExecutionContext(
            sessionId = sessionId,
            toolTimeoutMs = timeout,
            maxConcurrency = concurrency,
            allowNetwork = environment.networkType != "NONE",
            powerProfile = powerProfile,
            thermalProfile = environment.thermalStatus
        )
    }
}
