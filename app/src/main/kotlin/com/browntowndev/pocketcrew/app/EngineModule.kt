package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.feature.inference.ConversationManagerImpl
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.model.inference.DraftOneModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.DraftTwoModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.FastModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.FinalSynthesizerModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.MainModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ThinkingModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.VisionModelEngine
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.feature.inference.InferenceFactoryImpl
import com.browntowndev.pocketcrew.feature.inference.LiteRtInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.LlamaInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.MediaPipeInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.NoOpInferenceService
import com.browntowndev.pocketcrew.feature.inference.LlmInferenceWrapper
import com.browntowndev.pocketcrew.feature.inference.llama.GpuConfig
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module for LiteRT engine and inference pipeline components.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindInferenceFactory(impl: InferenceFactoryImpl): InferenceFactoryPort

    companion object {
        private const val TAG = "EngineModule"

        private fun getModelPath(context: Context, filename: String): String {
            val modelsDir = File(context.filesDir, "models")
            return File(modelsDir, filename).absolutePath
        }

        private fun createEngine(modelPath: String): Engine {
            val config = EngineConfig(modelPath)
            return Engine(config).also {
                Log.i(TAG, "LiteRT Engine initialized at $modelPath")
            }
        }

        private fun createLlmInference(context: Context, modelPath: String): LlmInference {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(16384)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            return LlmInference.createFromOptions(context, options)
        }

        // Qualified Engine providers
        @Provides @Singleton @MainModelEngine
        fun provideMainModelEngine(@ApplicationContext context: Context, modelRegistry: ModelRegistryPort): Engine {
            val asset = modelRegistry.getRegisteredAssetSync(ModelType.MAIN) ?: throw IllegalStateException("No asset")
            return createEngine(getModelPath(context, asset.metadata.localFileName))
        }

        @Provides @Singleton @VisionModelEngine
        fun provideVisionModelEngine(@ApplicationContext context: Context, modelRegistry: ModelRegistryPort): Engine {
            val asset = modelRegistry.getRegisteredAssetSync(ModelType.VISION) ?: throw IllegalStateException("No asset")
            return createEngine(getModelPath(context, asset.metadata.localFileName))
        }

        @Provides @Singleton @DraftOneModelEngine
        fun provideDraftOneModelEngine(@ApplicationContext context: Context, modelRegistry: ModelRegistryPort): Engine {
            val asset = modelRegistry.getRegisteredAssetSync(ModelType.DRAFT_ONE) ?: throw IllegalStateException("No asset")
            return createEngine(getModelPath(context, asset.metadata.localFileName))
        }

        @Provides @Singleton @DraftTwoModelEngine
        fun provideDraftTwoModelEngine(@ApplicationContext context: Context, modelRegistry: ModelRegistryPort): Engine {
            val asset = modelRegistry.getRegisteredAssetSync(ModelType.DRAFT_TWO) ?: throw IllegalStateException("No asset")
            return createEngine(getModelPath(context, asset.metadata.localFileName))
        }

        @Provides @Singleton @FastModelEngine
        fun provideFastModelEngine(@ApplicationContext context: Context, modelRegistry: ModelRegistryPort): Engine {
            val asset = modelRegistry.getRegisteredAssetSync(ModelType.FAST) ?: throw IllegalStateException("No asset")
            return createEngine(getModelPath(context, asset.metadata.localFileName))
        }

        @Provides @Singleton @ThinkingModelEngine
        fun provideThinkingModelEngine(@ApplicationContext context: Context, modelRegistry: ModelRegistryPort): Engine {
            val asset = modelRegistry.getRegisteredAssetSync(ModelType.THINKING) ?: throw IllegalStateException("No asset")
            return createEngine(getModelPath(context, asset.metadata.localFileName))
        }

        @Provides @Singleton @FinalSynthesizerModelEngine
        fun provideFinalSynthesizerModelEngine(@ApplicationContext context: Context, modelRegistry: ModelRegistryPort): Engine {
            val asset = modelRegistry.getRegisteredAssetSync(ModelType.FINAL_SYNTHESIS) ?: throw IllegalStateException("No asset")
            return createEngine(getModelPath(context, asset.metadata.localFileName))
        }

        // ConversationManager providers
        @Provides @Singleton @MainModelEngine
        fun provideMainConversationManager(@MainModelEngine engine: Engine, modelRegistry: ModelRegistryPort): ConversationManagerPort =
            ConversationManagerImpl(engine, modelRegistry.getRegisteredConfigurationSync(ModelType.MAIN))

        @Provides @Singleton @VisionModelEngine
        fun provideVisionConversationManager(@VisionModelEngine engine: Engine, modelRegistry: ModelRegistryPort): ConversationManagerPort =
            ConversationManagerImpl(engine, modelRegistry.getRegisteredConfigurationSync(ModelType.VISION))

        @Provides @Singleton @DraftOneModelEngine
        fun provideDraftOneConversationManager(@DraftOneModelEngine engine: Engine, modelRegistry: ModelRegistryPort): ConversationManagerPort =
            ConversationManagerImpl(engine, modelRegistry.getRegisteredConfigurationSync(ModelType.DRAFT_ONE))

        @Provides @Singleton @DraftTwoModelEngine
        fun provideDraftTwoConversationManager(@DraftTwoModelEngine engine: Engine, modelRegistry: ModelRegistryPort): ConversationManagerPort =
            ConversationManagerImpl(engine, modelRegistry.getRegisteredConfigurationSync(ModelType.DRAFT_TWO))

        @Provides @Singleton @FastModelEngine
        fun provideFastConversationManager(@FastModelEngine engine: Engine, modelRegistry: ModelRegistryPort): ConversationManagerPort =
            ConversationManagerImpl(engine, modelRegistry.getRegisteredConfigurationSync(ModelType.FAST))

        @Provides @Singleton @ThinkingModelEngine
        fun provideThinkingConversationManager(@ThinkingModelEngine engine: Engine, modelRegistry: ModelRegistryPort): ConversationManagerPort =
            ConversationManagerImpl(engine, modelRegistry.getRegisteredConfigurationSync(ModelType.THINKING))

        @Provides @Singleton @FinalSynthesizerModelEngine
        fun provideFinalSynthesizerConversationManager(@FinalSynthesizerModelEngine engine: Engine, modelRegistry: ModelRegistryPort): ConversationManagerPort =
            ConversationManagerImpl(engine, modelRegistry.getRegisteredConfigurationSync(ModelType.FINAL_SYNTHESIS))

        // Inference Service providers
        @Provides @Singleton @MainModelEngine
        fun provideMainInferenceService(@ApplicationContext context: Context, @MainModelEngine conversationManager: Provider<ConversationManagerPort>, modelRegistry: ModelRegistryPort, processThinkingTokens: ProcessThinkingTokensUseCase, llamaChatSessionManager: LlamaChatSessionManager, loggingPort: LoggingPort): LlmInferencePort =
            createInferenceService(context, ModelType.MAIN, conversationManager, modelRegistry, processThinkingTokens, llamaChatSessionManager, loggingPort)

        @Provides @Singleton @VisionModelEngine
        fun provideVisionInferenceService(@ApplicationContext context: Context, @VisionModelEngine conversationManager: Provider<ConversationManagerPort>, modelRegistry: ModelRegistryPort, processThinkingTokens: ProcessThinkingTokensUseCase, llamaChatSessionManager: LlamaChatSessionManager, loggingPort: LoggingPort): LlmInferencePort =
            createInferenceService(context, ModelType.VISION, conversationManager, modelRegistry, processThinkingTokens, llamaChatSessionManager, loggingPort)

        @Provides @Singleton @DraftOneModelEngine
        fun provideDraftOneInferenceService(@ApplicationContext context: Context, @DraftOneModelEngine conversationManager: Provider<ConversationManagerPort>, modelRegistry: ModelRegistryPort, processThinkingTokens: ProcessThinkingTokensUseCase, llamaChatSessionManager: LlamaChatSessionManager, loggingPort: LoggingPort): LlmInferencePort =
            createInferenceService(context, ModelType.DRAFT_ONE, conversationManager, modelRegistry, processThinkingTokens, llamaChatSessionManager, loggingPort)

        @Provides @Singleton @DraftTwoModelEngine
        fun provideDraftTwoInferenceService(@ApplicationContext context: Context, @DraftTwoModelEngine conversationManager: Provider<ConversationManagerPort>, modelRegistry: ModelRegistryPort, processThinkingTokens: ProcessThinkingTokensUseCase, llamaChatSessionManager: LlamaChatSessionManager, loggingPort: LoggingPort): LlmInferencePort =
            createInferenceService(context, ModelType.DRAFT_TWO, conversationManager, modelRegistry, processThinkingTokens, llamaChatSessionManager, loggingPort)

        @Provides @Singleton @FastModelEngine
        fun provideFastInferenceService(@ApplicationContext context: Context, @FastModelEngine conversationManager: Provider<ConversationManagerPort>, modelRegistry: ModelRegistryPort, processThinkingTokens: ProcessThinkingTokensUseCase, llamaChatSessionManager: LlamaChatSessionManager, loggingPort: LoggingPort): LlmInferencePort =
            createInferenceService(context, ModelType.FAST, conversationManager, modelRegistry, processThinkingTokens, llamaChatSessionManager, loggingPort)

        @Provides @Singleton @ThinkingModelEngine
        fun provideThinkingInferenceService(@ApplicationContext context: Context, @ThinkingModelEngine conversationManager: Provider<ConversationManagerPort>, modelRegistry: ModelRegistryPort, processThinkingTokens: ProcessThinkingTokensUseCase, llamaChatSessionManager: LlamaChatSessionManager, loggingPort: LoggingPort): LlmInferencePort =
            createInferenceService(context, ModelType.THINKING, conversationManager, modelRegistry, processThinkingTokens, llamaChatSessionManager, loggingPort)

        @Provides @Singleton @FinalSynthesizerModelEngine
        fun provideFinalSynthesizerInferenceService(@ApplicationContext context: Context, @FinalSynthesizerModelEngine conversationManager: Provider<ConversationManagerPort>, modelRegistry: ModelRegistryPort, processThinkingTokens: ProcessThinkingTokensUseCase, llamaChatSessionManager: LlamaChatSessionManager, loggingPort: LoggingPort): LlmInferencePort =
            createInferenceService(context, ModelType.FINAL_SYNTHESIS, conversationManager, modelRegistry, processThinkingTokens, llamaChatSessionManager, loggingPort)

        private fun createInferenceService(
            context: Context,
            modelType: ModelType,
            conversationManager: Provider<ConversationManagerPort>,
            modelRegistry: ModelRegistryPort,
            processThinkingTokens: ProcessThinkingTokensUseCase,
            llamaChatSessionManager: LlamaChatSessionManager,
            loggingPort: LoggingPort
        ): LlmInferencePort {
            val asset = modelRegistry.getRegisteredAssetSync(modelType)
            val config = modelRegistry.getRegisteredConfigurationSync(modelType)

            if (asset == null || config == null) {
                Log.w(TAG, "No asset or config for $modelType, using NoOpInferenceService")
                return NoOpInferenceService(modelType)
            }

            val filename = asset.metadata.localFileName
            val modelPath = getModelPath(context, filename)

            return when {
                filename.endsWith(".gguf") -> {
                    val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, modelType, loggingPort)
                    val gpuConfig = GpuConfig.forDevice(context, asset.metadata.sizeInBytes, 32)
                    service.configure(
                        modelPath = modelPath,
                        systemPrompt = config.systemPrompt,
                        samplingConfig = LlamaSamplingConfig(
                            temperature = config.temperature.toFloat(),
                            topK = config.topK ?: 40,
                            topP = config.topP.toFloat(),
                            minP = config.minP.toFloat(),
                            maxTokens = config.maxTokens,
                            contextWindow = config.contextWindow,
                            batchSize = 256,
                            gpuLayers = gpuConfig.gpuLayers,
                            thinkingEnabled = config.thinkingEnabled,
                            repeatPenalty = config.repetitionPenalty.toFloat()
                        )
                    )
                    service
                }
                filename.endsWith(".task") -> MediaPipeInferenceServiceImpl(LlmInferenceWrapper(createLlmInference(context, modelPath)), modelType)
                else -> LiteRtInferenceServiceImpl(conversationManager.get(), processThinkingTokens, modelType)
            }
        }

        @Provides
        fun provideConversationManagerByType(
            @MainModelEngine main: Provider<ConversationManagerPort>,
            @VisionModelEngine vision: Provider<ConversationManagerPort>,
            @DraftOneModelEngine draftOne: Provider<ConversationManagerPort>,
            @DraftTwoModelEngine draftTwo: Provider<ConversationManagerPort>,
            @FastModelEngine fast: Provider<ConversationManagerPort>,
            @ThinkingModelEngine thinking: Provider<ConversationManagerPort>,
            @FinalSynthesizerModelEngine synthesis: Provider<ConversationManagerPort>
        ): (ModelType) -> ConversationManagerPort = { type ->
            when (type) {
                ModelType.MAIN -> main.get()
                ModelType.VISION -> vision.get()
                ModelType.DRAFT_ONE -> draftOne.get()
                ModelType.DRAFT_TWO -> draftTwo.get()
                ModelType.FAST -> fast.get()
                ModelType.THINKING -> thinking.get()
                ModelType.FINAL_SYNTHESIS -> synthesis.get()
            }
        }
    }
}