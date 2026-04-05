package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class MediaPipeInferenceServiceFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val activeModelProvider: ActiveModelProviderPort,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
) {
    fun create(modelPath: String, modelType: ModelType): LlmInferencePort {
        val options = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(16384)
            .setPreferredBackend(com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend.GPU)
            .build()
        return MediaPipeInferenceServiceImpl(
            LlmInferenceWrapper(com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(context, options)),
            modelType,
            activeModelProvider,
            processThinkingTokens,
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
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val localModelRepository: LocalModelRepositoryPort,
    private val activeModelProvider: ActiveModelProviderPort,
    private val apiModelRepository: ApiModelRepositoryPort,
    private val apiKeyProvider: ApiKeyProviderPort,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    private val llamaChatSessionManagerProvider: Provider<LlamaChatSessionManager>,
    private val conversationManagerProvider: Provider<ConversationManagerPort>,
    private val mediaPipeFactory: MediaPipeInferenceServiceFactory,
    private val openAiClientProvider: OpenAiClientProvider,
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
        val defaultAssignment = defaultModelRepository.getDefault(modelType)
        if (defaultAssignment == null) {
            loggingPort.warning(TAG, "No default assignment for $modelType, using NoOpInferenceService")
            return NoOpInferenceService(modelType)
        }

        val apiConfigId = defaultAssignment.apiConfigId
        if (apiConfigId != null) {
            val apiConfig = apiModelRepository.getConfigurationById(apiConfigId)
            val apiCreds = apiConfig?.let { apiModelRepository.getCredentialsById(it.apiCredentialsId) }
            val apiKey = apiCreds?.let { apiKeyProvider.getApiKey(it.credentialAlias) }

            if (apiConfig == null || apiCreds == null || apiKey == null) {
                loggingPort.error(TAG, "Missing API configuration or credentials for $modelType. Config: ${apiConfig?.id}, Creds: ${apiCreds?.id}, Key present: ${apiKey != null}")
                // Fallback to on-device logic below if we somehow have both set, though UI shouldn't allow this
            } else {
                val requestedIdentity = "api-${apiConfig.id}"
                
                return mutex.withLock {
                    activeService?.let { currentService ->
                        if (activeIdentity == requestedIdentity) {
                            return currentService
                        }
                        currentService.closeSession()
                    }

                    val newService = ApiInferenceServiceImpl(
                        client = openAiClientProvider.getClient(
                            apiKey = apiKey,
                            baseUrl = apiCreds.baseUrl
                        ),
                        modelId = apiCreds.modelId,
                        provider = apiCreds.provider.name,
                        modelType = modelType
                    )

                    activeService = newService
                    activeIdentity = requestedIdentity
                    return newService
                }
            }
        }

        val localConfigId = defaultAssignment.localConfigId
        if (localConfigId == null) {
            loggingPort.warning(TAG, "No local or API config for $modelType, using NoOpInferenceService")
            return NoOpInferenceService(modelType)
        }

        val asset = try {
            localModelRepository.getAssetByConfigId(localConfigId)
        } catch (e: Exception) {
            loggingPort.error(TAG, "Failed to fetch registered asset.", e)
            null
        }
        
        if (asset == null) {
            loggingPort.warning(TAG, "No asset found for config $localConfigId ($modelType), using NoOpInferenceService")
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
                        localModelRepository = localModelRepository,
                        activeModelProvider = activeModelProvider,
                        context = context,
                        modelType = modelType
                    )
                }
                "task" -> {
                    val modelPath = getModelPath(filename)
                    mediaPipeFactory.create(modelPath, modelType)
                }
                else -> {
                    LiteRtInferenceServiceImpl(
                        conversationManager = conversationManagerProvider.get(),
                        processThinkingTokens = processThinkingTokens,
                        modelType = modelType
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
