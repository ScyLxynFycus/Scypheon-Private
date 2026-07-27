package com.scypheon.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.scypheon.app.security.ScypheonIdentityManager
<<<<<<< Updated upstream
=======

import com.scypheon.sdk.core.security.SsrfProtectionInterceptor
>>>>>>> Stashed changes

/**
 * NetworkModule: Provides network-related dependencies for the Scypheon app and SDK tools.
 * Implements optimized timeouts for humanitarian use cases (reliable but patient).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(SsrfProtectionInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideScypheonIdentityManager(): ScypheonIdentityManager {
        return ScypheonIdentityManager()
    }
}

