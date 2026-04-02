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
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
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
 * Inference services and ConversationManagers resolve models lazily from [ModelRegistryPort]
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

        // ConversationManager providers - resolve models lazily via ModelRegistry
        // Pass applicationScope to enable async Flow observation without runBlocking
        @Provides @Singleton @MainModelEngine
        fun provideMainConversationManager(
            @ApplicationContext context: Context,
            modelRegistry: ModelRegistryPort,
            @ApplicationScope applicationScope: CoroutineScope
        ): ConversationManagerPort = ConversationManagerImpl(context, ModelType.MAIN, modelRegistry, applicationScope)

        @Provides @Singleton @VisionModelEngine
        fun provideVisionConversationManager(
            @ApplicationContext context: Context,
            modelRegistry: ModelRegistryPort,
            @ApplicationScope applicationScope: CoroutineScope
        ): ConversationManagerPort = ConversationManagerImpl(context, ModelType.VISION, modelRegistry, applicationScope)

        @Provides @Singleton @DraftOneModelEngine
        fun provideDraftOneConversationManager(
            @ApplicationContext context: Context,
            modelRegistry: ModelRegistryPort,
            @ApplicationScope applicationScope: CoroutineScope
        ): ConversationManagerPort = ConversationManagerImpl(context, ModelType.DRAFT_ONE, modelRegistry, applicationScope)

        @Provides @Singleton @DraftTwoModelEngine
        fun provideDraftTwoConversationManager(
            @ApplicationContext context: Context,
            modelRegistry: ModelRegistryPort,
            @ApplicationScope applicationScope: CoroutineScope
        ): ConversationManagerPort = ConversationManagerImpl(context, ModelType.DRAFT_TWO, modelRegistry, applicationScope)

        @Provides @Singleton @FastModelEngine
        fun provideFastConversationManager(
            @ApplicationContext context: Context,
            modelRegistry: ModelRegistryPort,
            @ApplicationScope applicationScope: CoroutineScope
        ): ConversationManagerPort = ConversationManagerImpl(context, ModelType.FAST, modelRegistry, applicationScope)

        @Provides @Singleton @ThinkingModelEngine
        fun provideThinkingConversationManager(
            @ApplicationContext context: Context,
            modelRegistry: ModelRegistryPort,
            @ApplicationScope applicationScope: CoroutineScope
        ): ConversationManagerPort = ConversationManagerImpl(context, ModelType.THINKING, modelRegistry, applicationScope)

        @Provides @Singleton @FinalSynthesizerModelEngine
        fun provideFinalSynthesizerConversationManager(
            @ApplicationContext context: Context,
            modelRegistry: ModelRegistryPort,
            @ApplicationScope applicationScope: CoroutineScope
        ): ConversationManagerPort = ConversationManagerImpl(context, ModelType.FINAL_SYNTHESIS, modelRegistry, applicationScope)

        @Provides
        @JvmSuppressWildcards
        fun provideConversationManagerByType(
            @MainModelEngine main: ConversationManagerPort,
            @VisionModelEngine vision: ConversationManagerPort,
            @DraftOneModelEngine draftOne: ConversationManagerPort,
            @DraftTwoModelEngine draftTwo: ConversationManagerPort,
            @FastModelEngine fast: ConversationManagerPort,
            @ThinkingModelEngine thinking: ConversationManagerPort,
            @FinalSynthesizerModelEngine synthesis: ConversationManagerPort
        ): (ModelType) -> ConversationManagerPort = { type ->
            when (type) {
                ModelType.MAIN -> main
                ModelType.VISION -> vision
                ModelType.DRAFT_ONE -> draftOne
                ModelType.DRAFT_TWO -> draftTwo
                ModelType.FAST -> fast
                ModelType.THINKING -> thinking
                ModelType.FINAL_SYNTHESIS -> synthesis
            }
        }
    }
}