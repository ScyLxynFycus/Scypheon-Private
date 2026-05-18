package com.scypheon.sdk.di

import com.scypheon.sdk.core.agent.ooda.AuditLogger
import com.scypheon.sdk.core.intelligence.graph.HybridGraphOrrigaEngine
import com.scypheon.sdk.core.intelligence.graph.HybridGraphOrrigaEngineImpl
import com.scypheon.sdk.core.intelligence.graph.OrrigaConfig
import com.scypheon.sdk.core.intelligence.graph.steps.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrrigaModule {

    @Provides
    @Singleton
    fun provideOrrigaConfig(): OrrigaConfig = OrrigaConfig(
        pipelineTimeoutMs = 25000L,
        enableAuditLogging = true,
        fallbackMessage = "[SYSTEM] Deep reasoning unavailable. Safe fallback activated."
    )

    @Provides
    @Singleton
    fun provideOrrigaEngine(
        reflectStep: ReflectStep,
        reasonStep: ReasonStep,
        investigateStep: InvestigateStep,
        groundStep: GroundStep,
        answerStep: AnswerStep,
        auditLogger: AuditLogger,
        config: OrrigaConfig
    ): HybridGraphOrrigaEngine = HybridGraphOrrigaEngineImpl(
        reflectStep, reasonStep, investigateStep, groundStep, answerStep, auditLogger, config
    )
}
