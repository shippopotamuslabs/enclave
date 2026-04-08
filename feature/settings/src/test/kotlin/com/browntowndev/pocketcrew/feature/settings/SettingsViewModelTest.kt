package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelMetadataUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateAllowMemoriesUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomPromptTextUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticPressUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticResponseUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateThemeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val getSettingsUseCase = mockk<GetSettingsUseCase>()
    private val updateThemeUseCase = mockk<UpdateThemeUseCase>(relaxed = true)
    private val updateHapticPressUseCase = mockk<UpdateHapticPressUseCase>(relaxed = true)
    private val updateHapticResponseUseCase = mockk<UpdateHapticResponseUseCase>(relaxed = true)
    private val updateCustomizationEnabledUseCase = mockk<UpdateCustomizationEnabledUseCase>(relaxed = true)
    private val updateSelectedPromptOptionUseCase = mockk<UpdateSelectedPromptOptionUseCase>(relaxed = true)
    private val updateCustomPromptTextUseCase = mockk<UpdateCustomPromptTextUseCase>(relaxed = true)
    private val updateAllowMemoriesUseCase = mockk<UpdateAllowMemoriesUseCase>(relaxed = true)
    private val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
    private val saveLocalModelConfigurationUseCase = mockk<SaveLocalModelConfigurationUseCase>(relaxed = true)
    private val deleteLocalModelConfigurationUseCase = mockk<DeleteLocalModelConfigurationUseCase>(relaxed = true)
    private val deleteLocalModelMetadataUseCase = mockk<DeleteLocalModelMetadataUseCase>(relaxed = true)
    private val deleteLocalModelUseCase = mockk<DeleteLocalModelUseCase>(relaxed = true)
    private val getApiModelAssetsUseCase = mockk<GetApiModelAssetsUseCase>()
    private val fetchApiProviderModelDetailUseCase = mockk<FetchApiProviderModelDetailUseCase>(relaxed = true)
    private val fetchApiProviderModelsUseCase = mockk<FetchApiProviderModelsUseCase>()
    private val saveApiCredentialsUseCase = mockk<SaveApiCredentialsUseCase>(relaxed = true)
    private val deleteApiCredentialsUseCase = mockk<DeleteApiCredentialsUseCase>(relaxed = true)
    private val saveApiModelConfigurationUseCase = mockk<SaveApiModelConfigurationUseCase>(relaxed = true)
    private val deleteApiModelConfigurationUseCase = mockk<DeleteApiModelConfigurationUseCase>(relaxed = true)
    private val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
    private val setDefaultModelUseCase = mockk<SetDefaultModelUseCase>(relaxed = true)
    private val errorHandler = object : ViewModelErrorHandler {
        override val errorEvents = MutableSharedFlow<String>()

        override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) = Unit

        override fun coroutineExceptionHandler(
            tag: String,
            message: String,
            userMessage: String
        ): CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }
    }

    @Test
    fun `add config reuses fetched metadata for same asset after returning to list`() = runTest {
        val asset = ApiModelAsset(
            credentials = ApiCredentials(
                id = 42L,
                displayName = "OpenAI",
                provider = ApiProvider.OPENAI,
                modelId = "gpt-4.1",
                baseUrl = null,
                credentialAlias = "openai-gpt-4-1"
            ),
            configurations = listOf(
                ApiModelConfiguration(
                    id = 7L,
                    apiCredentialsId = 42L,
                    displayName = "Default Preset",
                )
            )
        )
        val discoveredModels = listOf(
            DiscoveredApiModel(
                id = "gpt-4.1",
                name = "GPT-4.1",
                contextWindowTokens = 128_000,
                maxOutputTokens = 16_000
            )
        )
        val viewModel = createViewModel(apiAssets = listOf(asset))

        val selectedAsset = ApiModelAssetUi(
            credentialsId = asset.credentials.id,
            displayName = asset.credentials.displayName,
            provider = asset.credentials.provider,
            modelId = asset.credentials.modelId,
            baseUrl = asset.credentials.baseUrl,
            isVision = asset.credentials.isVision,
            credentialAlias = asset.credentials.credentialAlias,
            configurations = asset.configurations.map {
                ApiModelConfigUi(
                    id = it.id,
                    credentialsId = it.apiCredentialsId,
                    displayName = it.displayName,
                )
            }
        )

        coEvery {
            fetchApiProviderModelsUseCase(
                provider = ApiProvider.OPENAI,
                currentApiKey = "",
                credentialAlias = "openai-gpt-4-1",
                baseUrl = null
            )
        } returns discoveredModels
        coEvery {
            fetchApiProviderModelDetailUseCase(
                provider = any(),
                modelId = any(),
                currentApiKey = any(),
                credentialAlias = any(),
                baseUrl = any(),
            )
        } returns null

        viewModel.onSelectApiModelAsset(selectedAsset)
        viewModel.onFetchApiModels()
        runCurrent()

        viewModel.onBackToByokList()
        viewModel.onSelectApiModelAsset(selectedAsset)
        viewModel.onSelectApiModelConfig(ApiModelConfigUi(displayName = "Creative"))
        runCurrent()

        val config = viewModel.uiState.value.selectedApiModelConfig
        assertEquals("128000", config?.contextWindow)
        assertEquals("4000", config?.maxTokens)
    }

    @Test
    fun `fetch models uses selected reusable credential alias for new asset`() = runTest {
        val reusableAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = 99L,
                displayName = "Saved xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-3",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                credentialAlias = "saved-xai-key"
            ),
            configurations = emptyList()
        )
        val viewModel = createViewModel(apiAssets = listOf(reusableAsset))
        val newAsset = ApiModelAssetUi(
            credentialsId = 0L,
            displayName = "New xAI",
            provider = ApiProvider.XAI,
            modelId = "",
            baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            isVision = false,
            credentialAlias = "",
            configurations = emptyList()
        )

        coEvery {
            fetchApiProviderModelsUseCase(
                provider = ApiProvider.XAI,
                currentApiKey = "",
                credentialAlias = "saved-xai-key",
                baseUrl = ApiProvider.XAI.defaultBaseUrl()
            )
        } returns emptyList()
        coEvery {
            fetchApiProviderModelDetailUseCase(
                provider = ApiProvider.XAI,
                modelId = "",
                currentApiKey = "",
                credentialAlias = "saved-xai-key",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            )
        } returns null

        viewModel.onSelectApiModelAsset(newAsset)
        viewModel.onSelectReusableApiCredential(99L)
        viewModel.onFetchApiModels()
        runCurrent()

        coVerify {
            fetchApiProviderModelsUseCase(
                provider = ApiProvider.XAI,
                currentApiKey = "",
                credentialAlias = "saved-xai-key",
                baseUrl = ApiProvider.XAI.defaultBaseUrl()
            )
        }
    }

    @Test
    fun `xai discovered context window populates config and suggests max tokens`() = runTest {
        val asset = ApiModelAssetUi(
            credentialsId = 42L,
            displayName = "xAI",
            provider = ApiProvider.XAI,
            modelId = "grok-4.1-fast-reasoning",
            baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            isVision = false,
            credentialAlias = "saved-xai-key",
            configurations = emptyList()
        )

        coEvery {
            fetchApiProviderModelsUseCase(
                provider = ApiProvider.XAI,
                currentApiKey = "",
                credentialAlias = "saved-xai-key",
                baseUrl = ApiProvider.XAI.defaultBaseUrl()
            )
        } returns listOf(
            DiscoveredApiModel(
                id = "grok-4.1-fast-reasoning",
            )
        )
        coEvery {
            fetchApiProviderModelDetailUseCase(
                provider = ApiProvider.XAI,
                modelId = "grok-4.1-fast-reasoning",
                currentApiKey = "",
                credentialAlias = "saved-xai-key",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            )
        } returns DiscoveredApiModel(
            id = "grok-4.1-fast-reasoning",
            contextWindowTokens = 131_072,
        )

        val viewModel = createViewModel()
        viewModel.onSelectApiModelAsset(asset)
        viewModel.onFetchApiModels()
        runCurrent()

        viewModel.onSelectApiModelConfig(ApiModelConfigUi(displayName = "Creative"))

        val config = viewModel.uiState.value.selectedApiModelConfig
        assertEquals("131072", config?.contextWindow)
        assertEquals("32768", config?.maxTokens)
    }

    @Test
    fun `xai model selection fetches detail when list response lacks context`() = runTest {
        val assetWithoutModel = ApiModelAssetUi(
            credentialsId = 0L,
            displayName = "xAI",
            provider = ApiProvider.XAI,
            modelId = "",
            baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            isVision = false,
            credentialAlias = "saved-xai-key",
            configurations = emptyList()
        )
        val selectedAsset = assetWithoutModel.copy(modelId = "grok-4-fast-reasoning")

        coEvery {
            fetchApiProviderModelsUseCase(
                provider = ApiProvider.XAI,
                currentApiKey = "",
                credentialAlias = "saved-xai-key",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            )
        } returns listOf(
            DiscoveredApiModel(id = "grok-4-fast-reasoning")
        )
        coEvery {
            fetchApiProviderModelDetailUseCase(
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                currentApiKey = "",
                credentialAlias = "saved-xai-key",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            )
        } returns DiscoveredApiModel(
            id = "grok-4-fast-reasoning",
            contextWindowTokens = 256_000,
        )

        val viewModel = createViewModel()
        viewModel.onSelectApiModelAsset(assetWithoutModel)
        viewModel.onFetchApiModels()
        runCurrent()

        viewModel.onApiModelAssetFieldChange(selectedAsset)
        runCurrent()
        viewModel.onSelectApiModelConfig(ApiModelConfigUi(displayName = "Default Preset"))

        val config = viewModel.uiState.value.selectedApiModelConfig
        assertEquals("256000", config?.contextWindow)
        assertEquals("64000", config?.maxTokens)
    }

    @Test
    fun `save api credentials continues into preset editing after Room emits the inserted asset`() = runTest {
        val persistedAssets = MutableStateFlow<List<ApiModelAsset>>(emptyList())
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = 88L,
                displayName = "New xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                credentialAlias = "xai-grok-4-fast-reasoning"
            ),
            configurations = emptyList()
        )

        viewModel.onStartCreateApiModelAsset()
        viewModel.onApiModelAssetFieldChange(
            ApiModelAssetUi(
                credentialsId = 0L,
                displayName = "New xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                isVision = false,
                credentialAlias = "",
                configurations = emptyList()
            )
        )
        viewModel.onApiKeyChange("xai-key")

        coEvery { saveApiCredentialsUseCase(any(), any(), any()) } coAnswers {
            persistedAssets.value = listOf(persistedAsset)
            88L
        }
        coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
            persistedAssets.value = listOf(
                persistedAsset.copy(
                    configurations = listOf(
                        ApiModelConfiguration(
                            id = 92L,
                            apiCredentialsId = 88L,
                            displayName = "Default Preset",
                        )
                    )
                )
            )
            Result.success(92L)
        }

        var savedAsset: ApiModelAssetUi? = null
        var savedConfig: ApiModelConfigUi? = null
        viewModel.onSaveApiCredentials { assetUi, configUi ->
            savedAsset = assetUi
            savedConfig = configUi
        }
        runCurrent()

        assertEquals(88L, savedAsset?.credentialsId)
        assertEquals("Default Preset", savedConfig?.displayName)
        assertEquals(88L, viewModel.uiState.value.apiCredentialDraft?.credentialsId)
        coVerify(exactly = 1) {
            saveApiModelConfigurationUseCase(
                match {
                    it.apiCredentialsId == 88L &&
                        it.displayName == "Default Preset"
                }
            )
        }
    }

    @Test
    fun `save api model config does not navigate on failure`() = runTest {
        val asset = ApiModelAssetUi(
            credentialsId = 42L,
            displayName = "xAI",
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-reasoning",
            baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            isVision = false,
            credentialAlias = "saved-xai-key",
            configurations = emptyList()
        )
        val viewModel = createViewModel()
        viewModel.onSelectApiModelAsset(asset)
        viewModel.onSelectApiModelConfig(
            ApiModelConfigUi(
                id = 9L,
                credentialsId = 42L,
                displayName = "Default Preset"
            )
        )

        coEvery {
            saveApiModelConfigurationUseCase(any())
        } returns Result.failure(IllegalArgumentException("Parent credentials not found"))

        var onSuccessCalled = false
        viewModel.onSaveApiModelConfig {
            onSuccessCalled = true
        }
        runCurrent()

        assertFalse(onSuccessCalled)
    }

    @Test
    fun `save new provider waits for Room-backed asset before continuing into preset editing`() = runTest {
        val persistedAssets = MutableStateFlow<List<ApiModelAsset>>(emptyList())
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)

        viewModel.onSelectApiModelAsset(
            ApiModelAssetUi(
                credentialsId = 0L,
                displayName = "New xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                isVision = false,
                credentialAlias = "",
                configurations = emptyList()
            )
        )
        viewModel.onApiKeyChange("xai-key")

        coEvery { saveApiCredentialsUseCase(any(), any(), any()) } returns 88L
        coEvery { saveApiModelConfigurationUseCase(any()) } returns Result.success(901L)

        var savedAsset: ApiModelAssetUi? = null
        var savedConfig: ApiModelConfigUi? = null
        viewModel.onSaveApiCredentials { assetUi, configUi ->
            savedAsset = assetUi
            savedConfig = configUi
        }
        runCurrent()

        assertNull(savedAsset)
        assertNull(savedConfig)
    }

    @Test
    fun `save preset uses draft parent id instead of currently selected asset id`() = runTest {
        val providerA = ApiModelAssetUi(
            credentialsId = 11L,
            displayName = "Provider A",
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-reasoning",
            baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            isVision = false,
            credentialAlias = "provider-a",
            configurations = emptyList()
        )
        val providerB = providerA.copy(
            credentialsId = 22L,
            displayName = "Provider B",
            credentialAlias = "provider-b"
        )
        val viewModel = createViewModel()

        viewModel.onSelectApiModelAsset(providerA)
        viewModel.onSelectApiModelConfig(
            ApiModelConfigUi(
                credentialsId = 11L,
                displayName = "New Preset"
            )
        )
        viewModel.onApiModelAssetFieldChange(providerB)

        coEvery { saveApiModelConfigurationUseCase(any()) } returns Result.success(44L)

        viewModel.onSaveApiModelConfig {}
        runCurrent()

        coVerify {
            saveApiModelConfigurationUseCase(
                match { it.apiCredentialsId == 11L }
            )
        }
    }

    @Test
    fun `canceling byok editor discards unsaved draft edits and preserves persisted list`() = runTest {
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = 42L,
                displayName = "Persisted xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                credentialAlias = "persisted-xai"
            ),
            configurations = emptyList()
        )
        val persistedAssets = MutableStateFlow(listOf(persistedAsset))
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)

        val selectedAsset = viewModel.uiState.value.apiModels.single()
        viewModel.onSelectApiModelAsset(selectedAsset)
        viewModel.onApiModelAssetFieldChange(
            selectedAsset.copy(
                displayName = "Unsaved Draft Name",
                modelId = "grok-4.1"
            )
        )
        runCurrent()

        assertEquals("Unsaved Draft Name", viewModel.uiState.value.apiCredentialDraft?.displayName)
        assertEquals("Persisted xAI", viewModel.uiState.value.apiModels.single().displayName)

        viewModel.onBackToByokList()
        runCurrent()

        assertNull(viewModel.uiState.value.apiCredentialDraft)
        assertNull(viewModel.uiState.value.selectedApiModelAsset)
        assertNull(viewModel.uiState.value.selectedApiModelConfig)
        assertEquals("Persisted xAI", viewModel.uiState.value.apiModels.single().displayName)
    }

    @Test
    fun `room emission does not splice over unsaved api credential draft`() = runTest {
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = 51L,
                displayName = "Persisted OpenAI",
                provider = ApiProvider.OPENAI,
                modelId = "gpt-4.1",
                baseUrl = "https://api.openai.com/v1",
                credentialAlias = "persisted-openai"
            ),
            configurations = emptyList()
        )
        val persistedAssets = MutableStateFlow(listOf(persistedAsset))
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)

        val selectedAsset = viewModel.uiState.value.apiModels.single()
        viewModel.onSelectApiModelAsset(selectedAsset)
        viewModel.onApiModelAssetFieldChange(
            selectedAsset.copy(displayName = "Unsaved Draft Name")
        )

        persistedAssets.value = listOf(
            persistedAsset.copy(
                credentials = persistedAsset.credentials.copy(displayName = "Room Refreshed Name")
            )
        )
        runCurrent()

        assertEquals("Unsaved Draft Name", viewModel.uiState.value.apiCredentialDraft?.displayName)
        assertEquals("Room Refreshed Name", viewModel.uiState.value.apiModels.single().displayName)
    }

    @Test
    fun `custom header edits stay in draft and cleanup trims blank rows only from draft`() = runTest {
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = 64L,
                displayName = "Persisted xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                credentialAlias = "persisted-xai"
            ),
            configurations = listOf(
                ApiModelConfiguration(
                    id = 9L,
                    apiCredentialsId = 64L,
                    displayName = "Default Preset",
                    customHeaders = mapOf("Authorization" to "Bearer persisted")
                )
            )
        )
        val persistedAssets = MutableStateFlow(listOf(persistedAsset))
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)

        val selectedAsset = viewModel.uiState.value.apiModels.single()
        val selectedConfig = selectedAsset.configurations.single()
        viewModel.onSelectApiModelAsset(selectedAsset)
        viewModel.onSelectApiModelConfig(selectedConfig)
        viewModel.onAddCustomHeader()
        viewModel.onCustomHeaderChange(0, CustomHeaderUi("", ""))
        viewModel.onCustomHeaderChange(1, CustomHeaderUi("X-Test", "123"))
        runCurrent()

        assertEquals(
            listOf(CustomHeaderUi("", ""), CustomHeaderUi("X-Test", "123")),
            viewModel.uiState.value.selectedApiModelConfig?.customHeaders
        )
        assertEquals(
            listOf(CustomHeaderUi("Authorization", "Bearer persisted")),
            viewModel.uiState.value.apiModels.single().configurations.single().customHeaders
        )

        viewModel.onCleanupCustomHeaders()
        runCurrent()

        assertEquals(
            listOf(CustomHeaderUi("X-Test", "123")),
            viewModel.uiState.value.selectedApiModelConfig?.customHeaders
        )
        assertEquals(
            listOf(CustomHeaderUi("Authorization", "Bearer persisted")),
            viewModel.uiState.value.apiModels.single().configurations.single().customHeaders
        )
    }

    private fun createViewModel(
        apiAssets: List<ApiModelAsset> = emptyList(),
        apiAssetsFlow: Flow<List<ApiModelAsset>> = flowOf(apiAssets)
    ): SettingsViewModel {
        every { getSettingsUseCase() } returns flowOf(SettingsData())
        every { getLocalModelAssetsUseCase() } returns flowOf(emptyList())
        coEvery { getLocalModelAssetsUseCase.getSoftDeletedModels() } returns emptyList()
        every { getApiModelAssetsUseCase() } returns apiAssetsFlow
        every { getDefaultModelsUseCase() } returns flowOf(emptyList())

        return SettingsViewModel(
            savedStateHandle = SavedStateHandle(),
            getSettingsUseCase = getSettingsUseCase,
            updateThemeUseCase = updateThemeUseCase,
            updateHapticPressUseCase = updateHapticPressUseCase,
            updateHapticResponseUseCase = updateHapticResponseUseCase,
            updateCustomizationEnabledUseCase = updateCustomizationEnabledUseCase,
            updateSelectedPromptOptionUseCase = updateSelectedPromptOptionUseCase,
            updateCustomPromptTextUseCase = updateCustomPromptTextUseCase,
            updateAllowMemoriesUseCase = updateAllowMemoriesUseCase,
            getLocalModelAssetsUseCase = getLocalModelAssetsUseCase,
            saveLocalModelConfigurationUseCase = saveLocalModelConfigurationUseCase,
            deleteLocalModelConfigurationUseCase = deleteLocalModelConfigurationUseCase,
            deleteLocalModelMetadataUseCase = deleteLocalModelMetadataUseCase,
            deleteLocalModelUseCase = deleteLocalModelUseCase,
            getApiModelAssetsUseCase = getApiModelAssetsUseCase,
            fetchApiProviderModelDetailUseCase = fetchApiProviderModelDetailUseCase,
            fetchApiProviderModelsUseCase = fetchApiProviderModelsUseCase,
            saveApiCredentialsUseCase = saveApiCredentialsUseCase,
            deleteApiCredentialsUseCase = deleteApiCredentialsUseCase,
            saveApiModelConfigurationUseCase = saveApiModelConfigurationUseCase,
            deleteApiModelConfigurationUseCase = deleteApiModelConfigurationUseCase,
            getDefaultModelsUseCase = getDefaultModelsUseCase,
            setDefaultModelUseCase = setDefaultModelUseCase,
            errorHandler = errorHandler
        )
    }
}
