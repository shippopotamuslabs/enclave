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
     * Determines GPU availability by attempting GPU inference and falling back on failure.
     * In LiteRT 2.x, the library handles backend selection gracefully.
     */
    private fun createLlmInference(context: Context, modelPath: String): LlmInference {
        // Try GPU first, fall back to CPU on failure
        return try {
            Log.i(TAG, "Attempting GPU backend.")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(16384)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()

            LlmInference.createFromOptions(context, options).also {
                Log.i(TAG, "GPU Delegate supported! Using GPU backend.")
            }
        } catch (e: Exception) {
            Log.i(TAG, "GPU not supported, falling back to CPU: ${e.message}")
            val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(16384)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()

            LlmInference.createFromOptions(context, cpuOptions)
        }
    }

    private fun createEngine(modelPath: String): Engine {
        // Try GPU first, fall back to CPU on failure
        return try {
            Log.i(TAG, "Attempting GPU backend for LiteRT Engine.")
            val config = EngineConfig(
                modelPath,
                backend = Backend.GPU()
            )
            Engine(config).also {
                Log.i(TAG, "GPU Delegate supported! Using GPU backend for LiteRT Engine.")
            }
        } catch (e: Exception) {
            Log.i(TAG, "GPU not supported, falling back to CPU: ${e.message}")
            val config = EngineConfig(
                modelPath,
                backend = Backend.CPU()
            )
            Engine(config)
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
        return Engine(EngineConfig(getModelPath(context, filename)))
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
        return Engine(EngineConfig(getModelPath(context, filename)))
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
        return Engine(EngineConfig(getModelPath(context, filename)))
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
        return Engine(EngineConfig(getModelPath(context, filename)))
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
        return Engine(EngineConfig(getModelPath(context, filename)))
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
        return Engine(EngineConfig(getModelPath(context, filename)))
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
        val config = modelRegistry.getRegisteredModelSync(ModelType.MAIN)
            ?: throw IllegalStateException("No model registered for ${ModelType.MAIN}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)

        return when {
            filename.endsWith(".gguf") -> {
                // Use llama.cpp implementation for GGUF models
                val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, ModelType.MAIN, loggingPort)
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
                MediaPipeInferenceServiceImpl(createLlmInference(context, modelPath), ModelType.MAIN)
            }
            else -> {
                LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, ModelType.DRAFT_ONE)
            }
        }
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
        val config = modelRegistry.getRegisteredModelSync(ModelType.VISION)
            ?: throw IllegalStateException("No model registered for ${ModelType.VISION}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, ModelType.VISION, loggingPort)
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
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, ModelType.VISION)
        }
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
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE)
            ?: throw IllegalStateException("No model registered for ${ModelType.DRAFT_ONE}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, ModelType.DRAFT_ONE, loggingPort)
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
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, ModelType.DRAFT_ONE)
        }
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
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO)
            ?: throw IllegalStateException("No model registered for ${ModelType.DRAFT_TWO}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, ModelType.DRAFT_TWO, loggingPort)
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
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, ModelType.DRAFT_TWO)
        }
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
        val config = modelRegistry.getRegisteredModelSync(ModelType.FAST)
            ?: throw IllegalStateException("No model registered for ${ModelType.FAST}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, ModelType.FAST, loggingPort)
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
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, ModelType.FAST)
        }
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
        val config = modelRegistry.getRegisteredModelSync(ModelType.THINKING)
            ?: throw IllegalStateException("No model registered for ${ModelType.THINKING}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, ModelType.THINKING, loggingPort)
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
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, ModelType.THINKING)
        }
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
        // Uses the dedicated FINAL_SYNTHESIS model config
        val config = modelRegistry.getRegisteredModelSync(ModelType.FINAL_SYNTHESIS)
            ?: throw IllegalStateException("No model registered for ${ModelType.FINAL_SYNTHESIS}. Please download a model first.")

        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)

        return if (filename.endsWith(".gguf")) {
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens, ModelType.FINAL_SYNTHESIS, loggingPort)
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
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, ModelType.FINAL_SYNTHESIS)
        }
    }
}
