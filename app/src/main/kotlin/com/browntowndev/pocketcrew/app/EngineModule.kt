package com.browntowndev.pocketcrew.app

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
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
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.feature.inference.InferenceFactoryImpl
import com.browntowndev.pocketcrew.feature.inference.LiteRtInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.MediaPipeInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.LlamaInferenceServiceImpl
import com.browntowndev.pocketcrew.feature.inference.LlmInferenceWrapper
import com.browntowndev.pocketcrew.feature.inference.NoOpInferenceService
import com.browntowndev.pocketcrew.feature.inference.llama.GpuConfig
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module for LiteRT engine and inference pipeline components.
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
        private const val TAG = "EngineModule"

        /**
         * Provides the application-level CoroutineScope.
         * Uses [Dispatchers.Default] for CPU-friendly collection of flows.
         * Using Default instead of Main to avoid requiring a Looper in unit tests.
         */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private fun getModelPath(context: Context, filename: String): String {
            // Use getExternalFilesDir to match ModelFileScanner's directory choice
            val modelsDir = File(context.getExternalFilesDir(null), "models")
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

        // Consolidated ConversationManager provider - resolve models lazily via ModelRegistry
        // Each InferenceService gets its own manager instance to isolate Engine lifecycle per model file
        @Provides
        fun provideConversationManager(
            @ApplicationContext context: Context,
            localModelRepository: LocalModelRepositoryPort,
            activeModelProvider: ActiveModelProviderPort
        ): ConversationManagerPort = ConversationManagerImpl(context, localModelRepository, activeModelProvider)
    }
}
