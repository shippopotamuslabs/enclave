package com.browntowndev.pocketcrew.app

import android.content.Context
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.feature.inference.ConversationManagerImpl
import com.browntowndev.pocketcrew.feature.inference.InferenceFactoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for inference engine components.
 * Inference services and ConversationManagers resolve models lazily from [LocalModelRepositoryPort]
 * at runtime, eliminating the need for a startup cache.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds
    @Singleton
    abstract fun bindInferenceFactory(impl: InferenceFactoryImpl): InferenceFactoryPort

    companion object {
        // Consolidated ConversationManager provider - resolve models lazily via ModelRegistry
        // Each InferenceService gets its own manager instance to isolate Engine lifecycle per model file
        @Provides
        fun provideConversationManager(
            @ApplicationContext context: Context,
            localModelRepository: LocalModelRepositoryPort,
            activeModelProvider: ActiveModelProviderPort,
            loggingPort: LoggingPort,
            toolExecutor: ToolExecutorPort,
        ): ConversationManagerPort = ConversationManagerImpl(
            context = context,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )
    }
}
