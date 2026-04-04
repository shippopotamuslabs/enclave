package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class MediaPipeInferenceServiceFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun create(modelPath: String): LlmInferencePort {
        val options = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(16384)
            .setPreferredBackend(com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend.GPU)
            .build()
        return MediaPipeInferenceServiceImpl(
            LlmInferenceWrapper(com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(context, options))
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
    private val modelRegistry: ModelRegistryPort,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    private val llamaChatSessionManagerProvider: Provider<LlamaChatSessionManager>,
    private val conversationManagerProvider: Provider<ConversationManagerPort>,
    private val mediaPipeFactory: MediaPipeInferenceServiceFactory,
    private val loggingPort: LoggingPort,
    private val inferenceLockManager: InferenceLockManager,
) : InferenceFactoryPort {

    companion object {
        private const val TAG = "InferenceFactory"
    }

    private var activeIdentity: String? = null
    private var activeService: LlmInferencePort? = null

    private val mutex = Mutex()

    override suspend fun <T> withInferenceService(
        modelType: ModelType,
        block: suspend (LlmInferencePort) -> T
    ): T {
        if (!inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)) {
            throw InferenceBusyException()
        }

        try {
            val service = resolveService(modelType)
            return block(service)
        } finally {
            inferenceLockManager.releaseLock()
        }
    }

    private suspend fun resolveService(modelType: ModelType): LlmInferencePort {
        val asset = try {
            modelRegistry.getRegisteredAsset(modelType)
        } catch (e: Exception) {
            loggingPort.error(TAG, "Failed to fetch registered asset.", e)
            null
        }
        if (asset == null) {
            loggingPort.warning(TAG, "No asset for $modelType, using NoOpInferenceService")
            return NoOpInferenceService(modelType)
        }
        val filename = asset.metadata.localFileName
        val extension = filename.substringAfterLast('.', "")
        val requestedIdentity = "${asset.metadata.sha256}-$extension"

        return mutex.withLock {
            activeService?.let { currentService ->
                val currentIdentity = activeIdentity
                if (currentIdentity == requestedIdentity) {
                    return currentService
                }

                currentService.closeSession()
                activeService = null
                activeIdentity = null
            }

            val newService = when (extension) {
                "gguf" -> {
                    LlamaInferenceServiceImpl(
                        sessionManager = llamaChatSessionManagerProvider.get(),
                        processThinkingTokens = processThinkingTokens,
                        loggingPort = loggingPort,
                        modelRegistry = modelRegistry,
                        context = context
                    )
                }
                "task" -> {
                    val modelPath = getModelPath(filename)
                    mediaPipeFactory.create(modelPath)
                }
                else -> {
                    LiteRtInferenceServiceImpl(
                        conversationManager = conversationManagerProvider.get(),
                        processThinkingTokens = processThinkingTokens
                    )
                }
            }

            activeService = newService
            activeIdentity = requestedIdentity
            return newService
        }
    }

    private fun getModelPath(filename: String): String {
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }
}
