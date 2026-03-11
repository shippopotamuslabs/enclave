package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.inference.ConversationManagerImpl
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.inference.LiteRtInferenceServiceImpl
import com.browntowndev.pocketcrew.inference.LlamaInferenceServiceImpl
import com.browntowndev.pocketcrew.inference.MediaPipeInferenceServiceImpl
import com.browntowndev.pocketcrew.inference.llama.GpuConfig
import com.browntowndev.pocketcrew.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.inference.llama.LlamaSamplingConfig
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
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationTarget

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class MainModelEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class VisionModelEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class DraftOneModelEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class DraftTwoModelEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class FastModelEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class ThinkingModelEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class FinalSynthesizerModelEngine

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    private const val TAG = "EngineModule"

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
     * Determines GPU availability by attempting to create a GPU backend.
     * In LiteRT 2.x, the library handles backend selection gracefully.
     * @return true if GPU delegate is supported on this device
     */
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
                backend = Backend.GPU,
            )
            Engine(config).also {
                Log.i(TAG, "GPU Delegate supported! Using GPU backend for LiteRT Engine.")
            }
        } catch (e: Exception) {
            Log.i(TAG, "GPU not supported, falling back to CPU: ${e.message}")
            val config = EngineConfig(
                modelPath,
                backend = Backend.CPU,
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
        // Uses the same model as MAIN for final synthesis
        val config = modelRegistry.getRegisteredModelSync(ModelType.MAIN)
            ?: throw IllegalStateException("No model registered for ${ModelType.MAIN}. Please download a model first.")
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
        // Uses the same model config as MAIN for final synthesis
        val config = modelRegistry.getRegisteredModelSync(ModelType.MAIN)
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
        llamaChatSessionManager: LlamaChatSessionManager
    ): LlmInferencePort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.MAIN)
            ?: throw IllegalStateException("No model registered for ${ModelType.MAIN}. Please download a model first.")
        val filename = config.metadata.localFileName
        val modelPath = getModelPath(context, filename)

        return when {
            filename.endsWith(".gguf") -> {
                // Use llama.cpp implementation for GGUF models
                val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens)
                val tunings = config.tunings
                val gpuConfig = GpuConfig.forDevice(context)

                service.configure(
                    modelPath = modelPath,
                    systemPrompt = config.persona.systemPrompt,
                    samplingConfig = LlamaSamplingConfig(
                        temperature = tunings.temperature.toFloat(),
                        topK = tunings.topK,
                        topP = tunings.topP.toFloat(),
                        maxTokens = tunings.maxTokens,
                        contextWindow = tunings.contextWindow,
                        threads = 4,
                        batchSize = 256,
                        gpuLayers = gpuConfig.gpuLayers,
                    )
                )
                service
            }
            filename.endsWith(".task") -> {
                MediaPipeInferenceServiceImpl(createLlmInference(context, modelPath))
            }
            else -> {
                LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
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
        llamaChatSessionManager: LlamaChatSessionManager
    ): LlmInferencePort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.VISION)
            ?: throw IllegalStateException("No model registered for ${ModelType.VISION}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens)
            val tunings = config.tunings
            val gpuConfig = GpuConfig.forDevice(context)

            service.configure(
                modelPath = modelPath,
                systemPrompt = config.persona.systemPrompt,
                samplingConfig = LlamaSamplingConfig(
                    temperature = tunings.temperature.toFloat(),
                    topK = tunings.topK,
                    topP = tunings.topP.toFloat(),
                    maxTokens = tunings.maxTokens,
                    contextWindow = tunings.contextWindow,
                    threads = 4,
                    batchSize = 256,
                    gpuLayers = gpuConfig.gpuLayers,
                    thinkingEnabled = tunings.thinkingEnabled
                )
            )
            service
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
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
        llamaChatSessionManager: LlamaChatSessionManager
    ): LlmInferencePort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE)
            ?: throw IllegalStateException("No model registered for ${ModelType.DRAFT_ONE}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens)
            val tunings = config.tunings
            val gpuConfig = GpuConfig.forDevice(context)

            service.configure(
                modelPath = modelPath,
                systemPrompt = config.persona.systemPrompt,
                samplingConfig = LlamaSamplingConfig(
                    temperature = tunings.temperature.toFloat(),
                    topK = tunings.topK,
                    topP = tunings.topP.toFloat(),
                    maxTokens = tunings.maxTokens,
                    contextWindow = tunings.contextWindow,
                    threads = 4,
                    batchSize = 256,
                    gpuLayers = gpuConfig.gpuLayers,
                    thinkingEnabled = tunings.thinkingEnabled
                )
            )
            service
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
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
        llamaChatSessionManager: LlamaChatSessionManager
    ): LlmInferencePort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO)
            ?: throw IllegalStateException("No model registered for ${ModelType.DRAFT_TWO}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens)
            val tunings = config.tunings
            val gpuConfig = GpuConfig.forDevice(context)

            service.configure(
                modelPath = modelPath,
                systemPrompt = config.persona.systemPrompt,
                samplingConfig = LlamaSamplingConfig(
                    temperature = tunings.temperature.toFloat(),
                    topK = tunings.topK,
                    topP = tunings.topP.toFloat(),
                    maxTokens = tunings.maxTokens,
                    contextWindow = tunings.contextWindow,
                    threads = 4,
                    batchSize = 256,
                    gpuLayers = gpuConfig.gpuLayers,
                    thinkingEnabled = tunings.thinkingEnabled
                )
            )
            service
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
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
        llamaChatSessionManager: LlamaChatSessionManager
    ): LlmInferencePort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.FAST)
            ?: throw IllegalStateException("No model registered for ${ModelType.FAST}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens)
            val tunings = config.tunings
            val gpuConfig = GpuConfig.forDevice(context)

            service.configure(
                modelPath = modelPath,
                systemPrompt = config.persona.systemPrompt,
                samplingConfig = LlamaSamplingConfig(
                    temperature = tunings.temperature.toFloat(),
                    topK = tunings.topK,
                    topP = tunings.topP.toFloat(),
                    maxTokens = tunings.maxTokens,
                    contextWindow = tunings.contextWindow,
                    threads = 4,
                    batchSize = 256,
                    gpuLayers = gpuConfig.gpuLayers,
                    thinkingEnabled = tunings.thinkingEnabled
                )
            )
            service
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
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
        llamaChatSessionManager: LlamaChatSessionManager
    ): LlmInferencePort {
        val config = modelRegistry.getRegisteredModelSync(ModelType.THINKING)
            ?: throw IllegalStateException("No model registered for ${ModelType.THINKING}. Please download a model first.")
        val filename = config.metadata.localFileName

        return if (filename.endsWith(".gguf")) {
            val modelPath = getModelPath(context, filename)
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens)
            val tunings = config.tunings
            val gpuConfig = GpuConfig.forDevice(context)

            service.configure(
                modelPath = modelPath,
                systemPrompt = config.persona.systemPrompt,
                samplingConfig = LlamaSamplingConfig(
                    temperature = tunings.temperature.toFloat(),
                    topK = tunings.topK,
                    topP = tunings.topP.toFloat(),
                    maxTokens = tunings.maxTokens,
                    contextWindow = tunings.contextWindow,
                    threads = 4,
                    batchSize = 256,
                    gpuLayers = gpuConfig.gpuLayers,
                    thinkingEnabled = tunings.thinkingEnabled
                )
            )
            service
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
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
        llamaChatSessionManager: LlamaChatSessionManager
    ): LlmInferencePort {
        // Uses the same model as MAIN but with final review system prompt
        val mainConfig = modelRegistry.getRegisteredModelSync(ModelType.MAIN)
            ?: throw IllegalStateException("No model registered for ${ModelType.MAIN}. Please download a model first.")

        // Get FAST model's system prompt for final synthesizer persona
        val fastConfig = modelRegistry.getRegisteredModelSync(ModelType.FAST)
        val fastSystemPrompt = fastConfig?.persona?.systemPrompt ?: "You are a helpful assistant."

        val finalSynthesizerSystemPrompt = buildFinalSynthesizerSystemPrompt(fastSystemPrompt)

        val filename = mainConfig.metadata.localFileName
        val modelPath = getModelPath(context, filename)

        return if (filename.endsWith(".gguf")) {
            val service = LlamaInferenceServiceImpl(llamaChatSessionManager, processThinkingTokens)
            val tunings = mainConfig.tunings
            val gpuConfig = GpuConfig.forDevice(context)

            service.configure(
                modelPath = modelPath,
                systemPrompt = finalSynthesizerSystemPrompt,
                samplingConfig = LlamaSamplingConfig(
                    temperature = tunings.temperature.toFloat(),
                    topK = tunings.topK,
                    topP = tunings.topP.toFloat(),
                    maxTokens = tunings.maxTokens,
                    contextWindow = tunings.contextWindow,
                    threads = 4,
                    batchSize = 256,
                    gpuLayers = gpuConfig.gpuLayers,
                    thinkingEnabled = tunings.thinkingEnabled
                )
            )
            service
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
        }
    }

    /**
     * Builds the system prompt for the final synthesizer.
     * This is the final review pass that produces the user-facing answer.
     * Custom user prompts are injected via the orchestrator prompt.
     */
    private fun buildFinalSynthesizerSystemPrompt(fastSystemPrompt: String): String {
        return """
You are the final reply model in a multi-stage reasoning pipeline.

When you receive TASK: FINAL_REVIEW_AND_REPLY, your job is to:
1. Read the ORIGINAL_USER_PROMPT.
2. Critically evaluate the CANDIDATE_ANSWER.
3. Preserve what is strong.
4. Fix what is weak, unclear, verbose, inaccurate, or poorly matched to the user's request.
5. Produce the final answer for the user.

Do not merely paraphrase the CANDIDATE_ANSWER. Improve it where needed.
Follow any user-configured standing instructions provided in the USER_SYSTEM_PROMPT unless they conflict with the explicit task above.
USER_SYSTEM_PROMPT: $fastSystemPrompt
        """.trimIndent()
    }
}
