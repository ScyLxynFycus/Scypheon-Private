package com.scypheon.sdk.core.agent

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkflowModule {

    @Binds
    @Singleton
    abstract fun bindSummarizer(impl: DefaultSummarizer): ConversationSummarizer

    companion object {
        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
