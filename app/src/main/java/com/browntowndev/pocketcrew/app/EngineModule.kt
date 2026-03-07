package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.inference.ConversationManagerImpl
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.inference.LiteRtInferenceServiceImpl
import com.browntowndev.pocketcrew.inference.MediaPipeInferenceServiceImpl
import com.browntowndev.pocketcrew.inference.PipelineOrchestratorImpl
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
annotation class DraftModelEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class FastModelEngine

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

    private fun getFilenameForFormat(modelType: ModelType, format: ModelFileFormat): String {
        return "${modelType.name.lowercase()}.${format.extension.removePrefix(".")}"
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
        modelConfigCache: ModelConfigCachePort
    ): Engine {
        val config = modelConfigCache.getMainConfig()
        val filename = if (config != null) {
            getFilenameForFormat(ModelType.MAIN, config.metadata.modelFileFormat)
        } else {
            "${ModelType.MAIN.name.lowercase()}.litertlm"
        }
        val modelPath = getModelPath(context, filename)
        return createEngine(modelPath)
    }

    @Provides
    @Singleton
    @VisionModelEngine
    fun provideVisionModelEngine(
        @ApplicationContext context: Context,
        modelConfigCache: ModelConfigCachePort
    ): Engine {
        val config = modelConfigCache.getVisionConfig()
        val filename = if (config != null) {
            getFilenameForFormat(ModelType.VISION, config.metadata.modelFileFormat)
        } else {
            "${ModelType.VISION.name.lowercase()}.litertlm"
        }
        return Engine(EngineConfig(getModelPath(context, filename)))
    }

    @Provides
    @Singleton
    @DraftModelEngine
    fun provideDraftModelEngine(
        @ApplicationContext context: Context,
        modelConfigCache: ModelConfigCachePort
    ): Engine {
        val config = modelConfigCache.getDraftConfig()
        val filename = if (config != null) {
            getFilenameForFormat(ModelType.DRAFT, config.metadata.modelFileFormat)
        } else {
            "${ModelType.DRAFT.name.lowercase()}.litertlm"
        }
        return Engine(EngineConfig(getModelPath(context, filename)))
    }

    @Provides
    @Singleton
    @FastModelEngine
    fun provideFastModelEngine(
        @ApplicationContext context: Context,
        modelConfigCache: ModelConfigCachePort
    ): Engine {
        val config = modelConfigCache.getFastConfig()
        val filename = if (config != null) {
            getFilenameForFormat(ModelType.FAST, config.metadata.modelFileFormat)
        } else {
            "${ModelType.FAST.name.lowercase()}.litertlm"
        }
        return Engine(EngineConfig(getModelPath(context, filename)))
    }

    // Qualified ConversationManager providers - each is bound to a specific Engine
    @Provides
    @Singleton
    @MainModelEngine
    fun provideMainConversationManager(
        @MainModelEngine engine: Engine,
        modelConfigCache: ModelConfigCachePort
    ): ConversationManagerPort {
        val config = modelConfigCache.getMainConfig()
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @VisionModelEngine
    fun provideVisionConversationManager(
        @VisionModelEngine engine: Engine,
        modelConfigCache: ModelConfigCachePort
    ): ConversationManagerPort {
        val config = modelConfigCache.getVisionConfig()
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @DraftModelEngine
    fun provideDraftConversationManager(
        @DraftModelEngine engine: Engine,
        modelConfigCache: ModelConfigCachePort
    ): ConversationManagerPort {
        val config = modelConfigCache.getDraftConfig()
        return ConversationManagerImpl(engine, config)
    }

    @Provides
    @Singleton
    @FastModelEngine
    fun provideFastConversationManager(
        @FastModelEngine engine: Engine,
        modelConfigCache: ModelConfigCachePort
    ): ConversationManagerPort {
        val config = modelConfigCache.getFastConfig()
        Log.d(TAG, "Fast Config Sys Prompt: ${config?.persona?.systemPrompt}")
        return ConversationManagerImpl(engine, config)
    }

    // LlmInferencePort providers - inject qualified ConversationManager
    @Provides
    @Singleton
    @MainModelEngine
    fun provideMainInferenceService(
        @ApplicationContext context: Context,
        @MainModelEngine conversationManager: ConversationManagerPort,
        modelConfigCache: ModelConfigCachePort,
        processThinkingTokens: ProcessThinkingTokensUseCase
    ): LlmInferencePort {
        val config = modelConfigCache.getMainConfig()
        val filename = if (config != null) {
            getFilenameForFormat(ModelType.MAIN, config.metadata.modelFileFormat)
        } else {
            "${ModelType.MAIN.name.lowercase()}.litertlm"
        }
        val modelPath = getModelPath(context, filename)
        return if (filename.endsWith(".task")) {
            MediaPipeInferenceServiceImpl(createLlmInference(context, modelPath))
        } else {
            LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
        }
    }

    @Provides
    @Singleton
    @VisionModelEngine
    fun provideVisionInferenceService(
        @VisionModelEngine conversationManager: ConversationManagerPort,
        processThinkingTokens: ProcessThinkingTokensUseCase
    ): LlmInferencePort {
        return LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
    }

    @Provides
    @Singleton
    @DraftModelEngine
    fun provideDraftInferenceService(
        @DraftModelEngine conversationManager: ConversationManagerPort,
        processThinkingTokens: ProcessThinkingTokensUseCase
    ): LlmInferencePort {
        return LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
    }

    @Provides
    @Singleton
    @FastModelEngine
    fun provideFastInferenceService(
        @FastModelEngine conversationManager: ConversationManagerPort,
        processThinkingTokens: ProcessThinkingTokensUseCase
    ): LlmInferencePort {
        return LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EnginePipelineModule {
    @Binds
    @Singleton
    abstract fun bindEnginePipelineOrchestrator(
        impl: PipelineOrchestratorImpl
    ): EnginePipelineOrchestrator
}
