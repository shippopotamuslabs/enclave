package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.SaveApiCredentialsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.SaveApiModelConfigurationUseCaseImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveApiProviderDraftUseCaseTest {
    private lateinit var apiModelRepository: FakeApiModelRepository
    private lateinit var useCase: SaveApiProviderDraftUseCase

    @BeforeEach
    fun setUp() {
        apiModelRepository = FakeApiModelRepository()
        useCase =
            SaveApiProviderDraftUseCase(
                saveApiCredentialsUseCase = SaveApiCredentialsUseCaseImpl(apiModelRepository),
                saveApiModelConfigurationUseCase = SaveApiModelConfigurationUseCaseImpl(apiModelRepository),
                getApiModelAssetsUseCase = GetApiModelAssetsUseCaseImpl(apiModelRepository),
                apiModelRepository = apiModelRepository,
            )
    }

    @Test
    fun `SCN-USECASE-SAVE-ASSET-003 Initial Preset Empty Model`() =
        runTest {
            val credId99 = ApiCredentialsId("99")
            apiModelRepository.saveCredentialsResultId = credId99

            val result =
                useCase(
                    ApiProviderDraft(
                        displayName = "Work OpenAI",
                        provider = ApiProvider.OPENAI,
                        baseUrl = "https://api.openai.com/v1",
                        credentialAlias = "",
                        apiKey = "sk-1",
                    ),
                ).getOrThrow()

            assertEquals("", result.createdPreset?.modelId)
            assertEquals(false, result.createdPreset?.isMultimodal)
        }

    @Test
    fun `SCN-USECASE-SAVE-ASSET-001 New Asset Alias From Provider Only`() =
        runTest {
            val credId1 = ApiCredentialsId("1")
            val credId99 = ApiCredentialsId("99")
            // An existing xAI asset whose stored key differs from the draft's key, so
            // findMatchingCredentials must not link the draft to it.
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId1,
                        displayName = "Existing xAI",
                        provider = ApiProvider.XAI,
                        baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                        credentialAlias = "xai-grok-4-fast-reasoning",
                    ),
                ),
                apiKeys = mapOf(credId1 to "a-different-key"),
            )
            apiModelRepository.saveCredentialsResultId = credId99

            val result =
                useCase(
                    ApiProviderDraft(
                        displayName = "New xAI",
                        provider = ApiProvider.XAI,
                        baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                        credentialAlias = "",
                        apiKey = "xai-key",
                    ),
                ).getOrThrow()

            assertEquals("xai", result.persistedAsset.credentials.credentialAlias)
            assertEquals("Default Preset", result.createdPreset?.displayName)
            assertEquals(credId99, result.createdPreset?.apiCredentialsId)
        }

    @Test
    fun `SCN-USECASE-SAVE-ASSET-002 Link Existing Credential Without Model Match`() =
        runTest {
            val credId42 = ApiCredentialsId("42")
            // The existing credential's stored key matches the draft's key, so the draft
            // links to it instead of saving new credentials.
            apiModelRepository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId42,
                        displayName = "Existing xAI",
                        provider = ApiProvider.XAI,
                        baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                        credentialAlias = "existing-xai",
                    ),
                ),
                apiKeys = mapOf(credId42 to "xai-key"),
            )
            apiModelRepository.seedConfigurations(
                listOf(
                    ApiModelConfiguration(
                        id = ApiModelConfigurationId("8"),
                        apiCredentialsId = credId42,
                        modelId = "",
                        displayName = "Default Preset",
                    ),
                ),
            )

            val result =
                useCase(
                    ApiProviderDraft(
                        displayName = "New xAI",
                        provider = ApiProvider.XAI,
                        baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                        credentialAlias = "",
                        apiKey = "xai-key",
                    ),
                ).getOrThrow()

            assertEquals(credId42, result.persistedAsset.credentials.id)
            assertEquals("Existing xAI", result.linkedExistingAssetDisplayName)
            assertEquals("Default Preset 2", result.createdPreset?.displayName)
            assertEquals(credId42, result.createdPreset?.apiCredentialsId)
            assertTrue(apiModelRepository.saveCredentialsCalls.isEmpty())
        }

    @Test
    fun `SCN-USECASE-SAVE-ASSET-004 OpenAI alias avoids soft-deleted alias collision`() =
        runTest {
            val credId99 = ApiCredentialsId("99")
            // The "openai" alias is already taken (e.g. by a soft-deleted credential).
            apiModelRepository.additionalCredentialAliases = setOf("openai")
            apiModelRepository.saveCredentialsResultId = credId99

            val result =
                useCase(
                    ApiProviderDraft(
                        displayName = "My OpenAI",
                        provider = ApiProvider.OPENAI,
                        baseUrl = null,
                        credentialAlias = "",
                        apiKey = "sk-openai",
                    ),
                ).getOrThrow()

            assertEquals("openai-2", result.persistedAsset.credentials.credentialAlias)
        }
}
