package com.scypheon.sdk.core.safety.helios

import com.scypheon.sdk.core.safety.helios.decoders.*
import com.scypheon.sdk.core.security.PqcSignatureWrapper
import com.scypheon.sdk.core.security.PqcConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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

    @Provides @Singleton
    fun providePqcSignatureVerifier(
        nativeWrapper: PqcSignatureWrapper,
        config: PqcConfig
    ): PqcSignatureVerifier = PqcSignatureVerifier(nativeWrapper, config)

    @Provides @Singleton
    fun provideResponseCache(): com.scypheon.sdk.core.memory.ResponseCache = com.scypheon.sdk.core.memory.ResponseCache()

    @Provides @Singleton
    fun provideFallbackTelemetry(): com.scypheon.sdk.core.resilience.FallbackTelemetry = com.scypheon.sdk.core.resilience.FallbackTelemetry()

    @Provides @Singleton
    fun provideFallbackEngine(
        graphMemory: com.scypheon.sdk.core.memory.GraphMemoryManager,
        responseCache: com.scypheon.sdk.core.memory.ResponseCache,
        telemetry: com.scypheon.sdk.core.resilience.FallbackTelemetry
    ): com.scypheon.sdk.core.resilience.FallbackEngine = 
        com.scypheon.sdk.core.resilience.SmartFallbackEngine(graphMemory, responseCache, telemetry)

    @Provides @Singleton
    fun provideRemoteConfigFetcher(
        selfHosted: SelfHostedRemoteFetcher,
        github: GitHubReleaseFetcher,
        noOp: NoOpRemoteFetcher
    ): RemoteConfigFetcher {
        return try {
            when (com.scypheon.sdk.BuildConfig.REMOTE_CONFIG_BACKEND) {
                "self-hosted" -> selfHosted
                "github" -> github
                "noop" -> noOp
                else -> noOp
            }
        } catch (e: NoSuchFieldError) {
            noOp
        }
    }

    @Provides @IntoSet
    fun provideBase64Decoder(decoder: Base64Decoder): ObfuscationDecoder = decoder

    @Provides @IntoSet
    fun provideHexDecoder(decoder: HexDecoder): ObfuscationDecoder = decoder

    @Provides @IntoSet
    fun provideUrlDecoder(decoder: UrlDecoder): ObfuscationDecoder = decoder

    @Provides @IntoSet
    fun provideRot13Decoder(decoder: Rot13Decoder): ObfuscationDecoder = decoder

    @Provides @IntoSet
    fun provideLeetspeakDecoder(decoder: LeetspeakDecoder): ObfuscationDecoder = decoder
}
