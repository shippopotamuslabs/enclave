package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.core.data.anthropic.AnthropicClientProvider
import com.browntowndev.pocketcrew.core.data.google.GoogleGenAiClientProvider
import com.browntowndev.pocketcrew.core.data.openai.OpenAiClientProvider
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
import com.browntowndev.pocketcrew.feature.inference.llama.GpuProfiler
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaBackend
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
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
    private val gpuProfiler: GpuProfiler,
) {
    suspend fun create(modelPath: String, modelType: ModelType): LlmInferencePort {
        val activeConfig = activeModelProvider.getActiveConfiguration(modelType)
        val contextWindow = activeConfig?.contextWindow ?: activeConfig?.maxTokens ?: 16384
        val detectedBackend = gpuProfiler.detectOptimalBackend()
        val preferredBackend = if (detectedBackend != LlamaBackend.CPU) {
            LlmInference.Backend.GPU
        } else {
            LlmInference.Backend.CPU
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(contextWindow)
            .setPreferredBackend(preferredBackend)
            .build()
        return MediaPipeInferenceServiceImpl(
            LlmInferenceWrapper(LlmInference.createFromOptions(context, options)),
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
    private val anthropicClientProvider: AnthropicClientProvider,
    private val googleGenAiClientProvider: GoogleGenAiClientProvider,
    private val loggingPort: LoggingPort,
    private val inferenceLockManager: InferenceLockManager,
) : InferenceFactoryPort {

    companion object {
        private const val TAG = "InferenceFactory"
    }

    private sealed interface ServiceIdentity {
        data class Api(
            val modelType: ModelType,
            val configId: Long,
            val credentialsId: Long,
            val provider: ApiProvider,
            val modelId: String,
            val baseUrl: String?,
            val reasoningEffort: String?,
            val openRouterRouting: OpenRouterRoutingIdentity,
            val googleApiVersion: String?,
            val headersHash: String,
            val apiKeyHash: String,
        ) : ServiceIdentity

        data class Local(
            val modelSha256: String,
            val extension: String,
            val localConfigId: Long,
        ) : ServiceIdentity
    }

    private data class OpenRouterRoutingIdentity(
        val providerSort: String,
        val allowFallbacks: Boolean,
        val requireParameters: Boolean,
        val dataCollectionPolicy: String,
        val zeroDataRetention: Boolean,
    )

    private val activeIdentities = mutableMapOf<ModelType, ServiceIdentity>()
    private val activeServices = mutableMapOf<ModelType, LlmInferencePort>()

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
                val requestHeaders = buildRequestHeaders(apiCreds.provider, apiConfig.customHeaders)
                val resolvedBaseUrl = apiCreds.baseUrl?.takeIf { it.isNotBlank() } ?: apiCreds.provider.defaultBaseUrl()
                val requestedIdentity = createApiIdentity(
                    modelType = modelType,
                    apiConfig = apiConfig,
                    apiCreds = apiCreds,
                    apiKey = apiKey,
                    requestHeaders = requestHeaders,
                    resolvedBaseUrl = resolvedBaseUrl
                )
                
                return mutex.withLock {
                    if (activeIdentities[modelType] == requestedIdentity) {
                        return@withLock activeServices[modelType]!!
                    }
                    
                    removeServiceFor(modelType)

                    val newService = when (apiCreds.provider) {
                        ApiProvider.ANTHROPIC -> AnthropicInferenceServiceImpl(
                            client = anthropicClientProvider.getClient(
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                                headers = requestHeaders
                            ),
                            modelId = apiCreds.modelId,
                            modelType = modelType,
                            baseUrl = resolvedBaseUrl,
                            loggingPort = loggingPort
                        )
                        ApiProvider.GOOGLE -> GoogleInferenceServiceImpl(
                            client = googleGenAiClientProvider.getClient(
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                                headers = requestHeaders,
                            ),
                            modelId = apiCreds.modelId,
                            modelType = modelType,
                            baseUrl = resolvedBaseUrl,
                            loggingPort = loggingPort
                        )
                        ApiProvider.OPENROUTER -> OpenRouterInferenceServiceImpl(
                            client = openAiClientProvider.getClient(
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                                headers = requestHeaders
                            ),
                            modelId = apiCreds.modelId,
                            modelType = modelType,
                            routing = apiConfig.openRouterRouting,
                            baseUrl = resolvedBaseUrl,
                            loggingPort = loggingPort
                        )
                        ApiProvider.XAI -> XaiInferenceServiceImpl(
                            client = openAiClientProvider.getClient(
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                                headers = requestHeaders
                            ),
                            modelId = apiCreds.modelId,
                            modelType = modelType,
                            baseUrl = resolvedBaseUrl,
                            loggingPort = loggingPort
                        )
                        else -> ApiInferenceServiceImpl(
                            client = openAiClientProvider.getClient(
                                apiKey = apiKey,
                                baseUrl = resolvedBaseUrl,
                                headers = requestHeaders
                            ),
                            modelId = apiCreds.modelId,
                            provider = apiCreds.provider.name,
                            modelType = modelType,
                            baseUrl = resolvedBaseUrl,
                            loggingPort = loggingPort
                        )
                    }
 
                    activeServices[modelType] = newService
                    activeIdentities[modelType] = requestedIdentity
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
        val requestedIdentity = ServiceIdentity.Local(
            modelSha256 = asset.metadata.sha256,
            extension = extension,
            localConfigId = localConfigId
        )
 
        return mutex.withLock {
            if (activeIdentities[modelType] == requestedIdentity) {
                return@withLock activeServices[modelType]!!
            }

            // Check if ANY other modelType is already using this exact local identity
            val existingEntry = activeIdentities.entries.find { it.value == requestedIdentity }
            if (existingEntry != null) {
                val existingService = activeServices[existingEntry.key]!!
                removeServiceFor(modelType)
                activeServices[modelType] = existingService
                activeIdentities[modelType] = requestedIdentity
                return@withLock existingService
            }

            // Enforce single local engine constraint to prevent OOM
            val localIdentitiesToClose = activeIdentities
                .filterValues { it is ServiceIdentity.Local }
                .keys
                .toList()
            localIdentitiesToClose.forEach { key ->
                removeServiceFor(key)
            }

            removeServiceFor(modelType)

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

            activeServices[modelType] = newService
            activeIdentities[modelType] = requestedIdentity
            return newService
        }
    }

    private suspend fun removeServiceFor(modelType: ModelType) {
        val currentIdentity = activeIdentities[modelType] ?: return
        val currentService = activeServices[modelType] ?: return
        
        activeServices.remove(modelType)
        activeIdentities.remove(modelType)
        
        // If no other modelType is using this identity, close the session
        if (activeIdentities.none { it.value == currentIdentity }) {
            currentService.closeSession()
        }
    }

    private fun createApiIdentity(
        modelType: ModelType,
        apiConfig: com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration,
        apiCreds: com.browntowndev.pocketcrew.domain.model.config.ApiCredentials,
        apiKey: String,
        requestHeaders: Map<String, String>,
        resolvedBaseUrl: String?
    ): ServiceIdentity.Api = ServiceIdentity.Api(
        modelType = modelType,
        configId = apiConfig.id,
        credentialsId = apiCreds.id,
        provider = apiCreds.provider,
        modelId = apiCreds.modelId,
        baseUrl = resolvedBaseUrl,
        reasoningEffort = apiConfig.reasoningEffort?.wireValue,
        openRouterRouting = OpenRouterRoutingIdentity(
            providerSort = apiConfig.openRouterRouting.providerSort.wireValue,
            allowFallbacks = apiConfig.openRouterRouting.allowFallbacks,
            requireParameters = apiConfig.openRouterRouting.requireParameters,
            dataCollectionPolicy = apiConfig.openRouterRouting.dataCollectionPolicy.wireValue,
            zeroDataRetention = apiConfig.openRouterRouting.zeroDataRetention
        ),
        googleApiVersion = if (apiCreds.provider == ApiProvider.GOOGLE) {
            GoogleGenAiClientProvider.GEMINI_API_VERSION
        } else {
            null
        },
        headersHash = requestHeaders.entries
            .sortedBy { it.key.lowercase() }
            .joinToString(separator = ",") { (key, value) -> "$key=$value" }
            .sha256Hex(),
        apiKeyHash = apiKey.sha256Hex()
    )

    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun buildRequestHeaders(
        provider: ApiProvider,
        customHeaders: Map<String, String>
    ): Map<String, String> {
        val normalizedCustomHeaders = customHeaders
            .filterValues { it.isNotBlank() }
        if (provider != ApiProvider.OPENROUTER) {
            return normalizedCustomHeaders
        }

        val openRouterDefaults = mapOf(
            "HTTP-Referer" to "android-app://${context.packageName}",
            "X-OpenRouter-Title" to context.applicationInfo.loadLabel(context.packageManager).toString(),
        )
        return openRouterDefaults + normalizedCustomHeaders
    }

    private fun getModelPath(filename: String): String {
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }
}
