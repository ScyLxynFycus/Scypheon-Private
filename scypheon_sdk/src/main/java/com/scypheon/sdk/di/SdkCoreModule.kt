package com.scypheon.sdk.di

import com.scypheon.sdk.core.memory.IVectorEngine
import com.scypheon.sdk.core.memory.VectorEngineRouter
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.DefaultResilienceCircuitBreaker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface SdkCoreModule {

    @Binds
    @Singleton
    fun bindVectorEngine(router: VectorEngineRouter): IVectorEngine
}
