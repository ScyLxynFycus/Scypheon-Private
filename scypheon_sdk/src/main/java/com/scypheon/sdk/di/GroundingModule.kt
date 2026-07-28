package com.scypheon.sdk.di

import com.scypheon.sdk.core.grounding.MedicalGroundingEngine
import com.scypheon.sdk.core.grounding.RoomMedicalGroundingEngine
import com.scypheon.sdk.core.intelligence.graph.KnowledgeGuardImpl
import com.scypheon.sdk.core.intelligence.graph.steps.GroundStep
import com.scypheon.sdk.core.intelligence.graph.steps.InvestigateStep
import com.scypheon.sdk.core.safety.PiiDetector
import com.scypheon.sdk.core.safety.PiiDetectorImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GroundingBindingsModule {
    @Binds @Singleton abstract fun bindPiiDetector(impl: PiiDetectorImpl): PiiDetector
    @Binds @Singleton abstract fun bindMedicalGrounding(impl: RoomMedicalGroundingEngine): MedicalGroundingEngine
}

@Module
@InstallIn(SingletonComponent::class)
object GroundingProvidesModule {
    
    @Provides @Singleton
    fun provideInvestigateStep(
        groundingEngine: MedicalGroundingEngine,
        circuitBreaker: com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
    ): InvestigateStep = InvestigateStep(groundingEngine, circuitBreaker)

    @Provides @Singleton
    fun provideGroundStep(knowledgeGuard: KnowledgeGuardImpl): GroundStep =
        GroundStep(knowledgeGuard)
}

