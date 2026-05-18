package com.scypheon.sdk.di

import com.scypheon.sdk.core.agent.context.ContextManager
import com.scypheon.sdk.core.agent.orchestrator.*
import com.scypheon.sdk.core.engine.InferenceGovernor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InferenceDispatcher

@Module
@InstallIn(SingletonComponent::class)
object ContextModule {
    // GemmaOrchestrator removed in favor of ORRIGA Hybrid Graph
}
