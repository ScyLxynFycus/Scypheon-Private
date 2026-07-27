package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.agent.tool.hooks.ClinicalAuditPostHook
import com.scypheon.sdk.core.agent.tool.hooks.ClinicalSafetyPreHook
import com.scypheon.sdk.core.agent.tool.hooks.EducationAuditPostHook
import com.scypheon.sdk.core.agent.tool.hooks.ResponseQualityStopHook
import com.scypheon.sdk.core.agent.tool.hooks.SafetyGuardStopHook
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [v1.5.0-SAR] Hilt module for ToolHookEngine initialization.
 * Registers all built-in PreToolUse, PostToolUse, and Stop hooks.
 * 
 * Pattern: Claude Code registers hooks at startup via settings.json + built-in defaults.
 * Scypheon registers hooks via DI — all hooks are instantiated and registered once.
 */
@Module
@InstallIn(SingletonComponent::class)
object ToolHookModule {

    @Provides
    @Singleton
    fun provideToolHookEngine(
        clinicalPreHook: ClinicalSafetyPreHook,
        clinicalPostHook: ClinicalAuditPostHook,
        educationPostHook: EducationAuditPostHook,
        qualityStopHook: ResponseQualityStopHook,
        safetyStopHook: SafetyGuardStopHook,
        blackBoxVault: com.scypheon.sdk.core.telemetry.BlackBoxVault,
        circuitBreaker: com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
    ): ToolHookEngine {
        return ToolHookEngine(blackBoxVault, circuitBreaker).apply {
            // ── PreToolUse Hooks ──
            registerPreToolUse(clinicalPreHook)
            
            // ── PostToolUse Hooks ──
            registerPostToolUse(clinicalPostHook)
            registerPostToolUse(educationPostHook)
            
            // ── Stop Hooks (order matters — safety first, then quality) ──
            registerStopHook(safetyStopHook)
            registerStopHook(qualityStopHook)
        }
    }
}
