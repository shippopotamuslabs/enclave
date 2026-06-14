package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.ModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.config.DistributionConfigurationPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShouldModeratePromptUseCaseTest {
    private lateinit var distributionConfig: DistributionConfigurationPort
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var trustedDomainRepository: TrustedDomainRepositoryPort
    private lateinit var useCase: ShouldModeratePromptUseCase

    private val trustedDomains = setOf("api.openai.com", "api.x.ai")
    private val trustedWildcards = setOf(".googleapis.com", ".openai.azure.com")

    @BeforeEach
    fun setup() {
        distributionConfig = mockk()
        activeModelProvider = mockk()
        trustedDomainRepository = mockk()
        every { distributionConfig.isPlayStoreFlavor } returns true
        useCase =
            ShouldModeratePromptUseCase(
                distributionConfig,
                activeModelProvider,
                trustedDomainRepository,
                NoOpLoggingPort,
            )

        coEvery { trustedDomainRepository.getTrustedDomains() } returns trustedDomains
        coEvery { trustedDomainRepository.getTrustedWildcards() } returns trustedWildcards
    }

    private fun createActiveConfig(
        id: ModelConfigurationId,
        isLocal: Boolean,
        name: String,
        modelId: String,
        apiProvider: ApiProvider? = null,
        apiBaseUrl: String? = null,
    ) = ActiveModelConfiguration(
        id = id,
        isLocal = isLocal,
        name = name,
        modelId = modelId,
        systemPrompt = "",
        thinkingEnabled = false,
        temperature = null,
        topK = null,
        topP = null,
        maxTokens = null,
        minP = null,
        repetitionPenalty = null,
        contextWindow = null,
        apiProvider = apiProvider,
        apiBaseUrl = apiBaseUrl,
    )

    // --- Normal flavor (isPlayStoreFlavor = false) — always returns false ---

    @Test
    fun invoke_normalFlavor_returnsFalse_regardlessOfConfig() =
        runTest {
            every { distributionConfig.isPlayStoreFlavor } returns false
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns null

            assertFalse(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_normalFlavor_returnsFalse_evenForLocalModel() =
        runTest {
            every { distributionConfig.isPlayStoreFlavor } returns false
            val config =
                createActiveConfig(
                    id = LocalModelConfigurationId("local-id"),
                    isLocal = true,
                    name = "Gemma 1B",
                    modelId = "gemma.gguf",
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertFalse(useCase(ModelType.FAST))
        }

    // --- Play Store flavor (isPlayStoreFlavor = true) — trusted-domain logic ---

    @Test
    fun invoke_playStoreFlavor_returnsTrue_whenNoActiveConfig() =
        runTest {
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns null
            assertTrue(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsTrue_whenLocalConfig() =
        runTest {
            val config =
                createActiveConfig(
                    id = LocalModelConfigurationId("local-id"),
                    isLocal = true,
                    name = "Gemma 1B",
                    modelId = "gemma.gguf",
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertTrue(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsFalse_whenTrustedDomainDirectMatch() =
        runTest {
            val config =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-id"),
                    isLocal = false,
                    name = "GPT-4o",
                    modelId = "gpt-4o",
                    apiProvider = ApiProvider.OPENAI,
                    apiBaseUrl = "https://api.openai.com/v1",
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertFalse(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsFalse_whenTrustedDomainWildcardMatch() =
        runTest {
            val config =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-id"),
                    isLocal = false,
                    name = "Gemini Pro",
                    modelId = "gemini-pro",
                    apiProvider = ApiProvider.GOOGLE,
                    apiBaseUrl = "https://us-central1-aiplatform.googleapis.com/v1",
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertFalse(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsTrue_whenUntrustedDomain() =
        runTest {
            val config =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-id"),
                    isLocal = false,
                    name = "Custom Ablated",
                    modelId = "dolphin-llama3",
                    apiProvider = ApiProvider.OPENROUTER,
                    apiBaseUrl = "https://api.untrusted-model-host.com/v1",
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertTrue(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsFalse_whenApiBaseUrlNullButProviderTrusted() =
        runTest {
            val config =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-id"),
                    isLocal = false,
                    name = "Grok",
                    modelId = "grok-4",
                    apiProvider = ApiProvider.XAI,
                    apiBaseUrl = null,
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertFalse(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsTrue_whenApiBaseUrlNullAndNoProvider() =
        runTest {
            val config =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-id"),
                    isLocal = false,
                    name = "Mystery API",
                    modelId = "mystery-model",
                    apiProvider = null,
                    apiBaseUrl = null,
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertTrue(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsFalse_whenTrustedDomainMatchWithUppercase() =
        runTest {
            val config =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-id"),
                    isLocal = false,
                    name = "GPT-4o",
                    modelId = "gpt-4o",
                    apiProvider = ApiProvider.OPENAI,
                    apiBaseUrl = "https://API.OPENAI.COM/v1",
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertFalse(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_playStoreFlavor_returnsTrue_whenUntrustedSuffixMatchesWildcard() =
        runTest {
            val config =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-id"),
                    isLocal = false,
                    name = "Malicious Host",
                    modelId = "untrusted",
                    apiProvider = ApiProvider.OPENAI,
                    apiBaseUrl = "https://fake-googleapis.com/v1",
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns config

            assertTrue(useCase(ModelType.FAST))
        }

    private object NoOpLoggingPort : LoggingPort {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
