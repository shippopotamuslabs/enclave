package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.FakeDefaultModelRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SetDefaultModelUseCaseTest {
    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var apiModelRepository: ApiModelRepositoryPort
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        apiModelRepository = mockk(relaxed = true)
        useCase = SetDefaultModelUseCaseImpl(repository, apiModelRepository)
    }

    @Test
    fun `set FAST slot to API source`() =
        runTest {
            useCase(ModelType.FAST, localConfigId = null, apiConfigId = ApiModelConfigurationId("5"))

            val assignment = repository.getDefault(ModelType.FAST)
            assertEquals(ApiModelConfigurationId("5"), assignment?.apiConfigId)
        }

    @Test
    fun `set THINKING slot back to ON_DEVICE`() =
        runTest {
            useCase(ModelType.THINKING, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)

            val assignment = repository.getDefault(ModelType.THINKING)
            assertEquals(LocalModelConfigurationId("1"), assignment?.localConfigId)
        }

    @Test
    fun `set TTS slot to TTS provider`() =
        runTest {
            val ttsId = TtsProviderId("tts-1")
            useCase(ModelType.TTS, localConfigId = null, apiConfigId = null, ttsProviderId = ttsId)

            val assignment = repository.getDefault(ModelType.TTS)
            assertEquals(ttsId, assignment?.ttsProviderId)
        }

    @Test
    fun `SCN-USECASE-VISION-001 Vision Default Requires Multimodal Preset`() =
        runTest {
            val configId = ApiModelConfigurationId("config-1")
            val credId = ApiCredentialsId("cred-1")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    isMultimodal = false,
                    displayName = "Text Only",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "OpenAI",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "openai",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.VISION, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("multimodal", ignoreCase = true))
        }

    @Test
    fun `set VISION slot rejects local model assignments`() =
        runTest {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.VISION, localConfigId = LocalModelConfigurationId("vision-local"), apiConfigId = null)
                }

            assertEquals("Vision slot is API-only.", error.message)
        }

    @Test
    fun `SCN-USECASE-CHAT-001 MAIN slot rejects provider that does not support chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-fal")
            val credId = ApiCredentialsId("cred-fal")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "fal-ai/flux",
                    displayName = "FAL Flux",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "FAL",
                    provider = ApiProvider.FAL,
                    credentialAlias = "fal-alias",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.MAIN, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("does not support chat", ignoreCase = true))
        }

    @Test
    fun `SCN-USECASE-CHAT-002 THINKING slot rejects provider that does not support chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-fal-2")
            val credId = ApiCredentialsId("cred-fal-2")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "fal-ai/flux",
                    displayName = "FAL Flux",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "FAL",
                    provider = ApiProvider.FAL,
                    credentialAlias = "fal-alias-2",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.THINKING, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("does not support chat", ignoreCase = true))
        }

    @Test
    fun `MAIN slot accepts provider that supports chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-anthropic")
            val credId = ApiCredentialsId("cred-anthropic")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "claude-opus-4-8",
                    displayName = "Claude",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "Anthropic",
                    provider = ApiProvider.ANTHROPIC,
                    credentialAlias = "anthropic-alias",
                )

            useCase(ModelType.MAIN, localConfigId = null, apiConfigId = configId)

            val assignment = repository.getDefault(ModelType.MAIN)
            assertEquals(configId, assignment?.apiConfigId)
        }

    @Test
    fun `VISION slot rejects provider that does not support chat`() =
        runTest {
            val configId = ApiModelConfigurationId("config-fal-vision")
            val credId = ApiCredentialsId("cred-fal-vision")
            coEvery { apiModelRepository.getConfigurationById(configId) } returns
                ApiModelConfiguration(
                    id = configId,
                    apiCredentialsId = credId,
                    modelId = "fal-ai/flux-pro",
                    isMultimodal = true,
                    displayName = "FAL Flux",
                )
            coEvery { apiModelRepository.getCredentialsById(credId) } returns
                ApiCredentials(
                    id = credId,
                    displayName = "FAL",
                    provider = ApiProvider.FAL,
                    credentialAlias = "fal-vision",
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    useCase(ModelType.VISION, localConfigId = null, apiConfigId = configId)
                }

            assertTrue(error.message!!.contains("does not support vision", ignoreCase = true))
        }
}
