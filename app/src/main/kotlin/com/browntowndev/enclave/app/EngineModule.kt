package com.browntowndev.enclave.app

import android.content.Context
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.inference.CompactionSummarizerPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.CriminalIntentModerationPort
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.OnDeviceInferenceCancellationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import com.browntowndev.enclave.domain.port.inference.ToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.feature.inference.ConversationManager
import com.browntowndev.enclave.feature.inference.ConversationManagerImpl
import com.browntowndev.enclave.feature.inference.InferenceFactoryImpl
import com.browntowndev.enclave.feature.inference.OnDeviceInferenceCancellationImpl
import com.browntowndev.enclave.feature.inference.ProfileAnalysisInferenceAdapter
import com.browntowndev.enclave.feature.inference.moderation.OnnxCriminalIntentClassifier
import com.browntowndev.enclave.feature.inference.moderation.OnnxPromptModerationClassifier
import com.browntowndev.enclave.feature.inference.compaction.OnnxCompactionSummarizer
import com.browntowndev.enclave.feature.inference.titlegen.OnnxChatTitleGenerator
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

    @Binds
    @Singleton
    abstract fun bindChatTitleGeneratorPort(impl: OnnxChatTitleGenerator): ChatTitleGeneratorPort

    @Binds
    @Singleton
    abstract fun bindCompactionSummarizerPort(impl: OnnxCompactionSummarizer): CompactionSummarizerPort

    @Binds
    @Singleton
    abstract fun bindOnDeviceInferenceCancellationPort(
        impl: OnDeviceInferenceCancellationImpl,
    ): OnDeviceInferenceCancellationPort

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
            embeddingEnginePort: EmbeddingEnginePort,
            monotonicToolActivation: MonotonicToolActivationPort,
            toolSearchEmbeddingCache: ToolSearchEmbeddingCachePort,
        ): ConversationManager =
            ConversationManagerImpl(
                context = context,
                localModelRepository = localModelRepository,
                activeModelProvider = activeModelProvider,
                loggingPort = loggingPort,
                toolExecutor = toolExecutor,
                embeddingEnginePort = embeddingEnginePort,
                monotonicToolActivation = monotonicToolActivation,
                toolSearchEmbeddingCache = toolSearchEmbeddingCache,
            )
    }
}
