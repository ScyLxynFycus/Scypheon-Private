package com.scypheon.app.di

import com.scypheon.sdk.core.utils.HardwarePreferences
import com.scypheon.sdk.core.engine.HardwareConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HardwareModule {
    @Binds
    abstract fun bindHardwareConfig(impl: HardwarePreferences): HardwareConfigProvider
}
