package com.browntowndev.enclave.app

import android.content.Context
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.CriminalIntentModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.feature.inference.ConversationManager
import com.browntowndev.enclave.feature.inference.ConversationManagerImpl
import com.browntowndev.enclave.feature.inference.InferenceFactoryImpl
import com.browntowndev.enclave.feature.inference.ProfileAnalysisInferenceAdapter
import com.browntowndev.enclave.feature.inference.moderation.OnnxCriminalIntentClassifier
import com.browntowndev.enclave.feature.inference.moderation.OnnxPromptModerationClassifier
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

    @Binds
    @Singleton
    abstract fun bindProfileAnalysisInference(impl: ProfileAnalysisInferenceAdapter): ProfileAnalysisInferencePort

    @Binds
    @Singleton
    abstract fun bindPromptModerationPort(impl: OnnxPromptModerationClassifier): PromptModerationPort

    @Binds
    @Singleton
    abstract fun bindCriminalIntentModerationPort(impl: OnnxCriminalIntentClassifier): CriminalIntentModerationPort

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
        ): ConversationManager =
            ConversationManagerImpl(
                context = context,
                localModelRepository = localModelRepository,
                activeModelProvider = activeModelProvider,
                loggingPort = loggingPort,
                toolExecutor = toolExecutor,
            )
    }
}
