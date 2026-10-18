package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeDistributionConfiguration
import com.browntowndev.enclave.core.testing.FakeTrustedDomainRepository
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.ModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShouldModeratePromptUseCaseTest {
    private lateinit var distributionConfig: FakeDistributionConfiguration
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var trustedDomainRepository: FakeTrustedDomainRepository
    private lateinit var useCase: ShouldModeratePromptUseCase

    private val trustedDomains = setOf("api.openai.com", "api.x.ai")
    private val trustedWildcards = setOf(".googleapis.com", ".openai.azure.com")

    @BeforeEach
    fun setup() {
        distributionConfig = FakeDistributionConfiguration(isPlayStoreFlavor = true)
        activeModelProvider = FakeActiveModelProvider()
        trustedDomainRepository = FakeTrustedDomainRepository()
        useCase =
            ShouldModeratePromptUseCase(
                distributionConfig,
                activeModelProvider,
                trustedDomainRepository,
                NoOpLoggingPort,
            )

        trustedDomainRepository.domains = trustedDomains
        trustedDomainRepository.wildcards = trustedWildcards
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
            distributionConfig.isPlayStoreFlavor = false
            activeModelProvider.configuration = null

            assertFalse(useCase(ModelType.FAST))
        }

    @Test
    fun invoke_normalFlavor_returnsFalse_evenForLocalModel() =
        runTest {
            distributionConfig.isPlayStoreFlavor = false
            val config =
                createActiveConfig(
                    id = LocalModelConfigurationId("local-id"),
                    isLocal = true,
                    name = "Gemma 1B",
                    modelId = "gemma.gguf",
                )
            activeModelProvider.configuration = config

            assertFalse(useCase(ModelType.FAST))
        }

    // --- Play Store flavor (isPlayStoreFlavor = true) — trusted-domain logic ---

    @Test
    fun invoke_playStoreFlavor_returnsTrue_whenNoActiveConfig() =
        runTest {
            activeModelProvider.configuration = null
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
            activeModelProvider.configuration = config

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
            activeModelProvider.configuration = config

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
            activeModelProvider.configuration = config

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
            activeModelProvider.configuration = config

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
            activeModelProvider.configuration = config

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
            activeModelProvider.configuration = config

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
            activeModelProvider.configuration = config

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
            activeModelProvider.configuration = config

            assertTrue(useCase(ModelType.FAST))
        }

    // --- Override resolution (OTHER mode) — must resolve config from override, NOT FAST slot ---

    @Test
    fun invoke_override_usesOverrideConfig_notFastSlot() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-untrusted-override"),
                    displayName = "Custom Untrusted",
                )
            // FAST slot is trusted (OpenAI); override is NOT (untrusted host).
            // Moderation must reflect the override config, not the FAST slot.
            val fastSlotConfig =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-fast-slot"),
                    isLocal = false,
                    name = "Fast Slot (Trusted)",
                    modelId = "gpt-4o",
                    apiProvider = ApiProvider.OPENAI,
                    apiBaseUrl = "https://api.openai.com/v1",
                )
            val overrideConfig =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-untrusted-override"),
                    isLocal = false,
                    name = "Custom Untrusted",
                    modelId = "dolphin-llama3",
                    apiProvider = ApiProvider.OPENROUTER,
                    apiBaseUrl = "https://api.untrusted-model-host.com/v1",
                )
            activeModelProvider.configuration = fastSlotConfig
            activeModelProvider.hasOverrideConfiguration = true
            activeModelProvider.overrideConfiguration = overrideConfig

            assertTrue(useCase(override))

            // Mutation defense: the FAST slot must never be consulted for an override.
            assertTrue(activeModelProvider.requestedModelTypes.isEmpty())
            assertEquals(listOf(override), activeModelProvider.requestedOverrides)
        }

    @Test
    fun invoke_override_returnsTrue_whenOverrideResolvesToLocalConfig() =
        runTest {
            val override =
                ChatModelOverride.Local(
                    configId = LocalModelConfigurationId("local-override"),
                    displayName = "Local Override",
                )
            val localConfig =
                createActiveConfig(
                    id = LocalModelConfigurationId("local-override"),
                    isLocal = true,
                    name = "Local Override",
                    modelId = "gemma.gguf",
                )
            activeModelProvider.hasOverrideConfiguration = true
            activeModelProvider.overrideConfiguration = localConfig

            assertTrue(useCase(override))
        }

    @Test
    fun invoke_override_returnsFalse_whenOverrideResolvesToTrustedApiConfig() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-trusted"),
                    displayName = "Trusted Override",
                )
            val trustedConfig =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-trusted"),
                    isLocal = false,
                    name = "Trusted Override",
                    modelId = "gpt-4o",
                    apiProvider = ApiProvider.OPENAI,
                    apiBaseUrl = "https://api.openai.com/v1",
                )
            activeModelProvider.hasOverrideConfiguration = true
            activeModelProvider.overrideConfiguration = trustedConfig

            assertFalse(useCase(override))
        }

    @Test
    fun invoke_override_returnsTrue_whenOverrideResolvesToUntrustedApiConfig() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-untrusted"),
                    displayName = "Untrusted Override",
                )
            val untrustedConfig =
                createActiveConfig(
                    id = ApiModelConfigurationId("api-untrusted"),
                    isLocal = false,
                    name = "Untrusted Override",
                    modelId = "dolphin-llama3",
                    apiProvider = ApiProvider.OPENROUTER,
                    apiBaseUrl = "https://api.untrusted-model-host.com/v1",
                )
            activeModelProvider.hasOverrideConfiguration = true
            activeModelProvider.overrideConfiguration = untrustedConfig

            assertTrue(useCase(override))
        }

    @Test
    fun invoke_override_returnsTrue_whenOverrideResolvesToNullConfig() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-missing"),
                    displayName = "Missing",
                )
            activeModelProvider.hasOverrideConfiguration = true
            activeModelProvider.overrideConfiguration = null

            assertTrue(useCase(override))
        }

    @Test
    fun invoke_override_normalFlavor_returnsFalse() =
        runTest {
            distributionConfig.isPlayStoreFlavor = false
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-any"),
                    displayName = "Any",
                )

            assertFalse(useCase(override))
        }

    private object NoOpLoggingPort : LoggingPort {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
