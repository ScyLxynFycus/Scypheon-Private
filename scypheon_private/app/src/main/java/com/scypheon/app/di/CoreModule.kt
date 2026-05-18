package com.scypheon.app.di

import com.scypheon.app.data.local.AndroidResourceGovernor
import com.scypheon.app.data.local.RuleBasedFallbackEngineImpl
import com.scypheon.sdk.core.engine.InferenceGovernor
import com.scypheon.sdk.core.engine.RuleBasedFallbackEngine
import com.scypheon.sdk.core.safety.AiResourceGovernor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    // InferenceGovernor is provided by the SDK via @Inject constructor, 
    // no binding needed unless we were using an interface.

    @Binds
    @Singleton
    abstract fun bindAiResourceGovernor(impl: AndroidResourceGovernor): AiResourceGovernor

    @Binds
    @Singleton
    abstract fun bindFallbackEngine(impl: RuleBasedFallbackEngineImpl): RuleBasedFallbackEngine
}
