package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SaveApiModelConfigurationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaveApiProviderDraftUseCaseTest {
    private val saveApiCredentialsUseCase = mockk<SaveApiCredentialsUseCase>()
    private val saveApiModelConfigurationUseCase = mockk<SaveApiModelConfigurationUseCase>()
    private val getApiModelAssetsUseCase = mockk<GetApiModelAssetsUseCase>()
    private val apiModelRepository = mockk<ApiModelRepositoryPort>()

    @Test
    fun `SCN-USECASE-SAVE-ASSET-003 Initial Preset Empty Model`() =
        runTest {
            val credId99 = ApiCredentialsId("99")
            val persistedAssets = MutableStateFlow<List<ApiModelAsset>>(emptyList())
            every { getApiModelAssetsUseCase() } returns persistedAssets
            coEvery { saveApiCredentialsUseCase(any(), any(), any()) } coAnswers {
                val credentials = invocation.args[0] as ApiCredentials
                persistedAssets.value =
                    listOf(
                        ApiModelAsset(
                            credentials = credentials.copy(id = credId99),
                            configurations = emptyList(),
                        ),
                    )
                credId99
            }
            coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
                val configuration = invocation.args[0] as ApiModelConfiguration
                val expectedId = ApiModelConfigurationId("123")
                persistedAssets.value =
                    listOf(
                        ApiModelAsset(
                            credentials = persistedAssets.value.single().credentials,
                            configurations = listOf(configuration.copy(id = expectedId)),
                        ),
                    )
                Result.success(expectedId)
            }
            coEvery {
                apiModelRepository.findMatchingCredentials(
                    provider = any(),
                    baseUrl = any(),
                    apiKey = any(),
                    sourceCredentialAlias = any(),
                )
            } returns null

            val result =
                SaveApiProviderDraftUseCase(
                    saveApiCredentialsUseCase = saveApiCredentialsUseCase,
                    saveApiModelConfigurationUseCase = saveApiModelConfigurationUseCase,
                    getApiModelAssetsUseCase = getApiModelAssetsUseCase,
                    apiModelRepository = apiModelRepository,
                ).invoke(
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
            val persistedAssets =
                MutableStateFlow(
                    listOf(
                        ApiModelAsset(
                            credentials =
                                ApiCredentials(
                                    id = credId1,
                                    displayName = "Existing xAI",
                                    provider = ApiProvider.XAI,
                                    baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                                    credentialAlias = "xai-grok-4-fast-reasoning",
                                ),
                            configurations = emptyList(),
                        ),
                    ),
                )
            every { getApiModelAssetsUseCase() } returns persistedAssets
            coEvery { saveApiCredentialsUseCase(any(), any(), any()) } coAnswers {
                val credentials = invocation.args[0] as ApiCredentials
                persistedAssets.value =
                    listOf(
                        ApiModelAsset(
                            credentials = credentials.copy(id = credId99),
                            configurations = emptyList(),
                        ),
                    )
                credId99
            }
            coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
                val configuration = invocation.args[0] as ApiModelConfiguration
                val expectedId = ApiModelConfigurationId("123")
                persistedAssets.value =
                    listOf(
                        ApiModelAsset(
                            credentials = persistedAssets.value.single().credentials,
                            configurations = listOf(configuration.copy(id = expectedId)),
                        ),
                    )
                Result.success(expectedId)
            }
            coEvery {
                apiModelRepository.findMatchingCredentials(
                    provider = any(),
                    baseUrl = any(),
                    apiKey = any(),
                    sourceCredentialAlias = any(),
                )
            } returns null

            val result =
                SaveApiProviderDraftUseCase(
                    saveApiCredentialsUseCase = saveApiCredentialsUseCase,
                    saveApiModelConfigurationUseCase = saveApiModelConfigurationUseCase,
                    getApiModelAssetsUseCase = getApiModelAssetsUseCase,
                    apiModelRepository = apiModelRepository,
                ).invoke(
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
            val existingAsset =
                ApiModelAsset(
                    credentials =
                        ApiCredentials(
                            id = credId42,
                            displayName = "Existing xAI",
                            provider = ApiProvider.XAI,
                            baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                            credentialAlias = "existing-xai",
                        ),
                    configurations =
                        listOf(
                            ApiModelConfiguration(
                                id = ApiModelConfigurationId("8"),
                                apiCredentialsId = credId42,
                                modelId = "",
                                displayName = "Default Preset",
                            ),
                        ),
                )
            val persistedAssets = MutableStateFlow(listOf(existingAsset))
            every { getApiModelAssetsUseCase() } returns persistedAssets
            coEvery {
                apiModelRepository.findMatchingCredentials(
                    provider = ApiProvider.XAI,
                    baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                    apiKey = "xai-key",
                    sourceCredentialAlias = null,
                )
            } returns existingAsset.credentials
            coEvery { saveApiCredentialsUseCase(any(), any(), any()) } returns ApiCredentialsId("0")
            coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
                val configuration = invocation.args[0] as ApiModelConfiguration
                val expectedId = ApiModelConfigurationId("99")
                persistedAssets.value =
                    listOf(
                        existingAsset.copy(
                            configurations =
                                listOf(
                                    configuration.copy(id = expectedId, apiCredentialsId = existingAsset.credentials.id),
                                ),
                        ),
                    )
                Result.success(expectedId)
            }

            val result =
                SaveApiProviderDraftUseCase(
                    saveApiCredentialsUseCase = saveApiCredentialsUseCase,
                    saveApiModelConfigurationUseCase = saveApiModelConfigurationUseCase,
                    getApiModelAssetsUseCase = getApiModelAssetsUseCase,
                    apiModelRepository = apiModelRepository,
                ).invoke(
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
            coVerify(exactly = 0) { saveApiCredentialsUseCase(any(), any(), any()) }
        }
}
