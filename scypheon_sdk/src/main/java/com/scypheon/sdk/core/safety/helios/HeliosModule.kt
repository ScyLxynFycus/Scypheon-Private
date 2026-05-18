package com.scypheon.sdk.core.safety.helios

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HeliosModule {
    @Provides @Singleton
    fun providePrivacyShield(): Layer5PrivacyShield = Layer5PrivacyShield()

    @Provides @Singleton
    fun provideSessionRiskManager(): SessionRiskManager = SessionRiskManager()

    @Provides @Singleton
    fun provideJailbreakDetector(): Layer3BJailbreakDetector = Layer3BJailbreakDetector()
}
