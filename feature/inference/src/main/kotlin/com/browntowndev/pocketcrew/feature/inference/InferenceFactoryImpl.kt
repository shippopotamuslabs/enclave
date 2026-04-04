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
) : InferenceFactoryPort {

    companion object {
        private const val TAG = "InferenceFactory"
    }

    /** Cache keyed by SHA-identity (sha256-extension). Services are shared across ModelTypes with same identity. */
    private val serviceCache = mutableMapOf<String, LlmInferencePort>()
    /** Reference count per SHA-identity. Ensures engine stays alive while any ModelType still uses it. */
    private val refCounts = mutableMapOf<String, Int>()
    
    // Maps a service instance to its SHA-identity key for robust refcounting
    private val serviceToIdentity = mutableMapOf<LlmInferencePort, String>()

    private val mutex = Mutex()

    override suspend fun registerUsage(service: LlmInferencePort) {
        mutex.withLock {
            val identity = serviceToIdentity[service] ?: return@withLock
            val current = refCounts[identity] ?: 0
            refCounts[identity] = current + 1
        }
    }

    override suspend fun releaseUsage(service: LlmInferencePort) {
        mutex.withLock {
            val identity = serviceToIdentity[service] ?: return@withLock
            
            val current = refCounts[identity] ?: 0
            val newCount = (current - 1).coerceAtLeast(0)

            if (newCount == 0) {
                // Keep the service cached while idle so subsequent requests can reuse the
                // underlying engine instead of recreating it on every mode switch.
                refCounts.remove(identity)
            } else {
                refCounts[identity] = newCount
            }
        }
    }

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
                return@withLock NoOpInferenceService(modelType)
            }

            val filename = asset.metadata.localFileName
            val extension = filename.substringAfterLast('.', "")
            val assetIdentity = "${asset.metadata.sha256}-$extension"

            // Return cached service if it exists. 
            serviceCache[assetIdentity]?.let { cached ->
                return@withLock cached
            }

            // Create new service and cache it by SHA-identity
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

            // Associate this instance with its identity for refcounting
            serviceToIdentity[newService] = assetIdentity
            serviceCache[assetIdentity] = newService
            return@withLock newService
        }
    }

    private fun getModelPath(filename: String): String {
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }
}
