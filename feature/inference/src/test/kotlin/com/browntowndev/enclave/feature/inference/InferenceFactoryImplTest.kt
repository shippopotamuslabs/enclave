@file:Suppress("ForbiddenMockKInTests") // OpenAIClient/AnthropicClient/genai Client are third-party SDK types; MockK is used only for them.

package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anthropic.client.AnthropicClient
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeTelemetryEventPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.error.InferenceIOException
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.device.DeviceMemoryProbePort
import com.browntowndev.enclave.domain.port.inference.CompactionSummarizerPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.NoopToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.usecase.chat.InMemoryMonotonicToolActivationStore
import com.browntowndev.enclave.domain.usecase.inference.InferenceType
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
import com.google.genai.Client
import com.openai.client.OpenAIClient
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import javax.inject.Provider

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class InferenceFactoryImplTest {
    private val defaultModelRepository = FakeFailingDefaultModelRepository()
    private val localModelRepository = FakeLocalModelRepository()
    private val activeModelProvider = FakeActiveModelProvider()
    private val apiModelRepository = FakeApiModelRepository()
    private val apiKeyProvider = FakeApiKeyProvider()
    private val processThinkingTokens = FakeProcessThinkingTokensUseCase()
    private val llamaSessionManager = LlamaChatSessionManager(FakeLlamaEnginePort())
    private val conversationProvider: Provider<ConversationManager> = providerOf(FakeConversationManager())
    private val openAiClientProvider = FakeOpenAiClientProviderPort()
    private val anthropicClientProvider = FakeAnthropicClientProviderPort()
    private val googleGenAiClientProvider = FakeGoogleGenAiClientProviderPort()
    private val loggingPort = FakeLoggingPort()
    private val inferenceLockManager = FakeInferenceLockManager()
    private val agentLoopPort = FakeAgentLoopPort()
    private val toolExecutor = FakeToolExecutor()
    private val openAiClient = mockk<OpenAIClient>(relaxed = true)
    private val anthropicClient = mockk<AnthropicClient>(relaxed = true)
    private val googleClient = mockk<Client>(relaxed = true)
    private val embeddingEnginePort: EmbeddingEnginePort = FakeEmbeddingEngine()

    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        openAiClientProvider.client = openAiClient
        anthropicClientProvider.client = anthropicClient
        googleGenAiClientProvider.client = googleClient

        factory =
            InferenceFactoryImpl(
                context = ApplicationProvider.getApplicationContext<Context>(),
                defaultModelRepository = defaultModelRepository,
                localModelRepository = localModelRepository,
                activeModelProvider = activeModelProvider,
                apiModelRepository = apiModelRepository,
                apiKeyProvider = apiKeyProvider,
                processThinkingTokens = processThinkingTokens,
                llamaChatSessionManagerProvider = providerOf(llamaSessionManager),
                conversationManagerProvider = conversationProvider,
                openAiClientProvider = openAiClientProvider,
                anthropicClientProvider = anthropicClientProvider,
                googleGenAiClientProvider = googleGenAiClientProvider,
                loggingPort = loggingPort,
                inferenceLockManager = inferenceLockManager,
                toolExecutionEventPort = FakeToolExecutionEventPort(),
                agentLoopPort = agentLoopPort,
                toolExecutorPort = toolExecutor,
                toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                toolStreamGatePort = FakeToolStreamGatePort(),
                telemetrySupport = testInferenceTelemetrySupport(),
                telemetryEventPort = FakeTelemetryEventPort(),
                embeddingEnginePort = embeddingEnginePort,
                monotonicToolActivation = InMemoryMonotonicToolActivationStore(),
                toolSearchEmbeddingCache = NoopToolSearchEmbeddingCachePort,
                compactionSummarizer = CompactionSummarizerPort.Dummy,
                deviceMemoryProbe = DeviceMemoryProbePort.Permissive,
            )
    }

    @Test
    fun `withInferenceService reuses local service when same SHA-256 model requested by different ModelType`() =
        runTest {
            val assignmentFast =
                DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-fast"))
            val assignmentThinking =
                DefaultModelAssignment(modelType = ModelType.THINKING, localConfigId = LocalModelConfigurationId("config-thinking"))

            // Same model binary (same SHA-256 and extension) but different configs — should still share the engine
            val assetFast =
                com.browntowndev.enclave.domain.model.config.LocalModelAsset(
                    metadata =
                        com.browntowndev.enclave.domain.model.config.LocalModelMetadata(
                            id = LocalModelId("1"),
                            huggingFaceModelName = "test/model",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "12345",
                            sizeInBytes = 1000L,
                            modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )

            val assetThinking =
                com.browntowndev.enclave.domain.model.config.LocalModelAsset(
                    metadata =
                        com.browntowndev.enclave.domain.model.config.LocalModelMetadata(
                            id = LocalModelId("1"),
                            huggingFaceModelName = "test/model",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "12345",
                            sizeInBytes = 1000L,
                            modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )

            defaultModelRepository.seed(assignmentFast)
            defaultModelRepository.seed(assignmentThinking)
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("config-fast"), assetFast)
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("config-thinking"), assetThinking)

            val first = factory.withInferenceService(ModelType.FAST) { it }
            val second = factory.withInferenceService(ModelType.THINKING) { it }

            assertEquals(first, second)
        }

    @Test
    fun `withInferenceService unloads previous local engine when different local model requested`() =
        runTest {
            val assignmentFast = DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-1"))
            val assignmentThinking =
                DefaultModelAssignment(modelType = ModelType.THINKING, localConfigId = LocalModelConfigurationId("config-2"))

            val asset1 =
                com.browntowndev.enclave.domain.model.config.LocalModelAsset(
                    metadata =
                        com.browntowndev.enclave.domain.model.config.LocalModelMetadata(
                            id = LocalModelId("1"),
                            huggingFaceModelName = "test/model1",
                            remoteFileName = "model1.gguf",
                            localFileName = "model1.gguf",
                            sha256 = "11111",
                            sizeInBytes = 1000L,
                            modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )

            val asset2 =
                com.browntowndev.enclave.domain.model.config.LocalModelAsset(
                    metadata =
                        com.browntowndev.enclave.domain.model.config.LocalModelMetadata(
                            id = LocalModelId("2"),
                            huggingFaceModelName = "test/model2",
                            remoteFileName = "model2.gguf",
                            localFileName = "model2.gguf",
                            sha256 = "22222",
                            sizeInBytes = 1000L,
                            modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )

            defaultModelRepository.seed(assignmentFast)
            defaultModelRepository.seed(assignmentThinking)
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("config-1"), asset1)
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("config-2"), asset2)

            val first = factory.withInferenceService(ModelType.FAST) { it }
            val second = factory.withInferenceService(ModelType.THINKING) { it }

            assertNotSame(first, second)
        }

    @Test
    fun `withInferenceService recreates API service when preset model changes under same config`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("7"))
            var config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    modelId = "gpt-4o",
                    displayName = "Thinking preset",
                )
            val credentials = apiCredentials()

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val first = factory.withInferenceService(ModelType.THINKING) { it }

            config = config.copy(modelId = "gpt-5")
            apiModelRepository.seedConfigurations(listOf(config))
            val second = factory.withInferenceService(ModelType.THINKING) { it }

            assertNotSame(first, second)
        }

    @Test
    fun `withInferenceService recreates API service when api key changes under same config and alias`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("7"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    modelId = "gpt-4o",
                    displayName = "Thinking preset",
                )
            val credentials = apiCredentials()
            var apiKey = "secret-a"

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", apiKey)

            val first = factory.withInferenceService(ModelType.THINKING) { it }

            apiKey = "secret-b"
            apiKeyProvider.saveApiKey("alias", apiKey)
            val second = factory.withInferenceService(ModelType.THINKING) { it }

            assertNotSame(first, second)
        }

    @Test
    fun `withInferenceService recreates OpenRouter service when preferred provider changes`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
            var config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("9"),
                    modelId = "openai/gpt-5.2:nitro",
                    displayName = "Router preset",
                    openRouterRouting = OpenRouterRoutingConfiguration(preferredProvider = "together"),
                )
            val credentials = apiCredentials(provider = ApiProvider.OPENROUTER)

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val first = factory.withInferenceService(ModelType.THINKING) { it }

            config =
                config.copy(
                    openRouterRouting = OpenRouterRoutingConfiguration(preferredProvider = "deepinfra"),
                )
            apiModelRepository.seedConfigurations(listOf(config))
            val second = factory.withInferenceService(ModelType.THINKING) { it }

            assertNotSame(first, second)
        }

    @Test
    fun `withInferenceService unloads local engine when switching to API model if no other role uses it`() =
        runTest {
            val localAssignment = DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-1"))
            val apiAssignment = DefaultModelAssignment(modelType = ModelType.FAST, apiConfigId = ApiModelConfigurationId("7"))

            val asset =
                com.browntowndev.enclave.domain.model.config.LocalModelAsset(
                    metadata =
                        com.browntowndev.enclave.domain.model.config.LocalModelMetadata(
                            id = LocalModelId("1"),
                            huggingFaceModelName = "test/model",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "12345",
                            sizeInBytes = 1000L,
                            modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )

            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    modelId = "gpt-4o",
                    displayName = "API preset",
                )
            val credentials = apiCredentials()

            // First load local model
            defaultModelRepository.seed(localAssignment)
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("config-1"), asset)

            val localService = factory.withInferenceService(ModelType.FAST) { it }

            // Then switch FAST to API model
            defaultModelRepository.seed(apiAssignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val apiService = factory.withInferenceService(ModelType.FAST) { it }

            assertNotSame(localService, apiService)
        }

    @Test
    fun `withInferenceService keeps local engine when switching one role to API model if another role still uses it`() =
        runTest {
            val localAssignmentFast =
                DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-fast"))
            val localAssignmentThinking =
                DefaultModelAssignment(modelType = ModelType.THINKING, localConfigId = LocalModelConfigurationId("config-thinking"))
            val apiAssignmentThinking = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("7"))

            // Same SHA-256 but different configs — should share the engine
            val assetFast =
                com.browntowndev.enclave.domain.model.config.LocalModelAsset(
                    metadata =
                        com.browntowndev.enclave.domain.model.config.LocalModelMetadata(
                            id = LocalModelId("1"),
                            huggingFaceModelName = "test/model",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "12345",
                            sizeInBytes = 1000L,
                            modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )

            val assetThinking =
                com.browntowndev.enclave.domain.model.config.LocalModelAsset(
                    metadata =
                        com.browntowndev.enclave.domain.model.config.LocalModelMetadata(
                            id = LocalModelId("1"),
                            huggingFaceModelName = "test/model",
                            remoteFileName = "model.gguf",
                            localFileName = "model.gguf",
                            sha256 = "12345",
                            sizeInBytes = 1000L,
                            modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )

            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    modelId = "gpt-4o",
                    displayName = "API preset",
                )
            val credentials = apiCredentials()

            // Load local model for both FAST and THINKING
            defaultModelRepository.seed(localAssignmentFast)
            defaultModelRepository.seed(localAssignmentThinking)
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("config-fast"), assetFast)
            localModelRepository.seedAssetForConfig(LocalModelConfigurationId("config-thinking"), assetThinking)

            val localServiceFast = factory.withInferenceService(ModelType.FAST) { it }
            val localServiceThinking = factory.withInferenceService(ModelType.THINKING) { it }

            assertEquals(localServiceFast, localServiceThinking) // They share the same instance

            // Switch THINKING to API model
            defaultModelRepository.seed(apiAssignmentThinking)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val apiServiceThinking = factory.withInferenceService(ModelType.THINKING) { it }

            assertNotSame(localServiceFast, apiServiceThinking)

            // FAST should still return the same local instance
            val localServiceFastAfterSwitch = factory.withInferenceService(ModelType.FAST) { it }
            assertEquals(localServiceFast, localServiceFastAfterSwitch)
        }

    @Test
    fun `withInferenceService keeps separate API service instances per model type`() =
        runTest {
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    modelId = "gpt-5",
                    displayName = "Shared preset",
                )
            val credentials = apiCredentials()

            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.FAST,
                    apiConfigId = ApiModelConfigurationId("7"),
                ))
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.THINKING,
                    apiConfigId = ApiModelConfigurationId("7"),
                ))
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val fastService = factory.withInferenceService(ModelType.FAST) { it }
            val thinkingService = factory.withInferenceService(ModelType.THINKING) { it }

            assertNotSame(fastService, thinkingService)
        }

    @Test
    fun `withInferenceService injects EmbeddingEnginePort into RustLoopInferenceService`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("9"),
                    modelId = "openai/gpt-5.2:nitro",
                    displayName = "Router preset",
                )
            val credentials = apiCredentials(provider = ApiProvider.OPENROUTER)

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            val field = RustLoopInferenceService::class.java.getDeclaredField("embeddingEnginePort")
            field.isAccessible = true
            assertSame(embeddingEnginePort, field.get(service))
        }

    @Test
    fun `withInferenceService routes openrouter credentials to dedicated service`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("9"),
                    modelId = "openai/gpt-5.2:nitro",
                    displayName = "Router preset",
                )
            val credentials = apiCredentials(provider = ApiProvider.OPENROUTER)

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            val request = openAiClientProvider.getClientRequests.last()
            assertEquals("secret", request.apiKey)
            assertEquals(ApiProvider.OPENROUTER.defaultBaseUrl(), request.baseUrl)
        }

    @Test
    fun `withInferenceService routes anthropic credentials to dedicated service`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("10"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("10"),
                    apiCredentialsId = ApiCredentialsId("12"),
                    modelId = "claude-sonnet-4-20250514",
                    displayName = "Anthropic preset",
                )
            val credentials =
                apiCredentials(
                    provider = ApiProvider.ANTHROPIC,
                    baseUrl = null,
                    id = ApiCredentialsId("12"),
                )

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            val request = anthropicClientProvider.getClientRequests.last()
            assertEquals("secret", request.apiKey)
            assertEquals(null, request.baseUrl)
            assertEquals(emptyMap<String, String>(), request.headers)
        }

    @Test
    fun `withInferenceService routes google credentials to dedicated service`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("13"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("13"),
                    apiCredentialsId = ApiCredentialsId("12"),
                    modelId = "gemini-2.5-flash",
                    displayName = "Google preset",
                )
            val credentials =
                apiCredentials(
                    provider = ApiProvider.GOOGLE,
                    baseUrl = null,
                    id = ApiCredentialsId("12"),
                )

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            val request = googleGenAiClientProvider.getClientRequests.last()
            assertEquals("secret", request.apiKey)
            assertEquals(ApiProvider.GOOGLE.defaultBaseUrl(), request.baseUrl)
            assertEquals(emptyMap<String, String>(), request.headers)
        }

    @Test
    fun `withInferenceService rejects fal credentials for chat inference`() =
        runTest {
            val thrown =
                runCatching {
                    resolveApiService(
                        provider = ApiProvider.FAL,
                        modelType = ModelType.THINKING,
                        apiConfigId = ApiModelConfigurationId("fal-config"),
                        apiCredentialsId = ApiCredentialsId("fal-creds"),
                        modelId = "fal-ai/flux-pro/kontext",
                    )
                }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException)
            assertEquals("fal.ai is media-only and cannot be used for chat inference.", thrown?.message)
        }

    @Test
    fun `withRawApiServiceForConfig rejects fal credentials for chat inference`() =
        runTest {
            val configId = ApiModelConfigurationId("fal-raw-config")
            val credsId = ApiCredentialsId("fal-raw-creds")
            apiModelRepository.seedConfigurations(listOf(
                apiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credsId,
                    modelId = "fal-ai/flux-pro/kontext",
                    displayName = "FAL preset",
                )))
            apiModelRepository.seedCredentials(listOf(apiCredentials(id = credsId, provider = ApiProvider.FAL)))
            apiKeyProvider.saveApiKey("alias", "secret")

            val thrown =
                runCatching {
                    factory.withRawApiServiceForConfig(configId) { it }
                }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException)
            assertEquals("fal.ai is media-only and cannot be used for chat inference.", thrown?.message)
        }

    @Test
    fun `withInferenceService resolves default baseUrl for openrouter when configured baseUrl is null`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("9"),
                    modelId = "openai/gpt-5.2:nitro",
                    displayName = "Router preset",
                )
            val credentials = apiCredentials(provider = ApiProvider.OPENROUTER, baseUrl = null)

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            // Verify the client provider was called with the resolved base URL
            val request = openAiClientProvider.getClientRequests.last()
            assertEquals("secret", request.apiKey)
            assertEquals("https://openrouter.ai/api/v1", request.baseUrl)
        }

    @Test
    fun `SCN-FACTORY-001 factory builds DeepSeekInferenceServiceImpl for DEEPSEEK credentials`() =
        runTest {
            val configId = ApiModelConfigurationId("deepseek-config")
            val credsId = ApiCredentialsId("deepseek-creds")
            apiModelRepository.seedConfigurations(listOf(
                apiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credsId,
                    modelId = "deepseek-chat",
                    displayName = "DeepSeek preset",
                )))
            apiModelRepository.seedCredentials(listOf(
                apiCredentials(
                    id = credsId,
                    provider = ApiProvider.DEEPSEEK,
                    baseUrl = null,
                )))
            apiKeyProvider.saveApiKey("alias", "ds-secret")

            val service = factory.withRawApiServiceForConfig(configId) { it }

            assertTrue(service.javaClass.name.contains("DeepSeekInferenceServiceImpl"))
        }

    @Test
    fun `SCN-FACTORY-002 blank base URL resolves to official DeepSeek endpoint`() =
        runTest {
            val configId = ApiModelConfigurationId("deepseek-config-2")
            val credsId = ApiCredentialsId("deepseek-creds-2")
            apiModelRepository.seedConfigurations(listOf(
                apiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credsId,
                    modelId = "deepseek-chat",
                    displayName = "DeepSeek preset",
                )))
            apiModelRepository.seedCredentials(listOf(
                apiCredentials(
                    id = credsId,
                    provider = ApiProvider.DEEPSEEK,
                    baseUrl = "   ",
                )))
            apiKeyProvider.saveApiKey("alias", "ds-secret")

            factory.withRawApiServiceForConfig(configId) { it }

            val request = openAiClientProvider.getClientRequests.last()
            assertEquals("ds-secret", request.apiKey)
            assertEquals("https://api.deepseek.com", request.baseUrl)
        }

    @Test
    fun `withInferenceService routes zai credentials to openai client with default base url`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("zai-config"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("zai-config"),
                    modelId = "glm-4.6",
                    displayName = "Z.ai preset",
                )
            val credentials = apiCredentials(provider = ApiProvider.ZAI, baseUrl = null)

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            val request = openAiClientProvider.getClientRequests.last()
            assertEquals("secret", request.apiKey)
            assertEquals("https://api.z.ai/api/paas/v4", request.baseUrl)
        }

    @Test
    fun `withInferenceService resolves default baseUrl for xAI when configured baseUrl is blank`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("9"),
                    modelId = "grok-2",
                    displayName = "xAI preset",
                )
            val credentials = apiCredentials(provider = ApiProvider.XAI, baseUrl = "   ")

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            // Verify the client provider was called with the resolved base URL
            val request = openAiClientProvider.getClientRequests.last()
            assertEquals("secret", request.apiKey)
            assertEquals("https://api.x.ai/v1", request.baseUrl)
        }

    @Test
    fun `withInferenceService uses configured baseUrl when valid`() =
        runTest {
            val customUrl = "https://custom.endpoint.com/v1"
            val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("9"),
                    modelId = "openai/gpt-5.2:nitro",
                    displayName = "Router preset",
                )
            val credentials = apiCredentials(provider = ApiProvider.OPENROUTER, baseUrl = customUrl)

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(listOf(credentials))
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.THINKING) { it }

            assertTrue(service is RustLoopInferenceService)
            // Verify the client provider was called with the custom base URL
            val request = openAiClientProvider.getClientRequests.last()
            assertEquals("secret", request.apiKey)
            assertEquals(customUrl, request.baseUrl)
        }

    @Test
    fun `withInferenceService routes vision slot through API services only`() =
        runTest {
            val assignment = DefaultModelAssignment(modelType = ModelType.VISION, apiConfigId = ApiModelConfigurationId("vision-config"))
            val config =
                apiModelConfiguration(
                    id = ApiModelConfigurationId("vision-config"),
                    apiCredentialsId = ApiCredentialsId("vision-creds"),
                    modelId = "gpt-4o",
                    displayName = "Vision preset",
                )

            defaultModelRepository.seed(assignment)
            apiModelRepository.seedConfigurations(listOf(config))
            apiModelRepository.seedCredentials(
                listOf(
                    apiCredentials(
                        provider = ApiProvider.OPENAI,
                        id = ApiCredentialsId("vision-creds"),
                    ),
                ),
            )
            apiKeyProvider.saveApiKey("alias", "secret")

            val service = factory.withInferenceService(ModelType.VISION) { it }

            assertTrue(service is RustLoopInferenceService)
            assertTrue(inferenceLockManager.acquiredTypes.contains(InferenceType.BYOK))
            assertFalse(inferenceLockManager.acquiredTypes.contains(InferenceType.ON_DEVICE))
        }

    @Test
    fun `withInferenceService maps IOException from default model lookup to InferenceIOException`() =
        runTest {
            defaultModelRepository.getDefaultFailure = java.io.IOException("io fail")

            val thrown =
                runCatching {
                    factory.withInferenceService(ModelType.FAST) { it }
                }.exceptionOrNull()

            assertTrue(thrown is InferenceIOException)
            assertEquals("io fail", thrown?.message)
            assertTrue((thrown as InferenceIOException).cause is java.io.IOException)
        }

    private fun apiCredentials(
        provider: ApiProvider = ApiProvider.OPENAI,
        baseUrl: String? = provider.defaultBaseUrl() ?: "https://api.openai.com/v1",
        id: ApiCredentialsId = ApiCredentialsId("11"),
    ): ApiCredentials =
        ApiCredentials(
            id = id,
            displayName = "OpenAI",
            provider = provider,
            baseUrl = baseUrl,
            credentialAlias = "alias",
        )

    private fun apiModelConfiguration(
        id: ApiModelConfigurationId,
        modelId: String,
        apiCredentialsId: ApiCredentialsId = ApiCredentialsId("11"),
        displayName: String = "Preset",
        openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    ): ApiModelConfiguration =
        ApiModelConfiguration(
            id = id,
            apiCredentialsId = apiCredentialsId,
            modelId = modelId,
            displayName = displayName,
            openRouterRouting = openRouterRouting,
        )

    private suspend fun resolveApiService(
        provider: ApiProvider,
        modelType: ModelType,
        apiConfigId: ApiModelConfigurationId,
        apiCredentialsId: ApiCredentialsId,
        modelId: String,
    ): com.browntowndev.enclave.domain.port.inference.LlmInferencePort {
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = modelType,
                apiConfigId = apiConfigId,
            ),
        )
        apiModelRepository.seedConfigurations(listOf(
            apiModelConfiguration(
                id = apiConfigId,
                apiCredentialsId = apiCredentialsId,
                modelId = modelId,
                displayName = "${provider.name} preset",
            )))
        apiModelRepository.seedCredentials(listOf(
            apiCredentials(
                id = apiCredentialsId,
                provider = provider,
                baseUrl =
                    when (provider) {
                        ApiProvider.OPENAI -> "https://api.openai.com/v1"
                        else -> provider.defaultBaseUrl()
                    },
            )))
        apiKeyProvider.saveApiKey("alias", "secret")
        return factory.withInferenceService(modelType) { it }
    }

    private fun <T> providerOf(value: T): Provider<T> =
        object : Provider<T> {
            override fun get(): T = value
        }
}
