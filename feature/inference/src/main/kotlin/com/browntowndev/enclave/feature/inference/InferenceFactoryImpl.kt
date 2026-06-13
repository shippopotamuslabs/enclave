package com.browntowndev.enclave.feature.inference

import android.content.Context
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.error.InferenceIOException
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.device.DeviceMemoryProbePort
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.CompactionSummarizerPort
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import com.browntowndev.enclave.domain.port.inference.ToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.port.inference.CommittedInferenceTranscriptPort
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferenceTranscriptPort
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DeviceEnvironmentRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.NoopDeviceEnvironmentRepository
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.usecase.chat.RollingSummarizer
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.InferenceType
import com.browntowndev.enclave.domain.usecase.inference.ResolveCompactionSummaryModelUseCase
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Provider

/**
 * Dynamic InferenceFactoryPort implementation.
 * Resolves the correct inference service (Llama, LiteRT) dynamically
 * based on the active model's file extension at inference time.
 */
@Suppress("LongParameterList", "LongMethod", "LargeClass", "ThrowsCount")
class InferenceFactoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val localModelRepository: LocalModelRepositoryPort,
        private val activeModelProvider: ActiveModelProviderPort,
        private val apiModelRepository: ApiModelRepositoryPort,
        private val apiKeyProvider: ApiKeyProviderPort,
        private val processThinkingTokens: ProcessThinkingTokensUseCase,
        private val llamaChatSessionManagerProvider: Provider<LlamaChatSessionManager>,
        private val conversationManagerProvider: Provider<ConversationManager>,
        private val openAiClientProvider: OpenAiClientProviderPort,
        private val anthropicClientProvider: AnthropicClientProviderPort,
        private val googleGenAiClientProvider: GoogleGenAiClientProviderPort,
        private val loggingPort: LoggingPort,
        private val inferenceLockManager: InferenceLockManager,
        private val toolExecutionEventPort: ToolExecutionEventPort,
        private val agentLoopPort: AgentLoopPort,
        private val toolExecutorPort: ToolExecutorPort,
        private val toolEnvelopeParserPort: ToolEnvelopeParserPort,
        private val toolStreamGatePort: ToolStreamGatePort,
        private val inferenceFactoryProvider: Provider<InferenceFactoryPort>? = null,
        private val telemetrySupport: InferenceTelemetrySupport,
        private val telemetryEventPort: TelemetryEventPort,
        private val committedInferencePrompts: CommittedInferencePromptPort = NoopCommittedInferencePromptPort,
        private val committedInferenceTranscripts: CommittedInferenceTranscriptPort = NoopCommittedInferenceTranscriptPort,
        private val deviceEnvironmentRepository: DeviceEnvironmentRepositoryPort = NoopDeviceEnvironmentRepository,
        private val embeddingEnginePort: EmbeddingEnginePort,
        private val monotonicToolActivation: MonotonicToolActivationPort,
        private val toolSearchEmbeddingCache: ToolSearchEmbeddingCachePort,
        private val compactionSummarizer: CompactionSummarizerPort,
        private val deviceMemoryProbe: DeviceMemoryProbePort,
    ) : InferenceFactoryPort {
        companion object {
            private const val TAG = "InferenceFactory"
            private const val GEMINI_API_VERSION = "v1alpha"
            private const val HTTP_HEADER_ASCII_MIN = 32
            private const val HTTP_HEADER_ASCII_MAX = 126
            private const val OPENROUTER_TITLE_MAX_LENGTH = 128
        }

        private sealed interface ServiceIdentity {
            data class Api(
                val modelType: ModelType,
                val configId: ApiModelConfigurationId,
                val credentialsId: ApiCredentialsId,
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
            block: suspend (LlmInferencePort) -> T,
        ): T =
            try {
                withInferenceServiceImpl(modelType, block)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InferenceIOException) {
                throw e
            } catch (e: java.io.IOException) {
                throw InferenceIOException(message = e.message, cause = e)
            }

        private suspend fun <T> withInferenceServiceImpl(
            modelType: ModelType,
            block: suspend (LlmInferencePort) -> T,
        ): T {
            val defaultAssignment =
                withContext(Dispatchers.IO) {
                    defaultModelRepository.getDefault(modelType)
                }
            val lockType = resolveLockType(modelType, defaultAssignment)

            if (!inferenceLockManager.acquireLock(lockType)) {
                throw InferenceBusyException()
            }

            loggingPort.info(TAG, "AgentLoop: [agent_core] modelType=$modelType")

            val service =
                try {
                    withContext(Dispatchers.IO) {
                        resolveService(modelType, defaultAssignment)
                    }
                } catch (e: Exception) {
                    inferenceLockManager.releaseLock(lockType)
                    throw e
                }

            return try {
                block(service)
            } finally {
                inferenceLockManager.releaseLock(lockType)
            }
        }

        private fun resolveLockType(
            modelType: ModelType,
            defaultAssignment: DefaultModelAssignment?,
        ): InferenceType =
            when {
                modelType == ModelType.VISION -> InferenceType.BYOK
                defaultAssignment?.apiConfigId != null -> InferenceType.BYOK
                else -> InferenceType.ON_DEVICE
            }

        @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
        private suspend fun resolveService(
            modelType: ModelType,
            defaultAssignment: DefaultModelAssignment? = null,
        ): LlmInferencePort {
            val assignment = defaultAssignment ?: defaultModelRepository.getDefault(modelType)
            if (assignment == null) {
                loggingPort.warning(TAG, "No default assignment for $modelType, using NoOpInferenceService")
                return NoOpInferenceService(modelType)
            }

            if (modelType == ModelType.VISION) {
                val apiConfigId =
                    assignment.apiConfigId
                        ?: throw IllegalStateException("Vision slot is API-only and requires an API configuration.")
                val apiConfig = apiModelRepository.getConfigurationById(apiConfigId)
                val apiCreds = apiConfig?.let { apiModelRepository.getCredentialsById(it.apiCredentialsId) }
                val apiKey = apiCreds?.let { apiKeyProvider.getApiKey(it.credentialAlias) }

                if (apiConfig == null || apiCreds == null || apiKey == null) {
                    throw IllegalStateException("Vision slot requires a configured API vision model and credentials.")
                }

                val requestHeaders = buildRequestHeaders(apiCreds.provider, apiConfig.customHeaders)
                val resolvedBaseUrl = apiCreds.baseUrl?.takeIf { it.isNotBlank() } ?: apiCreds.provider.defaultBaseUrl()
                val requestedIdentity =
                    createApiIdentity(
                        modelType = modelType,
                        apiConfig = apiConfig,
                        apiCredentials = apiCreds,
                        apiKey = apiKey,
                        requestHeaders = requestHeaders,
                        resolvedBaseUrl = resolvedBaseUrl,
                    )

                return mutex.withLock {
                    if (activeIdentities[modelType] == requestedIdentity) {
                        return@withLock activeServices[modelType]!!
                    }

                    removeServiceFor(modelType)

                    val newService =
                        buildUncachedApiService(
                            apiConfig = apiConfig,
                            apiCreds = apiCreds,
                            apiKey = apiKey,
                            requestHeaders = requestHeaders,
                            resolvedBaseUrl = resolvedBaseUrl,
                            modelType = modelType,
                        )

                    val finalService = wrapWithRustLoop(newService, apiCreds.provider.name, modelType)
                    activeServices[modelType] = finalService
                    activeIdentities[modelType] = requestedIdentity
                    return finalService
                }
            }

            val apiConfigId = assignment.apiConfigId
            if (apiConfigId != null) {
                val apiConfig = apiModelRepository.getConfigurationById(apiConfigId)
                val apiCreds = apiConfig?.let { apiModelRepository.getCredentialsById(it.apiCredentialsId) }
                val apiKey = apiCreds?.let { apiKeyProvider.getApiKey(it.credentialAlias) }

                if (apiConfig == null || apiCreds == null || apiKey == null) {
                    @Suppress("MaxLineLength")
                    loggingPort.error(
                        TAG,
                        "Missing API configuration or credentials for $modelType. Config: ${apiConfig?.id}, Creds: ${apiCreds?.id}, Key present: ${apiKey != null}",
                    )
                    // Fallback to on-device logic below if we somehow have both set, though UI shouldn't allow this
                } else {
                    val requestHeaders = buildRequestHeaders(apiCreds.provider, apiConfig.customHeaders)
                    val resolvedBaseUrl = apiCreds.baseUrl?.takeIf { it.isNotBlank() } ?: apiCreds.provider.defaultBaseUrl()
                    val requestedIdentity =
                        createApiIdentity(
                            modelType = modelType,
                            apiConfig = apiConfig,
                            apiCredentials = apiCreds,
                            apiKey = apiKey,
                            requestHeaders = requestHeaders,
                            resolvedBaseUrl = resolvedBaseUrl,
                        )

                    return mutex.withLock {
                        if (activeIdentities[modelType] == requestedIdentity) {
                            return@withLock activeServices[modelType]!!
                        }

                        removeServiceFor(modelType)

                        val newService =
                            buildUncachedApiService(
                                apiConfig = apiConfig,
                                apiCreds = apiCreds,
                                apiKey = apiKey,
                                requestHeaders = requestHeaders,
                                resolvedBaseUrl = resolvedBaseUrl,
                                modelType = modelType,
                            )

                        val finalService = wrapWithRustLoop(newService, apiCreds.provider.name, modelType)
                        activeServices[modelType] = finalService
                        activeIdentities[modelType] = requestedIdentity
                        return finalService
                    }
                }
            }

            val localConfigId = assignment.localConfigId
            if (localConfigId == null) {
                loggingPort.warning(TAG, "No local or API config for $modelType, using NoOpInferenceService")
                return NoOpInferenceService(modelType)
            }

            val asset =
                try {
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
            val requestedIdentity =
                ServiceIdentity.Local(
                    modelSha256 = asset.metadata.sha256,
                    extension = extension,
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
                val localIdentitiesToClose =
                    activeIdentities
                        .filterValues { it is ServiceIdentity.Local }
                        .keys
                        .toList()
                localIdentitiesToClose.forEach { key ->
                    removeServiceFor(key)
                }

                removeServiceFor(modelType)

                val newService =
                    when (extension) {
                        "gguf" -> {
                            wrapWithRustLoop(
                                service =
                                    LlamaInferenceServiceImpl(
                                        sessionManager = llamaChatSessionManagerProvider.get(),
                                        processThinkingTokens = processThinkingTokens,
                                        loggingPort = loggingPort,
                                        localModelRepository = localModelRepository,
                                        activeModelProvider = activeModelProvider,
                                        context = context,
                                        modelType = modelType,
                                        toolEnvelopeParserPort = toolEnvelopeParserPort,
                                        toolStreamGatePort = toolStreamGatePort,
                                        telemetrySupport = telemetrySupport,
                                    ),
                                provider = "LLAMA",
                                modelType = modelType,
                            )
                        }
                        "litertlm" -> {
                            LiteRtInferenceServiceImpl(
                                conversationManager = conversationManagerProvider.get(),
                                processThinkingTokens = processThinkingTokens,
                                toolExecutionEventPort = toolExecutionEventPort,
                                modelType = modelType,
                                telemetrySupport = telemetrySupport,
                            )
                        }
                        else -> {
                            LiteRtInferenceServiceImpl(
                                conversationManager = conversationManagerProvider.get(),
                                processThinkingTokens = processThinkingTokens,
                                toolExecutionEventPort = toolExecutionEventPort,
                                modelType = modelType,
                                telemetrySupport = telemetrySupport,
                            )
                        }
                    }

                activeServices[modelType] = newService
                activeIdentities[modelType] = requestedIdentity
                return newService
            }
        }

        override suspend fun <T> withRawApiServiceForConfig(
            configId: ApiModelConfigurationId,
            block: suspend (LlmInferencePort) -> T,
        ): T {
            val service = withContext(Dispatchers.IO) { resolveRawServiceForApiConfig(configId) }
            return try {
                block(service)
            } finally {
                service.closeSession()
            }
        }

        private suspend fun resolveRawServiceForApiConfig(configId: ApiModelConfigurationId): RustCompatibleInferenceService {
            val apiConfig =
                apiModelRepository.getConfigurationById(configId)
                    ?: throw IllegalStateException("No model configuration found for id=$configId")
            val apiCreds =
                apiModelRepository.getCredentialsById(apiConfig.apiCredentialsId)
                    ?: throw IllegalStateException("No credentials for config id=$configId")
            val apiKey =
                apiKeyProvider.getApiKey(apiCreds.credentialAlias)
                    ?: throw IllegalStateException("No API key for alias=${apiCreds.credentialAlias}")

            val requestHeaders = buildRequestHeaders(apiCreds.provider, apiConfig.customHeaders)
            val resolvedBaseUrl = apiCreds.baseUrl?.takeIf { it.isNotBlank() } ?: apiCreds.provider.defaultBaseUrl()
            val modelType = ModelType.MAIN

            return buildUncachedApiService(
                apiConfig = apiConfig,
                apiCreds = apiCreds,
                apiKey = apiKey,
                requestHeaders = requestHeaders,
                resolvedBaseUrl = resolvedBaseUrl,
                modelType = modelType,
            ) as RustCompatibleInferenceService
        }

        private fun buildUncachedApiService(
            apiConfig: ApiModelConfiguration,
            apiCreds: ApiCredentials,
            apiKey: String,
            requestHeaders: Map<String, String>,
            resolvedBaseUrl: String?,
            modelType: ModelType,
        ): LlmInferencePort =
            ApiInferenceServiceFactory.buildUncachedService(
                apiConfig = apiConfig,
                apiCreds = apiCreds,
                apiKey = apiKey,
                requestHeaders = requestHeaders,
                resolvedBaseUrl = resolvedBaseUrl,
                modelType = modelType,
                openAiClientProvider = openAiClientProvider,
                anthropicClientProvider = anthropicClientProvider,
                googleGenAiClientProvider = googleGenAiClientProvider,
                loggingPort = loggingPort,
                toolEnvelopeParserPort = toolEnvelopeParserPort,
                toolStreamGatePort = toolStreamGatePort,
                telemetrySupport = telemetrySupport,
                telemetryEventPort = telemetryEventPort,
            )

        private fun wrapWithRustLoop(
            service: LlmInferencePort,
            provider: String,
            modelType: ModelType,
        ): LlmInferencePort =
            if (service is RustCompatibleInferenceService) {
                RustLoopInferenceService(
                    inner = service,
                    agentLoopPort = agentLoopPort,
                    toolExecutor = toolExecutorPort,
                    loggingPort = loggingPort,
                    provider = provider,
                    modelType = modelType,
                    inferenceFactoryProvider = inferenceFactoryProvider ?: Provider { this },
                    rollingSummarizer = RollingSummarizer(loggingPort, JTokkitTokenCounter),
                    resolveCompactionSummaryModel =
                        ResolveCompactionSummaryModelUseCase(activeModelProvider, JTokkitTokenCounter),
                    committedInferencePrompts = committedInferencePrompts,
                    committedInferenceTranscripts = committedInferenceTranscripts,
                    telemetryEventPort = telemetryEventPort,
                    deviceEnvironmentRepository = deviceEnvironmentRepository,
                    embeddingEnginePort = embeddingEnginePort,
                    monotonicToolActivation = monotonicToolActivation,
                    toolSearchEmbeddingCache = toolSearchEmbeddingCache,
                    compactionSummarizer = compactionSummarizer,
                    deviceMemoryProbe = deviceMemoryProbe,
                )
            } else {
                service
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
            apiConfig: ApiModelConfiguration,
            apiCredentials: ApiCredentials,
            apiKey: String,
            requestHeaders: Map<String, String>,
            resolvedBaseUrl: String?,
        ): ServiceIdentity.Api =
            ServiceIdentity.Api(
                modelType = modelType,
                configId = apiConfig.id,
                credentialsId = apiCredentials.id,
                provider = apiCredentials.provider,
                modelId = apiConfig.modelId,
                baseUrl = resolvedBaseUrl,
                reasoningEffort = apiConfig.reasoningEffort?.wireValue,
                openRouterRouting =
                    OpenRouterRoutingIdentity(
                        providerSort = apiConfig.openRouterRouting.providerSort.wireValue,
                        allowFallbacks = apiConfig.openRouterRouting.allowFallbacks,
                        requireParameters = apiConfig.openRouterRouting.requireParameters,
                        dataCollectionPolicy = apiConfig.openRouterRouting.dataCollectionPolicy.wireValue,
                        zeroDataRetention = apiConfig.openRouterRouting.zeroDataRetention,
                    ),
                googleApiVersion =
                    if (apiCredentials.provider == ApiProvider.GOOGLE) {
                        GEMINI_API_VERSION
                    } else {
                        null
                    },
                headersHash =
                    requestHeaders.entries
                        .sortedBy { it.key.lowercase() }
                        .joinToString(separator = ",") { (key, value) -> "$key=$value" }
                        .sha256Hex(),
                apiKeyHash = apiKey.sha256Hex(),
            )

        private fun String.sha256Hex(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        private fun buildRequestHeaders(
            provider: ApiProvider,
            customHeaders: Map<String, String>,
        ): Map<String, String> {
            val normalizedCustomHeaders =
                customHeaders
                    .filterValues { it.isNotBlank() }
            if (provider != ApiProvider.OPENROUTER) {
                return normalizedCustomHeaders
            }

            val openRouterDefaults =
                mapOf(
                    "HTTP-Referer" to "android-app://${context.packageName}",
                    "X-OpenRouter-Title" to openRouterAppTitle(),
                )
            return openRouterDefaults + normalizedCustomHeaders
        }

        private fun openRouterAppTitle(): String {
            val raw = context.applicationInfo.loadLabel(context.packageManager).toString()
            val ascii =
                raw
                    .map { character ->
                        if (character.code in HTTP_HEADER_ASCII_MIN..HTTP_HEADER_ASCII_MAX) character else '?'
                    }.joinToString("")
                    .trim()
                    .take(OPENROUTER_TITLE_MAX_LENGTH)
            return ascii.ifBlank { "Enclave" }
        }
    }
