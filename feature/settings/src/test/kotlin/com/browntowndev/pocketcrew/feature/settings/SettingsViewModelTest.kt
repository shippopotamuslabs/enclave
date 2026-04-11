package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
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
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.DiscoverApiModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.ExecuteModelDeletionWithReassignmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetRestorableLocalModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.PrepareModelDeletionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SaveApiPresetUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SaveApiProviderDraftUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SaveTavilyApiKeyUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SaveLocalModelPresetUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsDeletionUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsLocalModelUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.ClearTavilyApiKeyUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateAllowMemoriesUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomPromptTextUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticPressUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticResponseUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSearchEnabledUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateThemeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
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
    private val updateSearchEnabledUseCase = mockk<UpdateSearchEnabledUseCase>(relaxed = true)
    private val saveTavilyApiKeyUseCase = mockk<SaveTavilyApiKeyUseCase>(relaxed = true)
    private val clearTavilyApiKeyUseCase = mockk<ClearTavilyApiKeyUseCase>(relaxed = true)
    private val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
    private val saveLocalModelConfigurationUseCase = mockk<SaveLocalModelConfigurationUseCase>(relaxed = true)
    private val deleteLocalModelConfigurationUseCase = mockk<DeleteLocalModelConfigurationUseCase>(relaxed = true)
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
    private val apiModelRepository = mockk<ApiModelRepositoryPort>()
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
                id = ApiCredentialsId("42"),
                displayName = "OpenAI",
                provider = ApiProvider.OPENAI,
                modelId = "gpt-4.1",
                baseUrl = null,
                credentialAlias = "openai-gpt-4-1"
            ),
            configurations = listOf(
                ApiModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    apiCredentialsId = ApiCredentialsId("42"),
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

        val config = viewModel.uiState.value.apiProviderEditor.presetDraft
        assertEquals("128000", config?.contextWindow)
        assertEquals("4000", config?.maxTokens)
    }

    @Test
    fun `fetch models uses selected reusable credential alias for new asset`() = runTest {
        val reusableAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = ApiCredentialsId("99"),
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
            credentialsId = ApiCredentialsId(""),
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
        viewModel.onSelectReusableApiCredential(ApiCredentialsId("99"))
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
            credentialsId = ApiCredentialsId("42"),
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

        val config = viewModel.uiState.value.apiProviderEditor.presetDraft
        assertEquals("131072", config?.contextWindow)
        assertEquals("32768", config?.maxTokens)
    }

    @Test
    fun `xai model selection fetches detail when list response lacks context`() = runTest {
        val assetWithoutModel = ApiModelAssetUi(
            credentialsId = ApiCredentialsId(""),
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

        val config = viewModel.uiState.value.apiProviderEditor.presetDraft
        assertEquals("256000", config?.contextWindow)
        assertEquals("64000", config?.maxTokens)
    }

    @Test
    fun `discovered model vision metadata normalizes api asset draft`() = runTest {
        val asset = ApiModelAssetUi(
            credentialsId = ApiCredentialsId(""),
            displayName = "OpenRouter Vision Candidate",
            provider = ApiProvider.OPENROUTER,
            modelId = "openai/gpt-4.1-mini",
            baseUrl = ApiProvider.OPENROUTER.defaultBaseUrl(),
            isVision = false,
            credentialAlias = "",
            configurations = emptyList()
        )

        coEvery {
            fetchApiProviderModelsUseCase(
                provider = ApiProvider.OPENROUTER,
                currentApiKey = "openrouter-key",
                credentialAlias = null,
                baseUrl = ApiProvider.OPENROUTER.defaultBaseUrl(),
            )
        } returns listOf(
            DiscoveredApiModel(
                id = "openai/gpt-4.1-mini",
                visionCapable = true,
            )
        )
        coEvery {
            fetchApiProviderModelDetailUseCase(
                provider = ApiProvider.OPENROUTER,
                modelId = any(),
                currentApiKey = any(),
                credentialAlias = any(),
                baseUrl = any(),
            )
        } returns null

        val viewModel = createViewModel()
        viewModel.onSelectApiModelAsset(asset)
        viewModel.onApiKeyChange("openrouter-key")
        viewModel.onFetchApiModels()
        runCurrent()

        assertTrue(viewModel.uiState.value.apiProviderEditor.assetDraft?.isVision == true)
    }

    @Test
    fun `save api credentials continues into preset editing after Room emits the inserted asset`() = runTest {
        val persistedAssets = MutableStateFlow<List<ApiModelAsset>>(emptyList())
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = ApiCredentialsId("88"),
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
                credentialsId = ApiCredentialsId(""),
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
            ApiCredentialsId("88")
        }
        coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
            persistedAssets.value = listOf(
                persistedAsset.copy(
                    configurations = listOf(
                        ApiModelConfiguration(
                            id = ApiModelConfigurationId("92"),
                            apiCredentialsId = ApiCredentialsId("88"),
                            displayName = "Default Preset",
                        )
                    )
                )
            )
            Result.success(ApiModelConfigurationId("92"))
        }

        var savedAsset: ApiModelAssetUi? = null
        var savedConfig: ApiModelConfigUi? = null
        viewModel.onSaveApiCredentials { assetUi, configUi ->
            savedAsset = assetUi
            savedConfig = configUi
        }
        runCurrent()

        assertEquals(ApiCredentialsId("88"), savedAsset?.credentialsId)
        assertEquals("Default Preset", savedConfig?.displayName)
        assertEquals(ApiCredentialsId("88"), viewModel.uiState.value.apiProviderEditor.assetDraft?.credentialsId)
        coVerify(exactly = 1) {
            saveApiModelConfigurationUseCase(
                match {
                    it.apiCredentialsId == ApiCredentialsId("88") &&
                        it.displayName == "Default Preset"
                }
            )
        }
    }

    @Test
    fun `save api credentials auto-links duplicate provider and emits snackbar`() = runTest {
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = ApiCredentialsId("88"),
                displayName = "Existing xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                credentialAlias = "existing-xai"
            ),
            configurations = emptyList()
        )
        val persistedAssets = MutableStateFlow(listOf(persistedAsset))
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)

        viewModel.onStartCreateApiModelAsset()
        viewModel.onApiModelAssetFieldChange(
            ApiModelAssetUi(
                credentialsId = ApiCredentialsId(""),
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

        coEvery {
            apiModelRepository.findMatchingCredentials(
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                apiKey = "xai-key",
                sourceCredentialAlias = null,
            )
        } returns persistedAsset.credentials
        coEvery { saveApiModelConfigurationUseCase(any()) } coAnswers {
            val configuration = invocation.args[0] as ApiModelConfiguration
            persistedAssets.value = listOf(
                persistedAsset.copy(
                    configurations = listOf(
                        configuration.copy(
                            id = ApiModelConfigurationId("901"),
                            apiCredentialsId = persistedAsset.credentials.id,
                        )
                    )
                )
            )
            Result.success(ApiModelConfigurationId("901"))
        }

        val snackbarMessages = mutableListOf<String>()
        val snackbarJob = launch {
            viewModel.snackbarMessages.collect { snackbarMessages += it }
        }
        runCurrent()

        var savedAsset: ApiModelAssetUi? = null
        var savedConfig: ApiModelConfigUi? = null
        viewModel.onSaveApiCredentials { assetUi, configUi ->
            savedAsset = assetUi
            savedConfig = configUi
        }
        runCurrent()

        assertEquals(ApiCredentialsId("88"), savedAsset?.credentialsId)
        assertEquals("Default Preset", savedConfig?.displayName)
        assertEquals(
            listOf("Automatically linked to \"Existing xAI\" API Model"),
            snackbarMessages,
        )
        coVerify(exactly = 0) {
            saveApiCredentialsUseCase(any(), any(), any())
        }

        snackbarJob.cancel()
    }

    @Test
    fun `save api model config does not navigate on failure`() = runTest {
        val asset = ApiModelAssetUi(
            credentialsId = ApiCredentialsId("42"),
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
                id = ApiModelConfigurationId("9"),
                credentialsId = ApiCredentialsId("42"),
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
                credentialsId = ApiCredentialsId(""),
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

        coEvery { saveApiCredentialsUseCase(any(), any(), any()) } returns ApiCredentialsId("88")
        coEvery { saveApiModelConfigurationUseCase(any()) } returns Result.success(ApiModelConfigurationId("901"))

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
            credentialsId = ApiCredentialsId("11"),
            displayName = "Provider A",
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-reasoning",
            baseUrl = ApiProvider.XAI.defaultBaseUrl(),
            isVision = false,
            credentialAlias = "provider-a",
            configurations = emptyList()
        )
        val providerB = providerA.copy(
            credentialsId = ApiCredentialsId("22"),
            displayName = "Provider B",
            credentialAlias = "provider-b"
        )
        val viewModel = createViewModel()

        viewModel.onSelectApiModelAsset(providerA)
        viewModel.onSelectApiModelConfig(
            ApiModelConfigUi(
                credentialsId = ApiCredentialsId("11"),
                displayName = "New Preset"
            )
        )
        viewModel.onApiModelAssetFieldChange(providerB)

        coEvery { saveApiModelConfigurationUseCase(any()) } returns Result.success(ApiModelConfigurationId("44"))

        viewModel.onSaveApiModelConfig {}
        runCurrent()

        coVerify {
            saveApiModelConfigurationUseCase(
                match { it.apiCredentialsId == ApiCredentialsId("11") }
            )
        }
    }

    @Test
    fun `canceling byok editor discards unsaved draft edits and preserves persisted list`() = runTest {
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = ApiCredentialsId("42"),
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

        val selectedAsset = viewModel.uiState.value.apiProvidersSheet.assets.single()
        viewModel.onSelectApiModelAsset(selectedAsset)
        viewModel.onApiModelAssetFieldChange(
            selectedAsset.copy(
                displayName = "Unsaved Draft Name",
                modelId = "grok-4.1"
            )
        )
        runCurrent()

        assertEquals("Unsaved Draft Name", viewModel.uiState.value.apiProviderEditor.assetDraft?.displayName)
        assertEquals("Persisted xAI", viewModel.uiState.value.apiProvidersSheet.assets.single().displayName)

        viewModel.onBackToByokList()
        runCurrent()

        assertNull(viewModel.uiState.value.apiProviderEditor.assetDraft)
        assertNull(viewModel.uiState.value.apiProvidersSheet.selectedAsset)
        assertNull(viewModel.uiState.value.apiProviderEditor.presetDraft)
        assertEquals("Persisted xAI", viewModel.uiState.value.apiProvidersSheet.assets.single().displayName)
    }

    @Test
    fun `room emission does not splice over unsaved api credential draft`() = runTest {
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = ApiCredentialsId("51"),
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

        val selectedAsset = viewModel.uiState.value.apiProvidersSheet.assets.single()
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

        assertEquals("Unsaved Draft Name", viewModel.uiState.value.apiProviderEditor.assetDraft?.displayName)
        assertEquals("Room Refreshed Name", viewModel.uiState.value.apiProvidersSheet.assets.single().displayName)
    }

    @Test
    fun `custom header edits stay in draft and cleanup trims blank rows only from draft`() = runTest {
        val persistedAsset = ApiModelAsset(
            credentials = ApiCredentials(
                id = ApiCredentialsId("64"),
                displayName = "Persisted xAI",
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
                baseUrl = ApiProvider.XAI.defaultBaseUrl(),
                credentialAlias = "persisted-xai"
            ),
            configurations = listOf(
                ApiModelConfiguration(
                    id = ApiModelConfigurationId("9"),
                    apiCredentialsId = ApiCredentialsId("64"),
                    displayName = "Default Preset",
                    customHeaders = mapOf("Authorization" to "Bearer persisted")
                )
            )
        )
        val persistedAssets = MutableStateFlow(listOf(persistedAsset))
        val viewModel = createViewModel(apiAssetsFlow = persistedAssets)

        val selectedAsset = viewModel.uiState.value.apiProvidersSheet.assets.single()
        val selectedConfig = selectedAsset.configurations.single()
        viewModel.onSelectApiModelAsset(selectedAsset)
        viewModel.onSelectApiModelConfig(selectedConfig)
        viewModel.onAddCustomHeader()
        viewModel.onCustomHeaderChange(0, CustomHeaderUi("", ""))
        viewModel.onCustomHeaderChange(1, CustomHeaderUi("X-Test", "123"))
        runCurrent()

        assertEquals(
            listOf(CustomHeaderUi("", ""), CustomHeaderUi("X-Test", "123")),
            viewModel.uiState.value.apiProviderEditor.presetDraft?.customHeaders
        )
        assertEquals(
            listOf(CustomHeaderUi("Authorization", "Bearer persisted")),
            viewModel.uiState.value.apiProvidersSheet.assets.single().configurations.single().customHeaders
        )

        viewModel.onCleanupCustomHeaders()
        runCurrent()

        assertEquals(
            listOf(CustomHeaderUi("X-Test", "123")),
            viewModel.uiState.value.apiProviderEditor.presetDraft?.customHeaders
        )
        assertEquals(
            listOf(CustomHeaderUi("Authorization", "Bearer persisted")),
            viewModel.uiState.value.apiProvidersSheet.assets.single().configurations.single().customHeaders
        )
    }

    @Test
    fun `start configure search skill seeds editor from persisted settings`() = runTest {
        val viewModel = createViewModel(
            settingsFlow = flowOf(
                SettingsData(
                    searchEnabled = true,
                    tavilyKeyPresent = true,
                )
            )
        )

        viewModel.onStartConfigureSearchSkill()
        runCurrent()

        assertTrue(viewModel.uiState.value.searchSkillEditor.isEditing)
        assertTrue(viewModel.uiState.value.searchSkillEditor.enabled)
        assertTrue(viewModel.uiState.value.searchSkillEditor.tavilyKeyPresent)
    }

    @Test
    fun `closing byok sheet after starting search configuration preserves search editor mode`() = runTest {
        val viewModel = createViewModel(
            settingsFlow = flowOf(
                SettingsData(
                    searchEnabled = true,
                    tavilyKeyPresent = true,
                )
            )
        )

        viewModel.onShowByokSheet(true)
        viewModel.onStartConfigureSearchSkill()
        viewModel.onShowByokSheet(false)
        runCurrent()

        assertTrue(viewModel.uiState.value.searchSkillEditor.isEditing)
        assertTrue(viewModel.uiState.value.searchSkillEditor.enabled)
        assertTrue(viewModel.uiState.value.searchSkillEditor.tavilyKeyPresent)
    }

    @Test
    fun `save search skill settings persists toggle and Tavily key`() = runTest {
        val viewModel = createViewModel()

        viewModel.onStartConfigureSearchSkill()
        viewModel.onSearchEnabledChange(true)
        viewModel.onTavilyApiKeyChange("tavily-secret")
        viewModel.onSaveSearchSkillSettings(onSuccess = {})
        runCurrent()

        coVerify { updateSearchEnabledUseCase(true) }
        coVerify { saveTavilyApiKeyUseCase("tavily-secret") }
        assertEquals("", viewModel.currentTavilyApiKey.value)
        assertFalse(viewModel.uiState.value.searchSkillEditor.isEditing)
    }

    @Test
    fun `clear Tavily key delegates to preferences use case`() = runTest {
        val viewModel = createViewModel(
            settingsFlow = flowOf(SettingsData(tavilyKeyPresent = true))
        )

        viewModel.onStartConfigureSearchSkill()
        viewModel.onClearTavilyApiKey()
        runCurrent()

        coVerify { clearTavilyApiKeyUseCase() }
        assertEquals("", viewModel.currentTavilyApiKey.value)
    }

    @Test
    fun `save search skill settings rejects enabling search without a Tavily key`() = runTest {
        val viewModel = createViewModel(
            settingsFlow = flowOf(SettingsData(searchEnabled = false, tavilyKeyPresent = false))
        )

        viewModel.onStartConfigureSearchSkill()
        viewModel.onSearchEnabledChange(true)
        viewModel.onSaveSearchSkillSettings(onSuccess = {})
        runCurrent()

        coVerify(exactly = 0) { updateSearchEnabledUseCase(any()) }
        coVerify(exactly = 0) { saveTavilyApiKeyUseCase(any()) }
    }

    private fun createViewModel(
        apiAssets: List<ApiModelAsset> = emptyList(),
        apiAssetsFlow: Flow<List<ApiModelAsset>> = flowOf(apiAssets),
        settingsFlow: Flow<SettingsData> = flowOf(SettingsData()),
    ): SettingsViewModel {
        every { getSettingsUseCase() } returns settingsFlow
        every { getLocalModelAssetsUseCase() } returns flowOf(emptyList())
        coEvery { getLocalModelAssetsUseCase.getSoftDeletedModels() } returns emptyList()
        every { getApiModelAssetsUseCase() } returns apiAssetsFlow
        every { getDefaultModelsUseCase() } returns flowOf(emptyList())

        val applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase()
        val localModelAssetUiMapper = LocalModelAssetUiMapper()
        val apiModelAssetUiMapper = ApiModelAssetUiMapper()
        val settingsUseCases = createSettingsUseCases(applyApiModelMetadataDefaultsUseCase)

        coEvery {
            apiModelRepository.findMatchingCredentials(
                provider = any(),
                modelId = any(),
                baseUrl = any(),
                apiKey = any(),
                sourceCredentialAlias = any(),
            )
        } returns null

        return SettingsViewModel(
            settingsUseCases = settingsUseCases,
            settingsUiStateFactory = SettingsUiStateFactory(
                localModelAssetUiMapper = localModelAssetUiMapper,
                apiModelAssetUiMapper = apiModelAssetUiMapper,
                apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
                applyApiModelMetadataDefaultsUseCase = applyApiModelMetadataDefaultsUseCase,
            ),
            localModelAssetUiMapper = localModelAssetUiMapper,
            apiModelAssetUiMapper = apiModelAssetUiMapper,
            reassignmentOptionUiMapper = ReassignmentOptionUiMapper(),
            errorHandler = errorHandler
        )
    }

    private fun createSettingsUseCases(
        applyApiModelMetadataDefaultsUseCase: ApplyApiModelMetadataDefaultsUseCase,
    ): SettingsUseCases {
        return SettingsUseCasesImpl(
            preferences = SettingsPreferencesUseCasesImpl(
                getSettings = getSettingsUseCase,
                updateTheme = updateThemeUseCase,
                updateHapticPress = updateHapticPressUseCase,
                updateHapticResponse = updateHapticResponseUseCase,
                updateCustomizationEnabled = updateCustomizationEnabledUseCase,
                updateSelectedPromptOption = updateSelectedPromptOptionUseCase,
                updateCustomPromptText = updateCustomPromptTextUseCase,
                updateAllowMemories = updateAllowMemoriesUseCase,
                updateSearchEnabled = updateSearchEnabledUseCase,
                saveTavilyApiKey = saveTavilyApiKeyUseCase,
                clearTavilyApiKey = clearTavilyApiKeyUseCase,
            ),
            localModels = SettingsLocalModelUseCasesImpl(
                getLocalModelAssets = getLocalModelAssetsUseCase,
                getRestorableLocalModels = GetRestorableLocalModelsUseCase(getLocalModelAssetsUseCase),
                saveLocalModelPreset = SaveLocalModelPresetUseCase(saveLocalModelConfigurationUseCase),
            ),
            apiProviders = SettingsApiProviderUseCasesImpl(
                getApiModelAssets = getApiModelAssetsUseCase,
                saveApiProviderDraft = SaveApiProviderDraftUseCase(
                    saveApiCredentialsUseCase = saveApiCredentialsUseCase,
                    saveApiModelConfigurationUseCase = saveApiModelConfigurationUseCase,
                    getApiModelAssetsUseCase = getApiModelAssetsUseCase,
                    apiModelRepository = apiModelRepository,
                ),
                saveApiPreset = SaveApiPresetUseCase(saveApiModelConfigurationUseCase),
                discoverApiModels = DiscoverApiModelsUseCase(
                    fetchApiProviderModelsUseCase = fetchApiProviderModelsUseCase,
                    fetchApiProviderModelDetailUseCase = fetchApiProviderModelDetailUseCase,
                ),
                applyApiModelMetadataDefaults = applyApiModelMetadataDefaultsUseCase,
            ),
            assignments = SettingsAssignmentUseCasesImpl(
                getDefaultModels = getDefaultModelsUseCase,
                setDefaultModel = setDefaultModelUseCase,
                resolveAssignedModelSelection = ResolveAssignedModelSelectionUseCase(
                    getDefaultModelsUseCase = getDefaultModelsUseCase,
                    getLocalModelAssetsUseCase = getLocalModelAssetsUseCase,
                    getApiModelAssetsUseCase = getApiModelAssetsUseCase,
                ),
            ),
            deletion = SettingsDeletionUseCasesImpl(
                prepareModelDeletion = PrepareModelDeletionUseCase(
                    getDefaultModelsUseCase = getDefaultModelsUseCase,
                    getLocalModelAssetsUseCase = getLocalModelAssetsUseCase,
                    getApiModelAssetsUseCase = getApiModelAssetsUseCase,
                    deleteLocalModelUseCase = deleteLocalModelUseCase,
                    deleteApiCredentialsUseCase = deleteApiCredentialsUseCase,
                    deleteApiModelConfigurationUseCase = deleteApiModelConfigurationUseCase,
                ),
                executeModelDeletionWithReassignment = ExecuteModelDeletionWithReassignmentUseCase(
                    deleteLocalModelUseCase = deleteLocalModelUseCase,
                    deleteLocalModelConfigurationUseCase = deleteLocalModelConfigurationUseCase,
                    deleteApiCredentialsUseCase = deleteApiCredentialsUseCase,
                    deleteApiModelConfigurationUseCase = deleteApiModelConfigurationUseCase,
                    setDefaultModelUseCase = setDefaultModelUseCase,
                ),
            ),
        )
    }
}
