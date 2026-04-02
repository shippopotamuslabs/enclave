package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipeInferenceServiceFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun create(modelPath: String, modelType: ModelType): LlmInferencePort {
        val options = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(16384)
            .setPreferredBackend(com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend.GPU)
            .build()
        return MediaPipeInferenceServiceImpl(
            LlmInferenceWrapper(com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(context, options)),
            modelType
        )
    }
}

/**
 * Dynamic InferenceFactoryPort implementation.
 * Resolves the correct inference service (Llama, MediaPipe, LiteRT) dynamically
 * based on the active model's file extension at inference time.
 */
class InferenceFactoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val conversationManagerProvider: @JvmSuppressWildcards (ModelType) -> ConversationManagerPort,
    private val modelRegistry: ModelRegistryPort,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    private val llamaChatSessionManager: LlamaChatSessionManager,
    private val mediaPipeFactory: MediaPipeInferenceServiceFactory,
    private val loggingPort: LoggingPort,
) : InferenceFactoryPort {

    companion object {
        private const val TAG = "InferenceFactory"
    }

    private val cachedServices = mutableMapOf<ModelType, LlmInferencePort>()
    private val cachedFileExtensions = mutableMapOf<ModelType, String?>()
    private val mutex = Mutex()

    override suspend fun getInferenceService(modelType: ModelType): LlmInferencePort {
        return mutex.withLock {
            val asset = try {
                modelRegistry.getRegisteredAsset(modelType)
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to fetch registered asset.", e)
                null
            }

            if (asset == null) {
                loggingPort.warning(TAG, "No asset for $modelType, using NoOpInferenceService")
                val oldService = cachedServices[modelType]
                if (oldService !is NoOpInferenceService) {
                    oldService?.closeSession()
                    val newService = NoOpInferenceService(modelType)
                    cachedServices[modelType] = newService
                    cachedFileExtensions[modelType] = null
                    return@withLock newService
                }
                return@withLock oldService
            }

            val filename = asset.metadata.localFileName
            val extension = filename.substringAfterLast('.', "")

            val currentService = cachedServices[modelType]
            val currentExtension = cachedFileExtensions[modelType]

            // If we already have a service and the extension hasn't changed, return it
            if (currentService != null && currentExtension == extension && currentService !is NoOpInferenceService) {
                return@withLock currentService
            }

            // Implementation type needs to change (or it's the first time)
            currentService?.closeSession()

            val newService = when (extension) {
                "gguf" -> {
                    LlamaInferenceServiceImpl(
                        llamaChatSessionManager,
                        processThinkingTokens,
                        modelType,
                        loggingPort,
                        modelRegistry,
                        context
                    )
                }
                "task" -> {
                    val modelPath = getModelPath(filename)
                    mediaPipeFactory.create(modelPath, modelType)
                }
                else -> {
                    LiteRtInferenceServiceImpl(
                        conversationManagerProvider(modelType),
                        processThinkingTokens,
                        modelType
                    )
                }
            }

            cachedServices[modelType] = newService
            cachedFileExtensions[modelType] = extension
            newService
        }
    }

    private fun getModelPath(filename: String): String {
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }
}
