package com.scypheon.app.di

import android.content.Context
import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import com.scypheon.sdk.core.engine.LlamaCppUniversalEngine
import com.scypheon.sdk.core.engine.SandboxLlamaEngine
import com.scypheon.sdk.core.engine.ModelLoader
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.humanitarian.accessibility.DeafEnvironmentGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.GestureGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.KineticGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.SignLanguageBridge
import com.scypheon.sdk.core.humanitarian.accessibility.VisualGuide
import com.scypheon.sdk.core.humanitarian.education.LiveEnglishTutor
import com.scypheon.sdk.core.humanitarian.medical.OfflineMedicineGuard
import com.scypheon.sdk.core.humanitarian.medical.ClinicalValidator
import com.scypheon.sdk.core.humanitarian.psychology.ReminiscenceCompanion
import com.scypheon.sdk.core.humanitarian.security.ScamGuard
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.memory.IVectorEngine
import com.scypheon.sdk.core.memory.LiteRtVectorEngine
import com.scypheon.sdk.core.memory.SandboxVectorEngine
import com.scypheon.sdk.core.memory.VectorEngineRouter
import com.scypheon.sdk.core.memory.ContextSummarizer
import com.scypheon.sdk.core.memory.GraphMemoryManager
import com.scypheon.sdk.core.memory.LocalDocumentParser
import com.scypheon.sdk.core.security.AegisPrivacyShield
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.swarm.AgentOrchestrator
import com.scypheon.sdk.core.swarm.MedicalSubAgent
import com.scypheon.sdk.core.swarm.SecuritySubAgent
import com.scypheon.sdk.core.safety.SafetyOrchestrator
import com.scypheon.sdk.core.humanitarian.medical.DrugInteractionChecker
import com.scypheon.sdk.core.humanitarian.medical.PharmacopeiaDao
import com.scypheon.sdk.core.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SdkModule {
    @Provides
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideBlackBoxVault(@ApplicationContext context: Context): BlackBoxVault {
        return BlackBoxVault(context)
    }

    @Provides
    @Singleton
    fun provideGraphMemoryManager(@ApplicationContext context: Context): GraphMemoryManager {
        return GraphMemoryManager(context)
    }

    @Provides
    @Singleton
    fun provideDualMemoryManager(
        @ApplicationContext context: Context,
        vectorEngine: IVectorEngine,
        graphManager: GraphMemoryManager
    ): DualMemoryManager {
        return DualMemoryManager(context, vectorEngine, graphManager)
    }





    @Provides
    @Singleton
    fun provideLocalDocumentParser(
        @ApplicationContext context: Context,
        dualMemoryManager: DualMemoryManager
    ): LocalDocumentParser {
        return LocalDocumentParser(context, dualMemoryManager)
    }

    @Provides
    @Singleton
    fun provideContextSummarizer(
        memoryManager: DualMemoryManager,
        gateway: NeuralGateway
    ): ContextSummarizer {
        return ContextSummarizer(memoryManager, gateway)
    }


    @Provides
    @Singleton
    fun provideLiteRtEliteEngine(
        circuitBreaker: com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
    ): LiteRtEliteEngine {
        return LiteRtEliteEngine(circuitBreaker)
    }

    @Provides
    @Singleton
    fun provideLlamaCppUniversalEngine(@ApplicationContext context: Context): LlamaCppUniversalEngine {
        return LlamaCppUniversalEngine(context)
    }

    @Provides
    @Singleton
    fun provideSandboxLlamaEngine(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
        clinicalValidator: ClinicalValidator,
        circuitBreaker: com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
    ): SandboxLlamaEngine {
        return SandboxLlamaEngine(context, keyManager, clinicalValidator, circuitBreaker)
    }

    @Provides
    @Singleton
    fun provideModelLoader(@ApplicationContext context: Context): ModelLoader {
        return ModelLoader(context)
    }

    @Provides
    @Singleton
    fun provideDocumentSkill(@ApplicationContext context: Context): com.scypheon.sdk.core.skills.DocumentSkill {
        return com.scypheon.sdk.core.skills.DocumentSkill(context)
    }

    @Provides
    @Singleton
    fun provideSensoryHooks(
        @ApplicationContext context: Context,
        audioGuardian: DeafEnvironmentGuardian,
        memoryManager: DualMemoryManager
    ): com.scypheon.sdk.core.gateway.SensoryHooks {
        return com.scypheon.sdk.core.gateway.SensoryHooks(context, audioGuardian, memoryManager)
    }

    @Provides
    @Singleton
    fun provideNeuralGateway(
        liteRtEngine: dagger.Lazy<LiteRtEliteEngine>,
        llamaEngine: dagger.Lazy<SandboxLlamaEngine>,
        modelLoader: dagger.Lazy<ModelLoader>,
        documentSkill: dagger.Lazy<com.scypheon.sdk.core.skills.DocumentSkill>,
        sensoryHooks: dagger.Lazy<com.scypheon.sdk.core.gateway.SensoryHooks>
    ): NeuralGateway {
        return NeuralGateway(liteRtEngine, llamaEngine, modelLoader, documentSkill, sensoryHooks)
    }

    @Provides
    @Singleton
    fun provideAgentOrchestrator(
        gateway: dagger.Lazy<NeuralGateway>,
        safetyOrchestrator: dagger.Lazy<SafetyOrchestrator>
    ): AgentOrchestrator {
        val orchestrator = AgentOrchestrator(gateway, safetyOrchestrator)
        orchestrator.registerAgent(MedicalSubAgent(gateway.get()))
        orchestrator.registerAgent(SecuritySubAgent(gateway.get()))
        return orchestrator
    }

    @Provides
    @Singleton
    fun provideOfflineMedicineGuard(
        @ApplicationContext context: Context,
        liteRtEngine: LiteRtEliteEngine,
        memoryManager: DualMemoryManager,
        interactionChecker: DrugInteractionChecker,
        dao: PharmacopeiaDao
    ): OfflineMedicineGuard {
        return OfflineMedicineGuard(context, liteRtEngine, memoryManager, interactionChecker, dao)
    }

    @Provides
    @Singleton
    fun provideLiveEnglishTutor(
        @ApplicationContext context: Context,
        liteRtEngine: LiteRtEliteEngine,
        memoryManager: DualMemoryManager,
        sensoryHooks: dagger.Lazy<com.scypheon.sdk.core.gateway.SensoryHooks>
    ): LiveEnglishTutor {
        return LiveEnglishTutor(context, liteRtEngine, memoryManager, sensoryHooks)
    }

    @Provides
    @Singleton
    fun provideScamGuard(
        @ApplicationContext context: Context,
        gateway: NeuralGateway
    ): ScamGuard {
        return ScamGuard(context, gateway) { scamMsg ->
            // Dispatch live alert to UI
            com.scypheon.app.ui.GlobalLiveEventBus.postEvent("🚨 [ScamGuard] Peringatan: $scamMsg")
            timber.log.Timber.w("Global Scam Detected: $scamMsg")
        }
    }

    @Provides
    @Singleton
    fun provideReminiscenceCompanion(
        @ApplicationContext context: Context,
        gateway: NeuralGateway,
        memoryManager: DualMemoryManager
    ): ReminiscenceCompanion {
        return ReminiscenceCompanion(context, gateway, memoryManager) {
            com.scypheon.app.ui.GlobalLiveEventBus.postEvent("🧠 [Reminiscence] Terapi nostalgia dimulai.")
        }
    }

    @Provides
    @Singleton
    fun provideVisualGuide(
        @ApplicationContext context: Context,
        memoryManager: DualMemoryManager
    ): VisualGuide {
        return VisualGuide(context, memoryManager)
    }

    @Provides
    @Singleton
    fun provideSignLanguageBridge(
        @ApplicationContext context: Context,
        liteRtEngine: LiteRtEliteEngine
    ): SignLanguageBridge {
        return SignLanguageBridge(context, liteRtEngine)
    }

    @Provides
    @Singleton
    fun provideDeafEnvironmentGuardian(@ApplicationContext context: Context): DeafEnvironmentGuardian {
        val guardian = DeafEnvironmentGuardian(context)
        guardian.setOnAlertTriggeredListener { label, msg ->
            com.scypheon.app.ui.GlobalLiveEventBus.postEvent("🚨 [DeafGuardian] Suara terdeteksi ($label): $msg")
            timber.log.Timber.w("Deaf Environment Guardian: $msg")
        }
        return guardian
    }

    @Provides
    @Singleton
    fun provideGestureGuardian(
        @ApplicationContext context: Context,
        memoryManager: DualMemoryManager
    ): GestureGuardian {
        return GestureGuardian(context, memoryManager) { eventType, msg ->
            com.scypheon.app.ui.GlobalLiveEventBus.postEvent(msg)
            timber.log.Timber.w("GestureGuardian Event: $eventType - $msg")
        }
    }

    @Provides
    @Singleton
    fun provideKineticGuardian(
        @ApplicationContext context: Context,
        memoryManager: DualMemoryManager
    ): KineticGuardian {
        return KineticGuardian(context, memoryManager) { type, msg ->
            com.scypheon.app.ui.GlobalLiveEventBus.postEvent(msg)
            timber.log.Timber.w("KineticGuardian Emergency: $type - $msg")
        }
    }
}
