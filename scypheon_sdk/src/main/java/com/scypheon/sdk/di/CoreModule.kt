package com.scypheon.sdk.di

import com.scypheon.sdk.core.agent.DelegationHandler
import com.scypheon.sdk.core.agent.OrrigaDelegationHandler
import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.grounding.MedicalGroundingEngine
import com.scypheon.sdk.core.grounding.RoomMedicalGroundingEngine
import com.scypheon.sdk.core.monitor.SystemMonitorImpl
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.safety.*
import com.scypheon.sdk.core.security.AuditLoggerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreBindingsModule {
    @Binds @Singleton abstract fun bindSystemMonitor(impl: SystemMonitorImpl): com.scypheon.sdk.core.agent.SystemMonitor
    @Binds @Singleton abstract fun bindInputSanitizer(impl: InputSanitizerImpl): InputSanitizer
    @Binds @Singleton abstract fun bindSafetyPipeline(impl: SafetyPipelineImpl): com.scypheon.sdk.core.agent.SafetyPipeline
    
    @Binds @Singleton abstract fun bindRouterOutputValidator(impl: OutputValidatorImpl): com.scypheon.sdk.core.agent.RouterOutputValidator
    @Binds @Singleton abstract fun bindOodaOutputValidator(impl: OutputValidatorImpl): com.scypheon.sdk.core.agent.ooda.OutputValidator
    
    @Binds @Singleton abstract fun bindRouterAuditLogger(impl: AuditLoggerImpl): com.scypheon.sdk.core.agent.RouterAuditLogger
    @Binds @Singleton abstract fun bindOodaAuditLogger(impl: AuditLoggerImpl): com.scypheon.sdk.core.agent.ooda.AuditLogger
    
    @Binds @Singleton abstract fun bindConversationRepo(impl: com.scypheon.sdk.core.data.RoomConversationRepository): com.scypheon.sdk.core.agent.ooda.ConversationRepository
    @Binds @Singleton abstract fun bindUrgencyClassifier(impl: RuleBasedUrgencyClassifier): com.scypheon.sdk.core.agent.ooda.UrgencyClassifier
    @Binds @Singleton abstract fun bindToolMatcher(impl: KeywordToolMatcher): com.scypheon.sdk.core.agent.ooda.ToolMatcher
    @Binds @Singleton abstract fun bindParameterExtractor(impl: RegexParameterExtractor): com.scypheon.sdk.core.agent.ooda.ParameterExtractor
    @Binds @Singleton abstract fun bindDelegationHandler(impl: OrrigaDelegationHandler): DelegationHandler
    @Binds @Singleton abstract fun bindToolSchemaValidator(impl: com.scypheon.sdk.core.agent.ooda.DefaultToolSchemaValidator): com.scypheon.sdk.core.agent.ooda.ToolSchemaValidator
    @Binds @Singleton abstract fun bindExecutionContextFactory(impl: com.scypheon.sdk.core.agent.tool.DefaultExecutionContextFactory): com.scypheon.sdk.core.agent.tool.ExecutionContextFactory
    @Binds @Singleton abstract fun bindMemoryReflector(impl: com.scypheon.sdk.core.intelligence.graph.MemoryReflectorImpl): com.scypheon.sdk.core.intelligence.graph.MemoryReflector
}


@Module
@InstallIn(SingletonComponent::class)
object InfrastructureModule {
    @Provides @Singleton
    fun provideDecisionConfig(): com.scypheon.sdk.core.agent.ooda.DecisionConfig = com.scypheon.sdk.core.agent.ooda.DecisionConfig()

    @Provides @Singleton
    fun provideOrientationConfig(): com.scypheon.sdk.core.agent.ooda.OrientationConfig = com.scypheon.sdk.core.agent.ooda.OrientationConfig()
}
