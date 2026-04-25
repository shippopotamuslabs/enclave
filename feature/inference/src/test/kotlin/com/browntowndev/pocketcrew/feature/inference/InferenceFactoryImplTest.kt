package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.content.pm.ApplicationInfo
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.anthropic.client.AnthropicClient
import com.google.genai.Client
import com.openai.client.OpenAIClient
import java.io.File
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
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
    private val llamaSessionManager = mockk<LlamaChatSessionManager>(relaxed = true)
    private val conversationProvider = providerOf(mockk<ConversationManager>(relaxed = true))
    private val openAiClientProvider = mockk<OpenAiClientProviderPort>(relaxed = true)
    private val anthropicClientProvider = mockk<AnthropicClientProviderPort>(relaxed = true)
    private val googleGenAiClientProvider = mockk<GoogleGenAiClientProviderPort>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val inferenceLockManager = mockk<InferenceLockManager>()
    private val toolExecutor = mockk<ToolExecutorPort>()
    private val toolExecutorProvider = providerOf(toolExecutor)
    private val openAiClient = mockk<OpenAIClient>(relaxed = true)
    private val anthropicClient = mockk<AnthropicClient>(relaxed = true)
    private val googleClient = mockk<Client>(relaxed = true)

    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        val applicationInfo = mockk<ApplicationInfo>(relaxed = true)
        every { context.packageName } returns "com.browntowndev.pocketcrew"
        every { context.packageManager } returns mockk(relaxed = true)
        every { context.applicationInfo } returns applicationInfo
        every { context.getExternalFilesDir(null) } returns File("/tmp")
        every { applicationInfo.loadLabel(any()) } returns "Pocket Crew"
        every { inferenceLockManager.acquireLock(any()) } returns true
        every { inferenceLockManager.releaseLock() } returns Unit
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { openAiClientProvider.getClient(any(), any(), any(), any(), any()) } returns openAiClient
        every { anthropicClientProvider.getClient(any(), any()) } returns anthropicClient
        every { googleGenAiClientProvider.getClient(any(), any(), any(), any()) } returns googleClient

        factory = InferenceFactoryImpl(
            context = context,
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
            orchestrator = LlmToolingOrchestrator(toolExecutor, loggingPort),
            inferenceLockManager = inferenceLockManager,
            toolExecutorProvider = toolExecutorProvider,
            toolExecutionEventPort = mockk(relaxed = true),
        )
    }

    @Test
    fun `withInferenceService reuses local service when same SHA-256 model requested by different ModelType`() = runTest {
        val assignmentFast = DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-fast"))
        val assignmentThinking = DefaultModelAssignment(modelType = ModelType.THINKING, localConfigId = LocalModelConfigurationId("config-thinking"))
        
        // Same model binary (same SHA-256 and extension) but different configs — should still share the engine
        val assetFast = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("1"),
                huggingFaceModelName = "test/model",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "12345",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )

        val assetThinking = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("1"),
                huggingFaceModelName = "test/model",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "12345",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )

        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns assignmentFast
        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignmentThinking
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-fast")) } returns assetFast
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-thinking")) } returns assetThinking

        val first = factory.withInferenceService(ModelType.FAST) { it }
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals(first, second)
    }

    @Test
    fun `withInferenceService unloads previous local engine when different local model requested`() = runTest {
        val assignmentFast = DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-1"))
        val assignmentThinking = DefaultModelAssignment(modelType = ModelType.THINKING, localConfigId = LocalModelConfigurationId("config-2"))
        
        val asset1 = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("1"),
                huggingFaceModelName = "test/model1",
                remoteFileName = "model1.gguf",
                localFileName = "model1.gguf",
                sha256 = "11111",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )

        val asset2 = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("2"),
                huggingFaceModelName = "test/model2",
                remoteFileName = "model2.gguf",
                localFileName = "model2.gguf",
                sha256 = "22222",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )

        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns assignmentFast
        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignmentThinking
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-1")) } returns asset1
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-2")) } returns asset2

        val first = factory.withInferenceService(ModelType.FAST) { it }
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertNotSame(first, second)
    }

    @Test
    fun `withInferenceService recreates API service when credential model changes under same config`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("7"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("7"), apiCredentialsId = ApiCredentialsId("11"), displayName = "Thinking preset")
        var credentials = apiCredentials(modelId = "gpt-4o")

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("7")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } answers { credentials }
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val first = factory.withInferenceService(ModelType.THINKING) { it }

        credentials = apiCredentials(modelId = "gpt-5")
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertNotSame(first, second)
    }

    @Test
    fun `withInferenceService recreates API service when api key changes under same config and alias`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("7"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("7"), apiCredentialsId = ApiCredentialsId("11"), displayName = "Thinking preset")
        val credentials = apiCredentials(modelId = "gpt-4o")
        var apiKey = "secret-a"

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("7")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } answers { apiKey }

        val first = factory.withInferenceService(ModelType.THINKING) { it }

        apiKey = "secret-b"
        val second = factory.withInferenceService(ModelType.THINKING) { it }

        assertNotSame(first, second)
    }

    @Test
    fun `withInferenceService unloads local engine when switching to API model if no other role uses it`() = runTest {
        val localAssignment = DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-1"))
        val apiAssignment = DefaultModelAssignment(modelType = ModelType.FAST, apiConfigId = ApiModelConfigurationId("7"))
        
        val asset = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("1"),
                huggingFaceModelName = "test/model",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "12345",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )
        
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("7"), apiCredentialsId = ApiCredentialsId("11"), displayName = "API preset")
        val credentials = apiCredentials(modelId = "gpt-4o")

        // First load local model
        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns localAssignment
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-1")) } returns asset

        val localService = factory.withInferenceService(ModelType.FAST) { it }
        
        // Then switch FAST to API model
        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns apiAssignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("7")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"
        
        val apiService = factory.withInferenceService(ModelType.FAST) { it }
        
        assertNotSame(localService, apiService)
    }

    @Test
    fun `withInferenceService keeps local engine when switching one role to API model if another role still uses it`() = runTest {
        val localAssignmentFast = DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-fast"))
        val localAssignmentThinking = DefaultModelAssignment(modelType = ModelType.THINKING, localConfigId = LocalModelConfigurationId("config-thinking"))
        val apiAssignmentThinking = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("7"))
        
        // Same SHA-256 but different configs — should share the engine
        val assetFast = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("1"),
                huggingFaceModelName = "test/model",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "12345",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )
        
        val assetThinking = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("1"),
                huggingFaceModelName = "test/model",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "12345",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )
        
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("7"), apiCredentialsId = ApiCredentialsId("11"), displayName = "API preset")
        val credentials = apiCredentials(modelId = "gpt-4o")

        // Load local model for both FAST and THINKING
        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns localAssignmentFast
        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns localAssignmentThinking
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-fast")) } returns assetFast
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-thinking")) } returns assetThinking

        val localServiceFast = factory.withInferenceService(ModelType.FAST) { it }
        val localServiceThinking = factory.withInferenceService(ModelType.THINKING) { it }
        
        assertEquals(localServiceFast, localServiceThinking) // They share the same instance
        
        // Switch THINKING to API model
        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns apiAssignmentThinking
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("7")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"
        
        val apiServiceThinking = factory.withInferenceService(ModelType.THINKING) { it }
        
        assertNotSame(localServiceFast, apiServiceThinking)
        
        // FAST should still return the same local instance
        val localServiceFastAfterSwitch = factory.withInferenceService(ModelType.FAST) { it }
        assertEquals(localServiceFast, localServiceFastAfterSwitch)
    }

    @Test
    fun `withInferenceService keeps separate API service instances per model type`() = runTest {
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("7"), apiCredentialsId = ApiCredentialsId("11"), displayName = "Shared preset")
        val credentials = apiCredentials(modelId = "gpt-5")

        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns DefaultModelAssignment(
            modelType = ModelType.FAST,
            apiConfigId = ApiModelConfigurationId("7")
        )
        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns DefaultModelAssignment(
            modelType = ModelType.THINKING,
            apiConfigId = ApiModelConfigurationId("7")
        )
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("7")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val fastService = factory.withInferenceService(ModelType.FAST) { it }
        val thinkingService = factory.withInferenceService(ModelType.THINKING) { it }

        assertNotSame(fastService, thinkingService)
    }

    @Test
    fun `withInferenceService routes openrouter credentials to dedicated service`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("9"), apiCredentialsId = ApiCredentialsId("11"), displayName = "Router preset")
        val credentials = apiCredentials(modelId = "openai/gpt-5.2:nitro", provider = ApiProvider.OPENROUTER)

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("9")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals("OpenRouterInferenceServiceImpl", service.javaClass.simpleName)
    }

    @Test
    fun `withInferenceService routes anthropic credentials to dedicated service`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("10"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("10"), apiCredentialsId = ApiCredentialsId("12"), displayName = "Anthropic preset")
        val credentials = apiCredentials(
            modelId = "claude-sonnet-4-20250514",
            provider = ApiProvider.ANTHROPIC,
            baseUrl = null
        )

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("10")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("12")) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals("AnthropicInferenceServiceImpl", service.javaClass.simpleName)
    }

    @Test
    fun `withInferenceService routes google credentials to dedicated service`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("13"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("13"), apiCredentialsId = ApiCredentialsId("12"), displayName = "Google preset")
        val credentials = apiCredentials(
            modelId = "gemini-2.5-flash",
            provider = ApiProvider.GOOGLE,
            baseUrl = null
        )

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("13")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("12")) } returns credentials
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.THINKING) { it }

        assertEquals("GoogleInferenceServiceImpl", service.javaClass.simpleName)
    }

    @Test
    fun `withInferenceService resolves default baseUrl for openrouter when configured baseUrl is null`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("9"), apiCredentialsId = ApiCredentialsId("11"), displayName = "Router preset")
        val credentials = apiCredentials(modelId = "openai/gpt-5.2:nitro", provider = ApiProvider.OPENROUTER, baseUrl = null)

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("9")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
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
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("9"), apiCredentialsId = ApiCredentialsId("11"), displayName = "xAI preset")
        val credentials = apiCredentials(modelId = "grok-2", provider = ApiProvider.XAI, baseUrl = "   ")

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("9")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
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
        val assignment = DefaultModelAssignment(modelType = ModelType.THINKING, apiConfigId = ApiModelConfigurationId("9"))
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("9"), apiCredentialsId = ApiCredentialsId("11"), displayName = "Router preset")
        val credentials = apiCredentials(modelId = "openai/gpt-5.2:nitro", provider = ApiProvider.OPENROUTER, baseUrl = customUrl)

        coEvery { defaultModelRepository.getDefault(ModelType.THINKING) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("9")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("11")) } returns credentials
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

    @Test
    fun `withInferenceService injects one shared tool executor into supported remote providers`() = runTest {
        val openAiService = resolveApiService(
            provider = ApiProvider.OPENAI,
            modelType = ModelType.FAST,
            apiConfigId = ApiModelConfigurationId("21"),
            apiCredentialsId = ApiCredentialsId("31"),
            modelId = "gpt-4o",
        ) as ApiInferenceServiceImpl
        val openRouterService = resolveApiService(
            provider = ApiProvider.OPENROUTER,
            modelType = ModelType.THINKING,
            apiConfigId = ApiModelConfigurationId("22"),
            apiCredentialsId = ApiCredentialsId("32"),
            modelId = "openai/gpt-5.2:nitro",
        ) as OpenRouterInferenceServiceImpl
        val xaiService = resolveApiService(
            provider = ApiProvider.XAI,
            modelType = ModelType.MAIN,
            apiConfigId = ApiModelConfigurationId("23"),
            apiCredentialsId = ApiCredentialsId("33"),
            modelId = "grok-2",
        ) as XaiInferenceServiceImpl
        val anthropicService = resolveApiService(
            provider = ApiProvider.ANTHROPIC,
            modelType = ModelType.FINAL_SYNTHESIS,
            apiConfigId = ApiModelConfigurationId("24"),
            apiCredentialsId = ApiCredentialsId("34"),
            modelId = "claude-sonnet-4-20250514",
        ) as AnthropicInferenceServiceImpl

        val openAiExecutor = openAiService.orchestrator.toolExecutor
        assertEquals(openAiExecutor, openRouterService.orchestrator.toolExecutor)
        assertEquals(openAiExecutor, xaiService.orchestrator.toolExecutor)
        assertEquals(openAiExecutor, anthropicService.orchestrator.toolExecutor)
        assertTrue(openAiExecutor is ToolExecutorPort)
    }

    @Test
    fun `withInferenceService injects shared tool executor into llama local service`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.FAST, localConfigId = LocalModelConfigurationId("config-41"))
        val asset = com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset(
            metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                id = LocalModelId("51"),
                huggingFaceModelName = "test/llama",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "llama-sha",
                sizeInBytes = 2048L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )

        coEvery { defaultModelRepository.getDefault(ModelType.FAST) } returns assignment
        coEvery { localModelRepository.getAssetByConfigId(LocalModelConfigurationId("config-41")) } returns asset

        val service = factory.withInferenceService(ModelType.FAST) { it } as LlamaInferenceServiceImpl

        assertTrue(service.orchestrator.toolExecutor is ToolExecutorPort)
    }

    @Test
    fun `withInferenceService routes vision slot through API services only`() = runTest {
        val assignment = DefaultModelAssignment(modelType = ModelType.VISION, apiConfigId = ApiModelConfigurationId("vision-config"))
        val config = ApiModelConfiguration(
            id = ApiModelConfigurationId("vision-config"),
            apiCredentialsId = ApiCredentialsId("vision-creds"),
            displayName = "Vision preset",
        )

        coEvery { defaultModelRepository.getDefault(ModelType.VISION) } returns assignment
        coEvery { apiModelRepository.getConfigurationById(ApiModelConfigurationId("vision-config")) } returns config
        coEvery { apiModelRepository.getCredentialsById(ApiCredentialsId("vision-creds")) } returns apiCredentials(
            modelId = "gpt-4o",
            provider = ApiProvider.OPENAI,
            id = ApiCredentialsId("vision-creds"),
        )
        every { apiKeyProvider.getApiKey("alias") } returns "secret"

        val service = factory.withInferenceService(ModelType.VISION) { it }

        assertTrue(service is ApiInferenceServiceImpl)
        verify { inferenceLockManager.acquireLock(com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType.BYOK) }
        verify(exactly = 0) { inferenceLockManager.acquireLock(com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType.ON_DEVICE) }
    }

    private fun apiCredentials(
        modelId: String,
        provider: ApiProvider = ApiProvider.OPENAI,
        baseUrl: String? = provider.defaultBaseUrl() ?: "https://api.openai.com/v1",
        id: ApiCredentialsId = ApiCredentialsId("11"),
    ): ApiCredentials = ApiCredentials(
        id = id,
        displayName = "OpenAI",
        provider = provider,
        modelId = modelId,
        baseUrl = baseUrl,
        credentialAlias = "alias"
    )

    private suspend fun resolveApiService(
        provider: ApiProvider,
        modelType: ModelType,
        apiConfigId: ApiModelConfigurationId,
        apiCredentialsId: ApiCredentialsId,
        modelId: String,
    ): com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort {
        coEvery { defaultModelRepository.getDefault(modelType) } returns DefaultModelAssignment(
            modelType = modelType,
            apiConfigId = apiConfigId,
        )
        coEvery { apiModelRepository.getConfigurationById(apiConfigId) } returns ApiModelConfiguration(
            id = apiConfigId,
            apiCredentialsId = apiCredentialsId,
            displayName = "${provider.name} preset",
        )
        coEvery { apiModelRepository.getCredentialsById(apiCredentialsId) } returns apiCredentials(
            id = apiCredentialsId,
            modelId = modelId,
            provider = provider,
            baseUrl = when (provider) {
                ApiProvider.OPENAI -> "https://api.openai.com/v1"
                else -> provider.defaultBaseUrl()
            }
        )
        every { apiKeyProvider.getApiKey("alias") } returns "secret"
        return factory.withInferenceService(modelType) { it }
    }

    private fun <T> providerOf(value: T): Provider<T> = object : Provider<T> {
        override fun get(): T = value
    }
}
