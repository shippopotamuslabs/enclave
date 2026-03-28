package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.FastModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.ThinkingModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.MainModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.VisionModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.DraftOneModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.DraftTwoModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.FinalSynthesizerModelEngine
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.feature.inference.ConversationManagerImpl
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.feature.inference.LiteRtInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.LlamaInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.MediaPipeInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.LlmInferenceWrapper
import com.browntowndev.pocketcrew.feature.inference.llama.GpuConfig
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    private const val TAG = "EngineModule"

    /**
     * Resolves the model file path from the models directory.
     */
    private fun getModelPath(context: Context, filename: String): String {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)

        // Try to get the file path - if it doesn't exist, let LiteRT handle the error
        // This allows for graceful failure rather than blocking during DI
        val targetFile = File(modelsDir, filename)

        if (!targetFile.exists()) {
            Log.e(TAG, "Model file not found: ${targetFile.absolutePath}")
            throw IllegalStateException(
                "Model file not found: ${targetFile.absolutePath}. " +
                "Please download models from the download screen first."
            )
        }

        return targetFile.absolutePath
    }

    /**
     * Creates LlmInference using the GPU backend. 
     * LiteRT 2.x handles the internal fallback to CPU if GPU is unavailable.
     */
    private fun createLlmInference(context: Context, modelPath: String): LlmInferenceWrapper {
        Log.i(TAG, "Creating LlmInference with GPU preference (auto-fallback handled by SDK).")

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(16384)
            .setPreferredBackend(LlmInference.Backend.GPU)
            .build()

        return LlmInferenceWrapper(LlmInference.createFromOptions(context, options))
    }

    /**
     * Creates LiteRT Engine using explicit GPU preference.
     */
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun createEngine(modelPath: String): Engine {
        Log.i(TAG, "Creating LiteRT Engine with GPU backend (NPU flags initialized in Application).")

        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            // For Gemma 3n models, vision should match the primary backend
            visionBackend = Backend.GPU()
        )
        
        return Engine(config).also {
            Log.i(TAG, "LiteRT Engine initialized with EngineConfig (GPU backend).")
        }
    }

    // Qualified Engine providers - each binds to a specific model type
    @Provides
    @Singleton
    @MainModelEngine
    fun provideMainModelEngine(
        @ApplicationContext context: Context,
        modelRegistry: ModelRegistryPort
    ): Engine {
        val config = modelRegistry.getRegisteredModelSync(ModelType.MAIN)
            ?: throw IllegalStateException("No model registered for ${ModelType.MAIN}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    @Provides
    @Singleton
    @VisionModelEngine
    fun provideVisionModelEngine(
        @ApplicationContext context: Context,
        modelRegistry: ModelRegistryPort
    ): Engine {
        val config = modelRegistry.getRegisteredModelSync(ModelType.VISION)
            ?: throw IllegalStateException("No model registered for ${ModelType.VISION}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    @Provides
    @Singleton
    @DraftOneModelEngine
    fun provideDraftOneModelEngine(
        @ApplicationContext context: Context,
        modelRegistry: ModelRegistryPort
    ): Engine {
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE)
            ?: throw IllegalStateException("No model registered for ${ModelType.DRAFT_ONE}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    @Provides
    @Singleton
    @DraftTwoModelEngine
    fun provideDraftTwoModelEngine(
        @ApplicationContext context: Context,
        modelRegistry: ModelRegistryPort
    ): Engine {
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO)
            ?: throw IllegalStateException("No model registered for ${ModelType.DRAFT_TWO}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    @Provides
    @Singleton
    @FastModelEngine
    fun provideFastModelEngine(
        @ApplicationContext context: Context,
        modelRegistry: ModelRegistryPort
    ): Engine {
        val config = modelRegistry.getRegisteredModelSync(ModelType.FAST)
            ?: throw IllegalStateException("No model registered for ${ModelType.FAST}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    @Provides
    @Singleton
    @ThinkingModelEngine
    fun provideThinkingModelEngine(
        @ApplicationContext context: Context,
        modelRegistry: ModelRegistryPort
    ): Engine {
        val config = modelRegistry.getRegisteredModelSync(ModelType.THINKING)
            ?: throw IllegalStateException("No model registered for ${ModelType.THINKING}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    @Provides
    @Singleton
    @FinalSynthesizerModelEngine
    fun provideFinalSynthesizerModelEngine(
        @ApplicationContext context: Context,
        modelRegistry: ModelRegistryPort
    ): Engine {
        // Uses the dedicated FINAL_SYNTHESIS model
        val config = modelRegistry.getRegisteredModelSync(ModelType.FINAL_SYNTHESIS)
            ?: throw IllegalStateException("No model registered for ${ModelType.FINAL_SYNTHESIS}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    // Qualified ConversationManager providers - each is bound to a specific Engine
    @Provides
    @Singleton
    @MainModelEngine
    fun provideMainConversationManager(
        @MainModelEngine engine: Engine,
        modelRegistry: ModelRegistryPort
    ): ConversationManagerPort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.MAIN)
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @VisionModelEngine
    fun provideVisionConversationManager(
        @VisionModelEngine engine: Engine,
        modelRegistry: ModelRegistryPort
    ): ConversationManagerPort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.VISION)
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @DraftOneModelEngine
    fun provideDraftConversationManager(
        @DraftOneModelEngine engine: Engine,
        modelRegistry: ModelRegistryPort
    ): ConversationManagerPort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE)
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @DraftTwoModelEngine
    fun provideDraftTwoConversationManager(
        @DraftTwoModelEngine engine: Engine,
        modelRegistry: ModelRegistryPort
    ): ConversationManagerPort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO)
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @FastModelEngine
    fun provideFastConversationManager(
        @FastModelEngine engine: Engine,
        modelRegistry: ModelRegistryPort
    ): ConversationManagerPort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.FAST)
        Log.d(TAG, "Fast Config Sys Prompt: ${config?.persona?.systemPrompt}")
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @ThinkingModelEngine
    fun provideThinkingConversationManager(
        @ThinkingModelEngine engine: Engine,
        modelRegistry: ModelRegistryPort
    ): ConversationManagerPort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.THINKING)
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @FinalSynthesizerModelEngine
    fun provideFinalSynthesizerConversationManager(
        @FinalSynthesizerModelEngine engine: Engine,
        modelRegistry: ModelRegistryPort
    ): ConversationManagerPort {
        // Uses the dedicated FINAL_SYNTHESIS model config
        val config = modelRegistry.getRegisteredModelSync(ModelType.FINAL_SYNTHESIS)
        return ConversationManagerImpl(engine, config)
    }

    /**
     * Factory method for creating LlmInferencePort implementations based on model file extension.
     */
    private fun createInferenceService(
        context: Context,
        modelType: ModelType,
        conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        val config = modelRegistry.getRegisteredModelSync(modelType)
            ?: throw IllegalStateException("No model registered for $modelType. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)

        return when {
            filename.endsWith(".gguf") -> {
                // Use llama.cpp implementation for GGUF models
                val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, modelType, loggingPort)
                val tunings = config.tunings
                val gpuConfig = GpuConfig.forDevice(context, config.metadata.sizeInBytes, 32)

                service.configure(
                    modelPath = modelPath,
                    systemPrompt = config.persona.systemPrompt,
                    samplingConfig = LlamaSamplingConfig(
                        temperature = tunings.temperature.toFloat(),
                        topK = tunings.topK,
                        topP = tunings.topP.toFloat(),
                        minP = tunings.minP.toFloat(),
                        maxTokens = tunings.maxTokens,
                        contextWindow = tunings.contextWindow,
                        batchSize = 256,
                        gpuLayers = gpuConfig.gpuLayers,
                        thinkingEnabled = tunings.thinkingEnabled,
                        repeatPenalty = tunings.repetitionPenalty.toFloat()
                    )
                )
                service
            }
            filename.endsWith(".task") -> {
                // Use MediaPipe for .task files
                MediaPipeInferenceServiceImpl(createLlmInference(context, modelPath), modelType)
            }
            else -> {
                // Default to LiteRT Engine
                LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, modelType)
            }
        }
    }

    // LlmInferencePort providers - inject qualified ConversationManager
    @Provides
    @Singleton
    @MainModelEngine
    fun provideMainInferenceService(
        @ApplicationContext context: Context,
        @MainModelEngine conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        return createInferenceService(
            context, ModelType.MAIN, conversationManager, modelRegistry,
            processThinkingTokens, llamaChatSessionManager, loggingPort
        )
    }

    @Provides
    @Singleton
    @VisionModelEngine
    fun provideVisionInferenceService(
        @ApplicationContext context: Context,
        @VisionModelEngine conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        return createInferenceService(
            context, ModelType.VISION, conversationManager, modelRegistry,
            processThinkingTokens, llamaChatSessionManager, loggingPort
        )
    }

    @Provides
    @Singleton
    @DraftOneModelEngine
    fun provideDraftInferenceService(
        @ApplicationContext context: Context,
        @DraftOneModelEngine conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        return createInferenceService(
            context, ModelType.DRAFT_ONE, conversationManager, modelRegistry,
            processThinkingTokens, llamaChatSessionManager, loggingPort
        )
    }

    @Provides
    @Singleton
    @DraftTwoModelEngine
    fun provideDraftTwoInferenceService(
        @ApplicationContext context: Context,
        @DraftTwoModelEngine conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        return createInferenceService(
            context, ModelType.DRAFT_TWO, conversationManager, modelRegistry,
            processThinkingTokens, llamaChatSessionManager, loggingPort
        )
    }

    @Provides
    @Singleton
    @FastModelEngine
    fun provideFastInferenceService(
        @ApplicationContext context: Context,
        @FastModelEngine conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        return createInferenceService(
            context, ModelType.FAST, conversationManager, modelRegistry,
            processThinkingTokens, llamaChatSessionManager, loggingPort
        )
    }

    @Provides
    @Singleton
    @ThinkingModelEngine
    fun provideThinkingInferenceService(
        @ApplicationContext context: Context,
        @ThinkingModelEngine conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        return createInferenceService(
            context, ModelType.THINKING, conversationManager, modelRegistry,
            processThinkingTokens, llamaChatSessionManager, loggingPort
        )
    }

    @Provides
    @Singleton
    @FinalSynthesizerModelEngine
    fun provideFinalSynthesizerInferenceService(
        @ApplicationContext context: Context,
        @FinalSynthesizerModelEngine conversationManager: ConversationManagerPort,
        modelRegistry: ModelRegistryPort,
        processThinkingTokens: ProcessThinkingTokensUseCase,
        llamaChatSessionManager: LlamaChatSessionManager,
        loggingPort: LoggingPort
    ): LlmInferencePort {
        return createInferenceService(
            context, ModelType.FINAL_SYNTHESIS, conversationManager, modelRegistry,
            processThinkingTokens, llamaChatSessionManager, loggingPort
        )
    }

    @Provides
    @Singleton
    fun provideInferenceFactory(
        impl: com.browntowndev.pocketcrew.feature.inference.InferenceFactoryImpl
    ): com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort = impl
}
