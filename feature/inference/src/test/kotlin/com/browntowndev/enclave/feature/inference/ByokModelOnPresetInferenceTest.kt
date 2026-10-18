@file:Suppress("ForbiddenMockKInTests") // OpenAIClient/AnthropicClient/genai Client are third-party SDK types; MockK is used only for them.

package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anthropic.client.AnthropicClient
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
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
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.google.genai.Client
import com.openai.client.OpenAIClient
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ByokModelOnPresetInferenceTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val apiModelRepository = FakeApiModelRepository()
    private val apiKeyProvider = FakeApiKeyProvider()
    private val openAiClientProvider = FakeOpenAiClientProviderPort()
    private val openAiClient = mockk<OpenAIClient>(relaxed = true)
    private val inferenceLockManager = FakeInferenceLockManager()
    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        openAiClientProvider.client = openAiClient
        factory =
            InferenceFactoryImpl(
                context = ApplicationProvider.getApplicationContext<Context>(),
                defaultModelRepository = defaultModelRepository,
                localModelRepository = FakeLocalModelRepository(),
                activeModelProvider = FakeActiveModelProvider(),
                apiModelRepository = apiModelRepository,
                apiKeyProvider = apiKeyProvider,
                processThinkingTokens = FakeProcessThinkingTokensUseCase(),
                llamaChatSessionManagerProvider =
                    providerOf(
                        com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager(FakeLlamaEnginePort()),
                    ),
                conversationManagerProvider = providerOf(FakeConversationManager()),
                openAiClientProvider = openAiClientProvider,
                anthropicClientProvider =
                    FakeAnthropicClientProviderPort().apply { client = mockk<AnthropicClient>(relaxed = true) },
                googleGenAiClientProvider =
                    FakeGoogleGenAiClientProviderPort().apply { client = mockk<Client>(relaxed = true) },
                loggingPort = FakeLoggingPort(),
                inferenceLockManager = inferenceLockManager,
                toolExecutionEventPort = FakeToolExecutionEventPort(),
                agentLoopPort = FakeAgentLoopPort(),
                toolExecutorPort = FakeToolExecutor(),
                toolEnvelopeParserPort =
                    com.browntowndev.enclave.domain.util
                        .FakeToolEnvelopeParserPort(),
                toolStreamGatePort =
                    com.browntowndev.enclave.domain.util
                        .FakeToolStreamGatePort(),
                telemetrySupport = testInferenceTelemetrySupport(),
                telemetryEventPort = FakeTelemetryEventPort(),
                embeddingEnginePort = FakeEmbeddingEngine(),
                monotonicToolActivation =
                    com.browntowndev.enclave.domain.usecase.chat.InMemoryMonotonicToolActivationStore(),
                toolSearchEmbeddingCache =
                    com.browntowndev.enclave.domain.port.inference.NoopToolSearchEmbeddingCachePort,
                compactionSummarizer =
                    com.browntowndev.enclave.domain.port.inference.CompactionSummarizerPort.Dummy,
                deviceMemoryProbe =
                    com.browntowndev.enclave.domain.port.device.DeviceMemoryProbePort.Permissive,
            )
    }

    @Test
    fun `SCN-INFERENCE-001 Factory Uses Config Model For OpenAI`() =
        runTest {
            val configId = ApiModelConfigurationId("cfg-primary")
            val credId = ApiCredentialsId("cred-1")
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.MAIN, apiConfigId = configId),
            )
            apiModelRepository.seedConfigurations(listOf(apiConfiguration(modelId = "gpt-4o", credId = credId).copy(id = configId)))
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "OpenAI",
                        provider = ApiProvider.OPENAI,
                        baseUrl = "https://api.openai.com/v1",
                        credentialAlias = "alias",
                    ),
                ),
            )
            apiKeyProvider.saveApiKey("alias", "secret")

            factory.withInferenceService(ModelType.MAIN) { }

            assertEquals("gpt-4o", readActiveApiModelId())
        }

    @Test
    fun `SCN-INFERENCE-002 Service Identity Model From Config`() =
        runTest {
            val config = apiConfiguration(modelId = "openai/gpt-5.2", credId = ApiCredentialsId("cred-1"))
            val credentials =
                ApiCredentials(
                    id = ApiCredentialsId("cred-1"),
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "alias",
                )
            val identity =
                invokeCreateApiIdentity(
                    modelType = ModelType.MAIN,
                    apiConfig = config,
                    apiCredentials = credentials,
                    apiKey = "secret",
                    requestHeaders = emptyMap(),
                    resolvedBaseUrl = "https://api.openai.com/v1",
                )
            assertEquals("openai/gpt-5.2", identityModelId(identity))
        }

    @Test
    fun `SCN-MUT-003 Inference Factory Must Not Read Credential Model`() =
        runTest {
            val configId = ApiModelConfigurationId("cfg-primary")
            val credId = ApiCredentialsId("cred-1")
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.MAIN, apiConfigId = configId),
            )
            apiModelRepository.seedConfigurations(listOf(apiConfiguration(modelId = "gpt-4o", credId = credId).copy(id = configId)))
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "OpenAI",
                        provider = ApiProvider.OPENAI,
                        baseUrl = "https://api.openai.com/v1",
                        credentialAlias = "alias",
                    ),
                ),
            )
            apiKeyProvider.saveApiKey("alias", "secret")

            factory.withInferenceService(ModelType.MAIN) { }

            assertEquals("gpt-4o", readActiveApiModelId())
        }

    private fun apiConfiguration(
        modelId: String,
        credId: ApiCredentialsId,
    ): ApiModelConfiguration =
        ApiModelConfiguration(
            id = ApiModelConfigurationId("cfg"),
            apiCredentialsId = credId,
            modelId = modelId,
            displayName = "Preset",
        )

    private fun readActiveApiModelId(): String {
        val identitiesField = InferenceFactoryImpl::class.java.getDeclaredField("activeIdentities")
        identitiesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val identities = identitiesField.get(factory) as Map<ModelType, Any>
        val identity = identities.getValue(ModelType.MAIN)
        return identityModelId(identity)
    }

    private fun identityModelId(identity: Any): String {
        val field = identity.javaClass.getDeclaredField("modelId")
        field.isAccessible = true
        return field.get(identity) as String
    }

    private fun invokeCreateApiIdentity(
        modelType: ModelType,
        apiConfig: ApiModelConfiguration,
        apiCredentials: ApiCredentials,
        apiKey: String,
        requestHeaders: Map<String, String>,
        resolvedBaseUrl: String?,
    ): Any {
        val method =
            InferenceFactoryImpl::class
                .declaredFunctions
                .first { it.name == "createApiIdentity" }
        method.isAccessible = true
        return requireNotNull(
            method.call(
                factory,
                modelType,
                apiConfig,
                apiCredentials,
                apiKey,
                requestHeaders,
                resolvedBaseUrl,
            ),
        )
    }

    private fun <T> providerOf(value: T): javax.inject.Provider<T> =
        object : javax.inject.Provider<T> {
            override fun get(): T = value
        }
}
