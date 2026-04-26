package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.byok.ClearDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ReDownloadModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.*
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
    private val updateBackgroundInferenceEnabledUseCase = mockk<UpdateBackgroundInferenceEnabledUseCase>(relaxed = true)
    private val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
    private val getRestorableLocalModelsUseCase = mockk<GetRestorableLocalModelsUseCase>(relaxed = true)
    private val saveLocalModelPresetUseCase = mockk<SaveLocalModelPresetUseCase>(relaxed = true)
    private val reDownloadModelUseCase = mockk<ReDownloadModelUseCase>(relaxed = true)
    private val getApiModelAssetsUseCase = mockk<GetApiModelAssetsUseCase>()
    private val saveApiProviderDraftUseCase = mockk<SaveApiProviderDraftUseCase>(relaxed = true)
    private val saveApiPresetUseCase = mockk<SaveApiPresetUseCase>(relaxed = true)
    private val discoverApiModelsUseCase = mockk<DiscoverApiModelsUseCase>(relaxed = true)
    private val applyApiModelMetadataDefaultsUseCase = mockk<ApplyApiModelMetadataDefaultsUseCase>(relaxed = true)
    private val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
    private val setDefaultModelUseCase = mockk<SetDefaultModelUseCase>(relaxed = true)
    private val resolveAssignedModelSelectionUseCase = mockk<ResolveAssignedModelSelectionUseCase>(relaxed = true)
    private val clearDefaultModelUseCase = mockk<ClearDefaultModelUseCase>(relaxed = true)
    private val prepareModelDeletionUseCase = mockk<PrepareModelDeletionUseCase>(relaxed = true)
    private val executeModelDeletionWithReassignmentUseCase = mockk<ExecuteModelDeletionWithReassignmentUseCase>(relaxed = true)

    private val getTtsProvidersUseCase = mockk<GetTtsProvidersUseCase>()
    private val saveTtsProviderUseCase = mockk<SaveTtsProviderUseCase>(relaxed = true)
    private val deleteTtsProviderUseCase = mockk<DeleteTtsProviderUseCase>(relaxed = true)

    private val localModelAssetUiMapper = LocalModelAssetUiMapper()
    private val apiModelAssetUiMapper = ApiModelAssetUiMapper()
    private val ttsProviderAssetUiMapper = TtsProviderAssetUiMapper()
    private val reassignmentOptionUiMapper = ReassignmentOptionUiMapper()
    private val errorHandler = mockk<ViewModelErrorHandler>()

    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        val settingsFlow = MutableStateFlow(SettingsData())
        every { getSettingsUseCase() } returns settingsFlow
        every { getLocalModelAssetsUseCase() } returns MutableStateFlow(emptyList())
        every { getApiModelAssetsUseCase() } returns MutableStateFlow(emptyList())
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
        every { getTtsProvidersUseCase() } returns MutableStateFlow(emptyList())
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }

        val settingsUseCases = SettingsUseCasesImpl(
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
                updateBackgroundInferenceEnabled = updateBackgroundInferenceEnabledUseCase,
            ),
            localModels = SettingsLocalModelUseCasesImpl(
                getLocalModelAssets = getLocalModelAssetsUseCase,
                getRestorableLocalModels = getRestorableLocalModelsUseCase,
                saveLocalModelPreset = saveLocalModelPresetUseCase,
                reDownloadModel = reDownloadModelUseCase,
            ),
            apiProviders = SettingsApiProviderUseCasesImpl(
                getApiModelAssets = getApiModelAssetsUseCase,
                saveApiProviderDraft = saveApiProviderDraftUseCase,
                saveApiPreset = saveApiPresetUseCase,
                discoverApiModels = discoverApiModelsUseCase,
                applyApiModelMetadataDefaults = applyApiModelMetadataDefaultsUseCase,
            ),
            assignments = SettingsAssignmentUseCasesImpl(
                getDefaultModels = getDefaultModelsUseCase,
                setDefaultModel = setDefaultModelUseCase,
                clearDefaultModel = clearDefaultModelUseCase,
                resolveAssignedModelSelection = resolveAssignedModelSelectionUseCase,
            ),
            deletion = SettingsDeletionUseCasesImpl(
                prepareModelDeletion = prepareModelDeletionUseCase,
                executeModelDeletionWithReassignment = executeModelDeletionWithReassignmentUseCase,
            ),
            tts = SettingsTtsUseCasesImpl(
                getTtsProviders = getTtsProvidersUseCase,
                saveTtsProvider = saveTtsProviderUseCase,
                deleteTtsProvider = deleteTtsProviderUseCase,
            )
        )

        viewModel = SettingsViewModel(
            settingsUseCases = settingsUseCases,
            settingsUiStateFactory = SettingsUiStateFactory(
                localModelAssetUiMapper,
                apiModelAssetUiMapper,
                ttsProviderAssetUiMapper,
                ApiDiscoveryUiFilter(),
                applyApiModelMetadataDefaultsUseCase
            ),
            localModelAssetUiMapper = localModelAssetUiMapper,
            apiModelAssetUiMapper = apiModelAssetUiMapper,
            ttsProviderAssetUiMapper = ttsProviderAssetUiMapper,
            reassignmentOptionUiMapper = reassignmentOptionUiMapper,
            errorHandler = errorHandler,
        )
    }

    @Test
    fun `initial state is correct`() = runTest {
        assertEquals(AppTheme.SYSTEM, viewModel.uiState.value.home.theme)
        assertTrue(viewModel.uiState.value.home.hapticPress)
        assertTrue(viewModel.uiState.value.home.hapticResponse)
    }

    @Test
    fun `model assignments include unassigned TTS slot`() = runTest {
        val ttsAssignment = viewModel.uiState.value.assignments.assignments
            .single { it.modelType == ModelType.TTS }

        assertEquals("Text-to-Speech", ttsAssignment.displayLabel)
        assertEquals("None Assigned", ttsAssignment.currentModelName)
    }

    @Test
    fun `onFetchTtsModels discovers models from TTS draft`() = runTest {
        coEvery { discoverApiModelsUseCase(any()) } returns ApiModelDiscoveryResult(
            models = listOf(
                DiscoveredApiModel(id = "gemini-2.5-flash-preview-tts"),
                DiscoveredApiModel(id = "gemini-2.5-flash"),
                DiscoveredApiModel(id = "gemini-3-pro-preview"),
            ),
            scope = ApiModelDiscoveryScope(
                provider = ApiProvider.GOOGLE,
                baseUrl = "",
                credentialAlias = null,
            ),
        )

        viewModel.onStartCreateTtsProviderAsset()
        viewModel.onTtsAssetFieldChange(
            TtsProviderAssetUi(
                displayName = "Google TTS",
                provider = ApiProvider.GOOGLE,
                voiceName = "Puck",
            )
        )
        viewModel.onApiKeyChange("google-key")

        viewModel.onFetchTtsModels()
        advanceUntilIdle()

        coVerify {
            discoverApiModelsUseCase(
                match { request ->
                    request.provider == ApiProvider.GOOGLE &&
                        request.currentApiKey == "google-key" &&
                        request.credentialAlias == null &&
                        request.selectedModelId == null
                }
            )
        }
        assertEquals(
            listOf("gemini-2.5-flash-preview-tts"),
            viewModel.uiState.value.apiProviderEditor.discovery.models.map { it.modelId },
        )
    }

    @Test
    fun `onSaveTtsProvider sets default model when useAsDefault is true`() = runTest {
        val ttsId = com.browntowndev.pocketcrew.domain.model.config.TtsProviderId("new-tts-id")
        coEvery { saveTtsProviderUseCase(any()) } returns Result.success(ttsId)

        viewModel.onStartCreateTtsProviderAsset()
        viewModel.onTtsAssetFieldChange(
            TtsProviderAssetUi(
                displayName = "Google TTS",
                provider = ApiProvider.GOOGLE,
                voiceName = "Puck",
                useAsDefault = true
            )
        )

        viewModel.onSaveTtsProvider(onSuccess = {})
        advanceUntilIdle()

        coVerify {
            saveTtsProviderUseCase(match { it.displayName == "Google TTS" })
            setDefaultModelUseCase(
                modelType = ModelType.TTS,
                localConfigId = null,
                apiConfigId = null,
                ttsProviderId = ttsId
            )
        }
    }

    @Test
    fun `onTtsAssetFieldChange sets default immediately for existing provider`() = runTest {
        val ttsId = com.browntowndev.pocketcrew.domain.model.config.TtsProviderId("existing-tts-id")
        val existingAsset = TtsProviderAssetUi(
            id = ttsId,
            displayName = "Existing TTS",
            useAsDefault = false
        )

        viewModel.onSelectTtsProviderAsset(existingAsset)
        
        viewModel.onTtsAssetFieldChange(existingAsset.copy(useAsDefault = true))
        advanceUntilIdle()

        coVerify {
            setDefaultModelUseCase(
                modelType = ModelType.TTS,
                localConfigId = null,
                apiConfigId = null,
                ttsProviderId = ttsId
            )
        }
    }

    @Test
    fun `onTtsAssetFieldChange clears default immediately for existing provider`() = runTest {
        val ttsId = com.browntowndev.pocketcrew.domain.model.config.TtsProviderId("existing-tts-id")
        val existingAsset = TtsProviderAssetUi(
            id = ttsId,
            displayName = "Existing TTS",
            useAsDefault = true
        )

        viewModel.onSelectTtsProviderAsset(existingAsset)
        
        viewModel.onTtsAssetFieldChange(existingAsset.copy(useAsDefault = false))
        advanceUntilIdle()

        coVerify {
            clearDefaultModelUseCase(ModelType.TTS)
        }
    }
}
