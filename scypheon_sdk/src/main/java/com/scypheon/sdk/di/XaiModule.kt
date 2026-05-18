package com.scypheon.sdk.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class XaiModule {
    // Legacy GemmaBackedExplainabilityEngine removed; pending ORRIGA-native XAI implementation
}
