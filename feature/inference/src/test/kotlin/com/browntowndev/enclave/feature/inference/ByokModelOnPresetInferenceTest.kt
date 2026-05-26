package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.openai.client.OpenAIClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

class ByokModelOnPresetInferenceTest {
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>()
    private val apiModelRepository = mockk<ApiModelRepositoryPort>()
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()
    private val openAiClientProvider = mockk<OpenAiClientProviderPort>(relaxed = true)
    private val openAiClient = mockk<OpenAIClient>(relaxed = true)
    private val inferenceLockManager = mockk<InferenceLockManager>()
    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        every { inferenceLockManager.acquireLock(any()) } returns true
        every { inferenceLockManager.releaseLock(any()) } returns Unit
        every { openAiClientProvider.getClient(any(), any(), any(), any(), any()) } returns openAiClient
        factory =
            InferenceFactoryImpl(
                context = mockk(relaxed = true),
                defaultModelRepository = defaultModelRepository,
                localModelRepository = mockk(relaxed = true),
                activeModelProvider = mockk(relaxed = true),
                apiModelRepository = apiModelRepository,
                apiKeyProvider = apiKeyProvider,
                processThinkingTokens = mockk(relaxed = true),
                llamaChatSessionManagerProvider = providerOf(mockk(relaxed = true)),
                conversationManagerProvider = providerOf(mockk(relaxed = true)),
                openAiClientProvider = openAiClientProvider,
                anthropicClientProvider = mockk(relaxed = true),
                googleGenAiClientProvider = mockk(relaxed = true),
                loggingPort = mockk(relaxed = true),
                inferenceLockManager = inferenceLockManager,
                toolExecutionEventPort = mockk(relaxed = true),
                agentLoopPort = mockk(relaxed = true),
                toolExecutorPort = mockk(relaxed = true),
                toolEnvelopeParserPort =
                    com.browntowndev.enclave.domain.util
                        .FakeToolEnvelopeParserPort(),
                toolStreamGatePort =
                    com.browntowndev.enclave.domain.util
                        .FakeToolStreamGatePort(),
                telemetrySupport = testInferenceTelemetrySupport(),
            )
    }

    @Test
    fun `SCN-INFERENCE-001 Factory Uses Config Model For OpenAI`() =
        runTest {
            val configId = ApiModelConfigurationId("cfg-primary")
            val credId = ApiCredentialsId("cred-1")
            coEvery { defaultModelRepository.getDefault(ModelType.MAIN) } returns
                DefaultModelAssignment(modelType = ModelType.MAIN, apiConfigId = configId)
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                apiConfiguration(modelId = "gpt-4o", credId = credId)
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    baseUrl = "https://api.openai.com/v1",
                    credentialAlias = "alias",
                )
            coEvery { apiKeyProvider.getApiKey("alias") } returns "secret"

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
            coEvery { defaultModelRepository.getDefault(ModelType.MAIN) } returns
                DefaultModelAssignment(modelType = ModelType.MAIN, apiConfigId = configId)
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                apiConfiguration(modelId = "gpt-4o", credId = credId)
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    baseUrl = "https://api.openai.com/v1",
                    credentialAlias = "alias",
                )
            coEvery { apiKeyProvider.getApiKey("alias") } returns "secret"

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
