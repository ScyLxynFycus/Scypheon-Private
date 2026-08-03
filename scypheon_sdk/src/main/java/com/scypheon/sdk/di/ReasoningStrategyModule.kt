package com.scypheon.sdk.di

import com.scypheon.sdk.core.intelligence.graph.DomainClassifier
import com.scypheon.sdk.core.intelligence.graph.RegexDomainClassifier
import com.scypheon.sdk.core.intelligence.graph.steps.DomainReasoningStrategy
import com.scypheon.sdk.core.intelligence.graph.steps.ReasoningDomain
import com.scypheon.sdk.core.intelligence.graph.strategies.EducationReasoningStrategy
import com.scypheon.sdk.core.intelligence.graph.strategies.GeneralReasoningStrategy
import com.scypheon.sdk.core.intelligence.graph.strategies.HumanitarianReasoningStrategy
import com.scypheon.sdk.core.intelligence.graph.strategies.MedicalReasoningStrategy
import com.scypheon.sdk.core.intelligence.graph.strategies.ResilienceReasoningStrategy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReasoningStrategyModule {

    @Multibinds
    abstract fun bindReasoningStrategies(): Map<ReasoningDomain, DomainReasoningStrategy>

    @Binds @IntoMap @ReasoningDomainKey(ReasoningDomain.MEDICAL)
    abstract fun bindMedicalStrategy(impl: MedicalReasoningStrategy): DomainReasoningStrategy

    @Binds @IntoMap @ReasoningDomainKey(ReasoningDomain.EDUCATION)
    abstract fun bindEducationStrategy(impl: EducationReasoningStrategy): DomainReasoningStrategy

    @Binds @IntoMap @ReasoningDomainKey(ReasoningDomain.RESILIENCE)
    abstract fun bindResilienceStrategy(impl: ResilienceReasoningStrategy): DomainReasoningStrategy

    @Binds @IntoMap @ReasoningDomainKey(ReasoningDomain.HUMANITARIAN)
    abstract fun bindHumanitarianStrategy(impl: HumanitarianReasoningStrategy): DomainReasoningStrategy

    @Binds @IntoMap @ReasoningDomainKey(ReasoningDomain.GENERAL)
    abstract fun bindGeneralStrategy(impl: GeneralReasoningStrategy): DomainReasoningStrategy

    @Binds @Singleton
    abstract fun bindDomainClassifier(impl: RegexDomainClassifier): DomainClassifier
}
