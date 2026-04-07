package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import com.browntowndev.pocketcrew.core.data.openai.OpenAiClientProvider
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.openai.client.OpenAIClient
import android.content.pm.ApplicationInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InferenceFactoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>()
    private val localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true)
    private val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
    private val apiModelRepository = mockk<ApiModelRepositoryPort>()
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()
    private val processThinkingTokens = mockk<ProcessThinkingTokensUseCase>(relaxed = true)
    private val llamaProvider = providerOf(mockk<LlamaChatSessionManager>(relaxed = true))
    private val conversationProvider = providerOf(mockk<ConversationManagerPort>(relaxed = true))
    private val mediaPipeFactory = mockk<MediaPipeInferenceServiceFactory>(relaxed = true)
    private val openAiClientProvider = mockk<OpenAiClientProvider>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val inferenceLockManager = mockk<InferenceLockManager>()
    private val openAiClient = mockk<OpenAIClient>(relaxed = true)

    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        val applicationInfo = mockk<ApplicationInfo>(relaxed = true)
        every { context.packageName } returns "com.browntowndev.pocketcrew"
        every { context.packageManager } returns mockk(relaxed = true)
        every { context.applicationInfo } returns applicationInfo
        every { applicationInfo.loadLabel(any()) } returns "Pocket Crew"
        every { inferenceLockManager.acquireLock(any()) } returns true
        every { inferenceLockManager.releaseLock() } returns Unit
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { openAiClientProvider.getClient(any(), any(), any(), any(), any()) } returns openAiClient

        factory = InferenceFactoryImpl(
            context = context,
            defaultModelRepository = defaultModelRepository,
            localModelRepository = localModelRepository,
            activeModelProvider = activeModelProvider,
            apiModelRepository = apiModelRepository,
            apiKeyProvider = apiKeyProvider,
            processThinkingTokens = processThinkingTokens,
            llamaChatSessionManagerProvider = llamaProvider,
            conversationManagerProvider = conversationProvider,
            mediaPipeFactory = mediaPipeFactory,
            openAiClientProvider = openAiClientProvider,
            loggingPort = loggingPort,
            inferenceLockManager = inferenceLockManager
        )
    }

    @Test
    fun `withInferenceService recreates API service when credential model changes under same config`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = 7L)
        val config = ApiModelConfiguration(id = 7L, apiCredentialsId = 11L, displayName = "Thinking preset")
        var credentials = apiCredentials(modelId = "gpt-4o")

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(7L) } returns config
        coEvery { apiModelRepository.getCredentialsById(11L) } answers { credentials }
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val first = factory.withInferenceService(ModelType.THINKING) { it }

        credentials = apiCredentials(modelId = "gpt-5")
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertNotSame(first, second)
    }

    @Test
    fun `withInferenceService keeps separate API service instances per model type`() = runTest {
        val config = ApiModelConfiguration(id = 7L, apiCredentialsId = 11L, displayName = "Shared preset")
        val credentials = apiCredentials(modelId = "gpt-5")

        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns DefaultModelAssignment(
            modelType = ModelType.FAST,
            apiConfigId = 7L
        )
        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns DefaultModelAssignment(
            modelType = ModelType.THINKING,
            apiConfigId = 7L
        )
        coEvery { apiModelRepository.getConfigurationById(7L) } returns config
        coEvery { apiModelRepository.getCredentialsById(11L) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val fastService = factory.withInferenceService(ModelType.FAST) { it }
        val thinkingService = factory.withInferenceService(ModelType.THINKING) { it }

        assertNotSame(fastService, thinkingService)
    }

    @Test
    fun `withInferenceService routes openrouter credentials to dedicated service`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = 9L)
        val config = ApiModelConfiguration(id = 9L, apiCredentialsId = 11L, displayName = "Router preset")
        val credentials = apiCredentials(modelId = "openai/gpt-5.2:nitro", provider = ApiProvider.OPENROUTER)

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(9L) } returns config
        coEvery { apiModelRepository.getCredentialsById(11L) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals("OpenRouterInferenceServiceImpl", service.javaClass.simpleName)
    }

    @Test
    fun `withInferenceService resolves default baseUrl for openrouter when configured baseUrl is null`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = 9L)
        val config = ApiModelConfiguration(id = 9L, apiCredentialsId = 11L, displayName = "Router preset")
        val credentials = apiCredentials(modelId = "openai/gpt-5.2:nitro", provider = ApiProvider.OPENROUTER, baseUrl = null)

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(9L) } returns config
        coEvery { apiModelRepository.getCredentialsById(11L) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals("OpenRouterInferenceServiceImpl", service.javaClass.simpleName)
        // Verify the client provider was called with the resolved base URL
        io.mockk.verify { 
            openAiClientProvider.getClient(
                apiKey = "secret", 
                baseUrl = "https://openrouter.ai/api/v1", 
                headers = any()
            ) 
        }
    }

    @Test
    fun `withInferenceService resolves default baseUrl for xAI when configured baseUrl is blank`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = 9L)
        val config = ApiModelConfiguration(id = 9L, apiCredentialsId = 11L, displayName = "xAI preset")
        val credentials = apiCredentials(modelId = "grok-2", provider = ApiProvider.XAI, baseUrl = "   ")

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(9L) } returns config
        coEvery { apiModelRepository.getCredentialsById(11L) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals("XaiInferenceServiceImpl", service.javaClass.simpleName)
        // Verify the client provider was called with the resolved base URL
        io.mockk.verify { 
            openAiClientProvider.getClient(
                apiKey = "secret", 
                baseUrl = "https://api.x.ai/v1", 
                headers = any()
            ) 
        }
    }

    @Test
    fun `withInferenceService uses configured baseUrl when valid`() = runTest {
        val customUrl = "https://custom.endpoint.com/v1"
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = 9L)
        val config = ApiModelConfiguration(id = 9L, apiCredentialsId = 11L, displayName = "Router preset")
        val credentials = apiCredentials(modelId = "openai/gpt-5.2:nitro", provider = ApiProvider.OPENROUTER, baseUrl = customUrl)

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(9L) } returns config
        coEvery { apiModelRepository.getCredentialsById(11L) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals("OpenRouterInferenceServiceImpl", service.javaClass.simpleName)
        // Verify the client provider was called with the custom base URL
        io.mockk.verify { 
            openAiClientProvider.getClient(
                apiKey = "secret", 
                baseUrl = customUrl, 
                headers = any()
            ) 
        }
    }

    private fun apiCredentials(
        modelId: String,
        provider: ApiProvider = ApiProvider.OPENAI,
        baseUrl: String? = provider.defaultBaseUrl() ?: "https://api.openai.com/v1"
    ): ApiCredentials = ApiCredentials(
        id = 11L,
        displayName = "OpenAI",
        provider = provider,
        modelId = modelId,
        baseUrl = baseUrl,
        credentialAlias = "alias"
    )

    private fun <T> providerOf(value: T): Provider<T> = object : Provider<T> {
        override fun get(): T = value
    }
}
