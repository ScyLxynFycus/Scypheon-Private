package com.scypheon.sdk.core.safety

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.scypheon.sdk.core.safety.helios.RuleDatabase
import com.scypheon.sdk.core.safety.helios.RuleDao
import com.scypheon.sdk.core.resilience.DefaultResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.agent.xai.MedicalExplainabilityEngine
import com.scypheon.sdk.core.agent.xai.DefaultMedicalExplainabilityEngine
import javax.inject.Singleton
import dagger.Provides
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
interface SafetyModule {

    @Binds
    @Singleton
    fun bindCircuitBreaker(impl: DefaultResilienceCircuitBreaker): ResilienceCircuitBreaker

    @Binds
    @Singleton
    fun bindExplainabilityEngine(impl: DefaultMedicalExplainabilityEngine): MedicalExplainabilityEngine

    companion object {

        @Provides
        @Singleton
        fun provideSafetyConfig(): SafetyConfig {
            return SafetyConfig()
        }
        
        @Provides
        @Singleton
        fun provideRuleDatabase(@ApplicationContext context: Context): RuleDatabase {
            return RuleDatabase.getInstance(context)
        }

        @Provides
        fun provideRuleDao(db: RuleDatabase): RuleDao {
            return db.ruleDao()
        }
    }
}

