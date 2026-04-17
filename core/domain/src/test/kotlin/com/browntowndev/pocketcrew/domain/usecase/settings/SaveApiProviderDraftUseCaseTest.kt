package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCase
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
    fun `creates unique alias and initial preset for new provider draft`() = runTest {
        val credId1 = ApiCredentialsId("1")
        val credId99 = ApiCredentialsId("99")
        val persistedAssets = MutableStateFlow(
            listOf(
                ApiModelAsset(
                    credentials = ApiCredentials(
                        id = credId1,
                        displayName = "Existing xAI",
                        provider = ApiProvider.XAI,
                        modelId = "grok-4-fast-reasoning",
                        baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                        credentialAlias = "xai-grok-4-fast-reasoning",
                    ),
                    configurations = emptyList(),
                )
            )
        )
        every { getApiModelAssetsUseCase() } returns persistedAssets
        coEvery { saveApiCredentialsUseCase(any(), any(), any()) } coAnswers {
            val credentials = invocation.args[0] as ApiCredentials
            persistedAssets.value = listOf(
                ApiModelAsset(
                    credentials = credentials.copy(id = credId99),
                    configurations = emptyList(),
                )
            )
            credId99
        }
        coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
            val configuration = invocation.args[0] as ApiModelConfiguration
            val expectedId = ApiModelConfigurationId("123")
            persistedAssets.value = listOf(
                ApiModelAsset(
                    credentials = persistedAssets.value.single().credentials,
                    configurations = listOf(configuration.copy(id = expectedId)),
                )
            )
            Result.success(expectedId)
        }
        coEvery {
            apiModelRepository.findMatchingCredentials(
                provider = any(),
                modelId = any(),
                baseUrl = any(),
                apiKey = any(),
                sourceCredentialAlias = any(),
            )
        } returns null

        val result = SaveApiProviderDraftUseCase(
            saveApiCredentialsUseCase = saveApiCredentialsUseCase,
            saveApiModelConfigurationUseCase = saveApiModelConfigurationUseCase,
            getApiModelAssetsUseCase = getApiModelAssetsUseCase,
            apiModelRepository = apiModelRepository,
        ).invoke(
            ApiProviderDraft(
                displayName = "New xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                isMultimodal = false,
                credentialAlias = "",
                apiKey = "xai-key",
            )
        ).getOrThrow()

        assertEquals("xai-grok-4-fast-reasoning-2", result.persistedAsset.credentials.credentialAlias)
        assertEquals("Default Preset", result.createdPreset?.displayName)
        assertEquals(credId99, result.createdPreset?.apiCredentialsId)
    }

    @Test
    fun `links to existing asset when api identity already exists`() = runTest {
        val credId42 = ApiCredentialsId("42")
        val existingAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = credId42,
                displayName = "Existing xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                credentialAlias = "existing-xai",
            ),
            configurations = listOf(
                ApiModelConfiguration(
                    id = ApiModelConfigurationId("8"),
                    apiCredentialsId = credId42,
                    displayName = "Default Preset",
                )
            )
        )
        val persistedAssets = MutableStateFlow(listOf(existingAsset))
        every { getApiModelAssetsUseCase() } returns persistedAssets
        coEvery {
            apiModelRepository.findMatchingCredentials(
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                apiKey = "xai-key",
                sourceCredentialAlias = null,
            )
        } returns existingAsset.credentials
        coEvery { saveApiCredentialsUseCase(any(), any(), any()) } returns ApiCredentialsId("0")
        coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
            val configuration = invocation.args[0] as ApiModelConfiguration
            val expectedId = ApiModelConfigurationId("99")
            persistedAssets.value = listOf(
                existingAsset.copy(
                    configurations = listOf(
                        configuration.copy(id = expectedId, apiCredentialsId = existingAsset.credentials.id)
                    )
                )
            )
            Result.success(expectedId)
        }

        val result = SaveApiProviderDraftUseCase(
            saveApiCredentialsUseCase = saveApiCredentialsUseCase,
            saveApiModelConfigurationUseCase = saveApiModelConfigurationUseCase,
            getApiModelAssetsUseCase = getApiModelAssetsUseCase,
            apiModelRepository = apiModelRepository,
        ).invoke(
            ApiProviderDraft(
                displayName = "New xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                isMultimodal = false,
                credentialAlias = "",
                apiKey = "xai-key",
            )
        ).getOrThrow()

        assertEquals(credId42, result.persistedAsset.credentials.id)
        assertEquals("Existing xAI", result.linkedExistingAssetDisplayName)
        assertEquals("Default Preset 2", result.createdPreset?.displayName)
        assertEquals(credId42, result.createdPreset?.apiCredentialsId)
        coVerify(exactly = 0) { saveApiCredentialsUseCase(any(), any(), any()) }
    }
}
