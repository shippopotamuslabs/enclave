package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.theme.FakeThemePreferencesRepository
import com.browntowndev.enclave.domain.theme.ObserveThemePreferencesUseCase
import com.browntowndev.enclave.domain.theme.SetThemeFamilyUseCase

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.auth.LogoutUseCase
import com.browntowndev.enclave.domain.usecase.byok.ClearDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.media.DeleteMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.download.ModelPackMember
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetModelPacksForSettingsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ModelPackSettingsRow
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.settings.*
import com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCasesImpl
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveUsageSpendSummaryUseCase
import org.junit.jupiter.api.DisplayName
import com.browntowndev.enclave.feature.settings.usage.UsageSpendUiMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val getSettingsUseCase = mockk<GetSettingsUseCase>()
    private val updateThemeUseCase = mockk<UpdateThemeUseCase>(relaxed = true)
    private val updateHapticResponseUseCase = mockk<UpdateHapticResponseUseCase>(relaxed = true)
    private val updateCustomizationEnabledUseCase = mockk<UpdateCustomizationEnabledUseCase>(relaxed = true)
    private val updateSelectedPromptOptionUseCase = mockk<UpdateSelectedPromptOptionUseCase>(relaxed = true)
    private val updateCustomPromptTextUseCase = mockk<UpdateCustomPromptTextUseCase>(relaxed = true)
    private val updateAllowMemoriesUseCase = mockk<UpdateAllowMemoriesUseCase>(relaxed = true)
    private val updateSearchEnabledUseCase = mockk<UpdateSearchEnabledUseCase>(relaxed = true)
    private val updateXSearchEnabledUseCase = mockk<UpdateXSearchEnabledUseCase>(relaxed = true)
    private val saveTavilyApiKeyUseCase = mockk<SaveTavilyApiKeyUseCase>(relaxed = true)
    private val clearTavilyApiKeyUseCase = mockk<ClearTavilyApiKeyUseCase>(relaxed = true)
    private val saveXApiKeyUseCase = mockk<SaveXApiKeyUseCase>(relaxed = true)
    private val clearXApiKeyUseCase = mockk<ClearXApiKeyUseCase>(relaxed = true)
    private val updateBackgroundInferenceEnabledUseCase = mockk<UpdateBackgroundInferenceEnabledUseCase>(relaxed = true)
    private val updateAutomaticErrorTrackingEnabledUseCase = mockk<UpdateAutomaticErrorTrackingEnabledUseCase>(relaxed = true)
    private val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
    private val getRestorableLocalModelsUseCase = mockk<GetRestorableLocalModelsUseCase>(relaxed = true)
    private val saveLocalModelPresetUseCase = mockk<SaveLocalModelPresetUseCase>(relaxed = true)
    private val reDownloadModelUseCase = mockk<ReDownloadModelUseCase>(relaxed = true)
    private val getModelPacksForSettingsUseCase = mockk<GetModelPacksForSettingsUseCase>(relaxed = true)
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

    private val getMediaProvidersUseCase = mockk<GetMediaProvidersUseCase>()
    private val saveMediaProviderUseCase = mockk<SaveMediaProviderUseCase>(relaxed = true)
    private val deleteMediaProviderUseCase = mockk<DeleteMediaProviderUseCase>(relaxed = true)

    private val loginWithGoogleUseCase = mockk<LoginWithGoogleUseCase>(relaxed = true)
    private val logoutUseCase = mockk<LogoutUseCase>(relaxed = true)
    private val getAuthStateUseCase = mockk<GetAuthStateUseCase>(relaxed = true)

    private val memoriesRepository = mockk<MemoriesRepository>()

    private val localModelAssetUiMapper = LocalModelAssetUiMapper()
    private val apiModelAssetUiMapper = ApiModelAssetUiMapper()
    private val ttsProviderAssetUiMapper = TtsProviderAssetUiMapper()
    private val mediaProviderAssetUiMapper = MediaProviderAssetUiMapper()
    private val reassignmentOptionUiMapper = ReassignmentOptionUiMapper()
    private val errorHandler = mockk<ViewModelErrorHandler>()

    private val localModelAssetsStateFlow = MutableStateFlow<List<LocalModelAsset>>(emptyList())

    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        val settingsFlow = MutableStateFlow(SettingsData())
        every { getSettingsUseCase() } returns settingsFlow
        every { getLocalModelAssetsUseCase() } returns localModelAssetsStateFlow
        val apiAssets =
            MockSettingsData.apiModels.map { uiAsset ->
                com.browntowndev.enclave.domain.model.config.ApiModelAsset(
                    credentials =
                        com.browntowndev.enclave.domain.model.config.ApiCredentials(
                            id = uiAsset.credentialsId,
                            displayName = uiAsset.displayName,
                            provider = uiAsset.provider,
                            baseUrl = uiAsset.baseUrl,
                            credentialAlias = uiAsset.credentialAlias,
                        ),
                    configurations =
                        uiAsset.configurations.map { config ->
                            com.browntowndev.enclave.domain.model.config.ApiModelConfiguration(
                                id = config.id,
                                apiCredentialsId = config.credentialsId,
                                modelId = config.modelId,
                                isMultimodal = config.isMultimodal,
                                displayName = config.displayName,
                            )
                        },
                )
            }
        every { getApiModelAssetsUseCase() } returns MutableStateFlow(apiAssets)
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
        every { getTtsProvidersUseCase() } returns MutableStateFlow(emptyList())
        every { getMediaProvidersUseCase() } returns MutableStateFlow(emptyList())
        every { memoriesRepository.getAllMemoriesFlow() } returns MutableStateFlow(emptyList())
        every { getAuthStateUseCase() } returns MutableStateFlow(com.browntowndev.enclave.domain.port.auth.AuthState.Loading)
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }

        val updateSearchProviderUseCase = mockk<UpdateSearchProviderUseCase>()
        val saveExaApiKeyUseCase = mockk<SaveExaApiKeyUseCase>()
        val clearExaApiKeyUseCase = mockk<ClearExaApiKeyUseCase>()
        val updateShowSearchProviderSwitchAlertUseCase = mockk<UpdateShowSearchProviderSwitchAlertUseCase>()

        val observeActiveUtilityModelsUseCase =
            mockk<com.browntowndev.enclave.domain.usecase.modelconfig.ObserveActiveUtilityModelsUseCase>()
        every { observeActiveUtilityModelsUseCase() } returns
            kotlinx.coroutines.flow.flowOf(
                emptyList<com.browntowndev.enclave.domain.model.config.UtilityModelMetadata>(),
            )

        val dataControlsUseCases = mockk<SettingsDataControlsUseCases>(relaxed = true)

        val settingsUseCases =
            SettingsUseCasesImpl(
                preferences =
                    SettingsPreferencesUseCasesImpl(
                        getSettings = getSettingsUseCase,
                        updateTheme = updateThemeUseCase,
                        updateHapticResponse = updateHapticResponseUseCase,
                        updateCustomizationEnabled = updateCustomizationEnabledUseCase,
                        updateSelectedPromptOption = updateSelectedPromptOptionUseCase,
                        updateCustomPromptText = updateCustomPromptTextUseCase,
                        updateAllowMemories = updateAllowMemoriesUseCase,
                        updateSearchEnabled = updateSearchEnabledUseCase,
                        updateSearchProvider = updateSearchProviderUseCase,
                        saveTavilyApiKey = saveTavilyApiKeyUseCase,
                        clearTavilyApiKey = clearTavilyApiKeyUseCase,
                        saveExaApiKey = saveExaApiKeyUseCase,
                        clearExaApiKey = clearExaApiKeyUseCase,
                        updateXSearchEnabled = updateXSearchEnabledUseCase,
                        saveXApiKey = saveXApiKeyUseCase,
                        clearXApiKey = clearXApiKeyUseCase,
                        updateShowSearchProviderSwitchAlert = updateShowSearchProviderSwitchAlertUseCase,
                        updateBackgroundInferenceEnabled = updateBackgroundInferenceEnabledUseCase,
                        updateAutomaticErrorTrackingEnabled = updateAutomaticErrorTrackingEnabledUseCase,
                    ),
                localModels =
                    SettingsLocalModelUseCasesImpl(
                        getLocalModelAssets = getLocalModelAssetsUseCase,
                        getRestorableLocalModels = getRestorableLocalModelsUseCase,
                        saveLocalModelPreset = saveLocalModelPresetUseCase,
                        reDownloadModel = reDownloadModelUseCase,
                        getModelPacksForSettings = getModelPacksForSettingsUseCase,
                        observeActiveUtilityModels = observeActiveUtilityModelsUseCase,
                        reDownloadModelPack = mockk(relaxed = true),
                        deleteModelPack = mockk(relaxed = true),
                    ),
                apiProviders =
                    SettingsApiProviderUseCasesImpl(
                        getApiModelAssets = getApiModelAssetsUseCase,
                        saveApiProviderDraft = saveApiProviderDraftUseCase,
                        saveApiPreset = saveApiPresetUseCase,
                        discoverApiModels = discoverApiModelsUseCase,
                        applyApiModelMetadataDefaults = applyApiModelMetadataDefaultsUseCase,
                        testApiProviderConnection = mockk(relaxed = true),
                        fetchOpenRouterModelEndpoints = mockk(relaxed = true),
                    ),
                assignments =
                    SettingsAssignmentUseCasesImpl(
                        getDefaultModels = getDefaultModelsUseCase,
                        setDefaultModel = setDefaultModelUseCase,
                        clearDefaultModel = clearDefaultModelUseCase,
                        resolveAssignedModelSelection = resolveAssignedModelSelectionUseCase,
                    ),
                deletion =
                    SettingsDeletionUseCasesImpl(
                        prepareModelDeletion = prepareModelDeletionUseCase,
                        executeModelDeletionWithReassignment = executeModelDeletionWithReassignmentUseCase,
                    ),
                tts =
                    SettingsTtsUseCasesImpl(
                        getTtsProviders = getTtsProvidersUseCase,
                        saveTtsProvider = saveTtsProviderUseCase,
                        deleteTtsProvider = deleteTtsProviderUseCase,
                    ),
                media =
                    SettingsMediaUseCasesImpl(
                        getMediaProviders = getMediaProvidersUseCase,
                        saveMediaProvider = saveMediaProviderUseCase,
                        deleteMediaProvider = deleteMediaProviderUseCase,
                    ),
                auth =
                    SettingsAuthUseCasesImpl(
                        loginWithGoogle = loginWithGoogleUseCase,
                        logout = logoutUseCase,
                        getAuthState = getAuthStateUseCase,
                    ),
                memories = SettingsMemoriesUseCasesImpl(memoriesRepository),
                dataControls = dataControlsUseCases,
            )

        val embeddingProgressPort = mockk<com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort>()
        every { embeddingProgressPort.isReindexing } returns MutableStateFlow(false)
        every { embeddingProgressPort.progress } returns MutableStateFlow(0f)


        val detectVaultReconciliationStateUseCase =
            mockk<com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase>(relaxed = true)
        val adoptCloudVaultUseCase =
            mockk<com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase>(relaxed = true)
        val bootstrapCloudVaultFromLocalUseCase =
            mockk<com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase>(relaxed = true)
        val createVaultUseCase = mockk<CreateVaultUseCase>(relaxed = true)
        val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
        val vaultMetadataPort = mockk<VaultMetadataPort>(relaxed = true)
        val cloudSyncPort =
            mockk<com.browntowndev.enclave.domain.port.repository.CloudSyncPort>(relaxed = true)
        val generalDelegate =
            SettingsGeneralDelegate(
                settingsUseCases.preferences,
                settingsUseCases.auth,
                errorHandler,
                detectVaultReconciliationStateUseCase,
                adoptCloudVaultUseCase,
                bootstrapCloudVaultFromLocalUseCase,
                createVaultUseCase,
                mockk<com.browntowndev.enclave.domain.usecase.security.UnlockVaultFromStoredMetadataUseCase>(relaxed = true),
                mockk<com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase>(relaxed = true),
                cryptoManager,
                vaultMetadataPort,
                cloudSyncPort,
            )
        val memoriesDelegate = SettingsMemoriesDelegate(settingsUseCases.preferences, settingsUseCases.memories, errorHandler)
        val dataControlsDelegate = SettingsDataControlsDelegate(settingsUseCases.dataControls, settingsUseCases.preferences, errorHandler)
        val deletionHelper = SettingsDeletionHelper(settingsUseCases.deletion, reassignmentOptionUiMapper, errorHandler)
        val localModelsDelegate =
            SettingsLocalModelsDelegate(settingsUseCases.localModels, deletionHelper, errorHandler)
        val apiProvidersDelegate =
            createSettingsApiProvidersDelegate(
                apiProviderUseCases = settingsUseCases.apiProviders,
                ttsUseCases = settingsUseCases.tts,
                liveVoiceUseCases = settingsUseCases.liveVoice,
                mediaUseCases = settingsUseCases.media,
                deletionHelper = deletionHelper,
                apiModelAssetUiMapper = apiModelAssetUiMapper,
                errorHandler = errorHandler,
            )
        val searchDelegate =
            SettingsSearchDelegate(
                settingsUseCases.preferences,
                mockk<com.browntowndev.enclave.domain.port.repository.CloudSyncPort>(relaxed = true),
                errorHandler,
            )
        val assignmentsDelegate = SettingsAssignmentsDelegate(settingsUseCases.assignments, errorHandler)

        viewModel =
            SettingsViewModel(
                settingsUseCases = settingsUseCases,
                settingsUiStateFactory =
                    buildSettingsUiStateFactory(
                        localModelAssetUiMapper,
                        apiModelAssetUiMapper,
                        ttsProviderAssetUiMapper,
                        mediaProviderAssetUiMapper,
                        ApiDiscoveryUiFilter(),
                        applyApiModelMetadataDefaultsUseCase,
                        UsageSpendUiMapper(),
                    ),
                observeUsageSpendSummaryUseCase = testUsageSpendSummaryUseCase(),
                generalDelegate = generalDelegate,
                memoriesDelegate = memoriesDelegate,
                dataControlsDelegate = dataControlsDelegate,
                localModelsDelegate = localModelsDelegate,
                apiProvidersDelegate = apiProvidersDelegate,
                searchDelegate = searchDelegate,
                assignmentsDelegate = assignmentsDelegate,
                deletionHelper = deletionHelper,
                errorHandler = errorHandler,
                observeEmbeddingProgress = com.browntowndev.enclave.domain.usecase.settings.ObserveEmbeddingProgressUseCase(embeddingProgressPort),
                observeDownloadWorkUseCase = com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase(com.browntowndev.enclave.core.testing.download.FakeDownloadWorkObserverPort()),
                observeThemePreferencesUseCase = ObserveThemePreferencesUseCase(FakeThemePreferencesRepository()),
                setThemeFamilyUseCase = SetThemeFamilyUseCase(FakeThemePreferencesRepository()),
            )
    }

    @Test
    fun `onLoginWithGoogle calls loginWithGoogle`() =
        runTest {
            coEvery { loginWithGoogleUseCase() } returns Result.success(UserProfile("uid", "Name", "email", null))

            viewModel.onLoginWithGoogle()
            advanceUntilIdle()

            coVerify { loginWithGoogleUseCase() }
            assertTrue(viewModel.uiState.value.vaultReauthSheet is VaultReauthSheetState.EnterPassword)
        }

    @Test
    fun `onLogout signs out and clears data`() =
        runTest {
            coEvery { logoutUseCase() } returns Result.success(Unit)

            viewModel.onLogout()
            advanceUntilIdle()

            coVerify { logoutUseCase() }
        }

    @Test
    fun `initial state is correct`() =
        runTest {
            assertEquals(AppTheme.SYSTEM, viewModel.uiState.value.home.theme)
            assertTrue(viewModel.uiState.value.home.hapticResponse)
        }

    @Test
    fun `model assignments include unassigned TTS slot`() =
        runTest {
            val ttsAssignment =
                viewModel.uiState.value.assignments.assignments
                    .single { it.modelType == ModelType.TTS }

            assertEquals("Text-to-Speech", ttsAssignment.displayLabel)
            assertEquals("None Assigned", ttsAssignment.currentModelName)
        }

    @Test
    fun `onFetchTtsModels discovers models from TTS draft`() =
        runTest {
            coEvery { discoverApiModelsUseCase(any()) } returns
                ApiModelDiscoveryResult(
                    models =
                        listOf(
                            DiscoveredApiModel(id = "gemini-2.5-flash-preview-tts"),
                            DiscoveredApiModel(id = "gemini-2.5-flash"),
                            DiscoveredApiModel(id = "gemini-3-pro-preview"),
                        ),
                    scope =
                        ApiModelDiscoveryScope(
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
                ),
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
                    },
                )
            }
            assertEquals(
                listOf("gemini-2.5-flash-preview-tts"),
                viewModel.uiState.value.ttsProviderEditor.discovery.models
                    .map { it.modelId },
            )
        }

    @Test
    fun `onSaveTtsProvider sets default model when useAsDefault is true`() =
        runTest {
            val ttsId =
                com.browntowndev.enclave.domain.model.config
                    .TtsProviderId("new-tts-id")
            coEvery { saveTtsProviderUseCase(any()) } returns Result.success(ttsId)

            viewModel.onStartCreateTtsProviderAsset()
            viewModel.onTtsAssetFieldChange(
                TtsProviderAssetUi(
                    displayName = "Google TTS",
                    provider = ApiProvider.GOOGLE,
                    voiceName = "Puck",
                    useAsDefault = true,
                ),
            )

            viewModel.onSaveTtsProvider(onSuccess = {})
            advanceUntilIdle()

            coVerify {
                saveTtsProviderUseCase(match { it.displayName == "Google TTS" })
                setDefaultModelUseCase(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId,
                )
            }
        }

    @Test
    fun `onTtsAssetFieldChange sets default immediately for existing provider`() =
        runTest {
            val ttsId =
                com.browntowndev.enclave.domain.model.config
                    .TtsProviderId("existing-tts-id")
            val existingAsset =
                TtsProviderAssetUi(
                    id = ttsId,
                    displayName = "Existing TTS",
                    useAsDefault = false,
                )

            viewModel.onSelectTtsProviderAsset(existingAsset)

            viewModel.onTtsAssetFieldChange(existingAsset.copy(useAsDefault = true))
            advanceUntilIdle()

            coVerify {
                setDefaultModelUseCase(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId,
                )
            }
        }

    @Test
    fun `onTtsAssetFieldChange clears default immediately for existing provider`() =
        runTest {
            val ttsId =
                com.browntowndev.enclave.domain.model.config
                    .TtsProviderId("existing-tts-id")
            val existingAsset =
                TtsProviderAssetUi(
                    id = ttsId,
                    displayName = "Existing TTS",
                    useAsDefault = true,
                )

            viewModel.onSelectTtsProviderAsset(existingAsset)

            viewModel.onTtsAssetFieldChange(existingAsset.copy(useAsDefault = false))
            advanceUntilIdle()

            coVerify {
                clearDefaultModelUseCase(ModelType.TTS)
            }
        }

    @Test
    fun `onXSearchEnabledChange updates state`() =
        runTest {
            viewModel.onXSearchEnabledChange(true)
            assertEquals(true, viewModel.uiState.value.xSearchEditor.enabled)
        }

    @Test
    fun `onXApiKeyChange updates current key`() =
        runTest {
            viewModel.onXApiKeyChange("new-x-key")
            assertEquals("new-x-key", viewModel.currentXApiKey.value)
        }

    @Test
    fun `onClearXApiKey calls clear and resets state`() =
        runTest {
            viewModel.onXApiKeyChange("old-key")
            viewModel.onClearXApiKey()
            advanceUntilIdle()

            coVerify { clearXApiKeyUseCase() }
            assertEquals("", viewModel.currentXApiKey.value)
        }

    @Test
    fun `onSaveXSearchSettings saves enabled status and api key`() =
        runTest {
            viewModel.onXSearchEnabledChange(true)
            viewModel.onXApiKeyChange("x-api-key")

            viewModel.onSaveXSearchSettings(onSuccess = {})
            advanceUntilIdle()

            coVerify {
                updateXSearchEnabledUseCase(true)
                saveXApiKeyUseCase("x-api-key")
            }
            assertEquals("", viewModel.currentXApiKey.value)
            assertEquals(false, viewModel.uiState.value.xSearchEditor.enabled)
        }

    @Test
    fun `onSaveMemory inserts new memory when id is empty`() =
        runTest {
            val draft = StoredMemory(id = "", text = "New memory", category = MemoryCategory.PREFERENCES)
            coEvery { memoriesRepository.insertMemory(draft.category, draft.text) } returns mockk()

            viewModel.onAddMemory()
            viewModel.onUpdateMemoryDraft(draft.text, draft.category)
            viewModel.onSaveMemory()
            advanceUntilIdle()

            coVerify { memoriesRepository.insertMemory(draft.category, draft.text) }
        }

    @Test
    fun `onSaveMemory updates existing memory when id is not empty`() =
        runTest {
            val existingMemory = StoredMemory(id = "mem-1", text = "Old memory", category = MemoryCategory.PREFERENCES)
            val updatedDraft = existingMemory.copy(text = "Updated memory")

            coEvery { memoriesRepository.updateMemory(updatedDraft.id, updatedDraft.text, updatedDraft.category) } returns Unit

            viewModel.onEditMemory(existingMemory)
            viewModel.onUpdateMemoryDraft(updatedDraft.text, updatedDraft.category)
            viewModel.onSaveMemory()
            advanceUntilIdle()

            coVerify { memoriesRepository.updateMemory(updatedDraft.id, updatedDraft.text, updatedDraft.category) }
        }

    @Test
    fun `onThemeChange updates preferences`() =
        runTest {
            viewModel.onThemeChange(AppTheme.DARK)
            advanceUntilIdle()
            coVerify { updateThemeUseCase(AppTheme.DARK) }
        }

    @Test
    fun `onHapticResponseChange updates preferences`() =
        runTest {
            viewModel.onHapticResponseChange(false)
            advanceUntilIdle()
            coVerify { updateHapticResponseUseCase(false) }
        }

    @Test
    fun `onBackgroundInferenceChange updates preferences`() =
        runTest {
            viewModel.onBackgroundInferenceChange(true)
            advanceUntilIdle()
            coVerify { updateBackgroundInferenceEnabledUseCase(true) }
        }

    @Test
    fun `onDeleteMemory calls repository`() =
        runTest {
            coEvery { memoriesRepository.deleteMemory("mem-1") } returns Unit
            viewModel.onDeleteMemory("mem-1")
            advanceUntilIdle()
            coVerify { memoriesRepository.deleteMemory("mem-1") }
        }

    @Test
    fun `onSaveLocalModelConfig calls use case`() =
        runTest {
            val asset = MockSettingsData.localModels.first()
            val config = LocalModelConfigUi(displayName = "Config 1")
            coEvery { saveLocalModelPresetUseCase(any(), any()) } returns
                Result.success(
                    com.browntowndev.enclave.domain.model.config
                        .LocalModelConfigurationId("1"),
                )

            viewModel.onSelectLocalModelAsset(asset)
            viewModel.onSelectLocalModelConfig(config)
            viewModel.onSaveLocalModelConfig(onSuccess = {})
            advanceUntilIdle()

            coVerify { saveLocalModelPresetUseCase(asset.metadataId, any()) }
        }

    @Test
    fun `configDraft preserved after sheet dismissed`() =
        runTest {
            val asset = MockSettingsData.localModels.first()
            val config = LocalModelConfigUi(localModelId = asset.metadataId, displayName = "My Preset")

            viewModel.onSelectLocalModelAsset(asset)
            viewModel.onSelectLocalModelConfig(config)
            viewModel.onShowModelConfigSheet(false)
            advanceUntilIdle()

            org.junit.jupiter.api.Assertions
                .assertNotNull(viewModel.uiState.value.localModelEditor.configDraft)
        }

    @Test
    fun `system preset configDraft is preserved after sheet dismissed`() =
        runTest {
            val asset = MockSettingsData.localModels.first()
            val config =
                LocalModelConfigUi(
                    localModelId = asset.metadataId,
                    displayName = "System Default",
                    isSystemPreset = true,
                )

            viewModel.onSelectLocalModelAsset(asset)
            viewModel.onSelectLocalModelConfig(config)
            viewModel.onShowModelConfigSheet(false)
            advanceUntilIdle()

            org.junit.jupiter.api.Assertions.assertTrue(
                viewModel.uiState.value.localModelEditor.configDraft
                    ?.isSystemPreset == true,
            )
        }

    @Test
    fun `config field values preserved after sheet dismissed`() =
        runTest {
            val asset = MockSettingsData.localModels.first()
            val config =
                LocalModelConfigUi(
                    localModelId = asset.metadataId,
                    displayName = "Creative",
                    temperature = 1.5,
                    contextWindow = "8192",
                    topK = "80",
                )

            viewModel.onSelectLocalModelAsset(asset)
            viewModel.onSelectLocalModelConfig(config)
            viewModel.onShowModelConfigSheet(false)
            advanceUntilIdle()

            val draft = viewModel.uiState.value.localModelEditor.configDraft
            org.junit.jupiter.api.Assertions
                .assertEquals("Creative", draft?.displayName)
            org.junit.jupiter.api.Assertions
                .assertEquals(1.5, draft?.temperature)
            org.junit.jupiter.api.Assertions
                .assertEquals("8192", draft?.contextWindow)
        }

    @Test
    fun `onSaveLocalModelConfig works after sheet dismissed`() =
        runTest {
            val asset = MockSettingsData.localModels.first()
            val config =
                LocalModelConfigUi(
                    localModelId = asset.metadataId,
                    displayName = "My Preset",
                    contextWindow = "4096",
                )
            coEvery { saveLocalModelPresetUseCase(any(), any()) } returns
                Result.success(
                    com.browntowndev.enclave.domain.model.config
                        .LocalModelConfigurationId("new-id"),
                )

            viewModel.onSelectLocalModelAsset(asset)
            viewModel.onSelectLocalModelConfig(config)
            viewModel.onShowModelConfigSheet(false)

            viewModel.onSaveLocalModelConfig(onSuccess = {})
            advanceUntilIdle()

            coVerify { saveLocalModelPresetUseCase(asset.metadataId, any()) }
        }

    @Test
    fun `onSaveApiCredentials calls use case and updates state`() =
        runTest {
            val draft =
                MockSettingsData.apiModels.first().copy(
                    credentialsId =
                        com.browntowndev.enclave.domain.model.config
                            .ApiCredentialsId("api-1"),
                    displayName = "OpenAI",
                )
            val persistedAsset =
                com.browntowndev.enclave.domain.model.config.ApiModelAsset(
                    credentials =
                        com.browntowndev.enclave.domain.model.config.ApiCredentials(
                            id = draft.credentialsId,
                            displayName = draft.displayName,
                            provider = draft.provider,
                            baseUrl = draft.baseUrl,
                            credentialAlias = "alias-1",
                        ),
                    configurations = emptyList(),
                )
            coEvery { saveApiProviderDraftUseCase(any()) } returns
                Result.success<com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraftSaveResult>(
                    com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraftSaveResult(
                        persistedAsset = persistedAsset,
                        createdPreset = null,
                    ),
                )

            viewModel.onStartCreateApiModelAsset()
            viewModel.onApiModelAssetFieldChange(draft)
            viewModel.onApiKeyChange("new-key")

            viewModel.onSaveApiCredentials(onSuccess = { _, _ -> })
            advanceUntilIdle()

            coVerify {
                saveApiProviderDraftUseCase(
                    match { request ->
                        request.id == draft.credentialsId && request.apiKey == "new-key"
                    },
                )
            }
            assertEquals(
                "OpenAI",
                viewModel.uiState.value.apiProviderEditor.assetDraft
                    ?.displayName,
            )
        }

    @Test
    fun `onConfirmDeletionWithReassignment calls use case`() =
        runTest {
            val target =
                ModelDeletionTarget.LocalModelAsset(
                    com.browntowndev.enclave.domain.model.config
                        .LocalModelId("model-1"),
                )
            val prepared =
                PreparedModelDeletion.Ready(
                    target = target,
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    reassignmentCandidates = emptyList(),
                )
            coEvery { prepareModelDeletionUseCase(any()) } returns prepared
            coEvery {
                executeModelDeletionWithReassignmentUseCase(any(), any(), any(), any(), any())
            } returns Result.success<Unit>(Unit)

            // Trigger deletion prep
            viewModel.onDeleteLocalModelAsset(target.id)
            advanceUntilIdle()

            // Confirm deletion
            val replacementConfigId =
                com.browntowndev.enclave.domain.model.config
                    .LocalModelConfigurationId("2")
            viewModel.onConfirmDeletionWithReassignment(replacementLocalConfigId = replacementConfigId, replacementApiConfigId = null)
            advanceUntilIdle()

            coVerify {
                executeModelDeletionWithReassignmentUseCase(
                    target = target,
                    modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                    replacementLocalConfigId = replacementConfigId,
                    replacementApiConfigId = null,
                    replacementMediaProviderId = null,
                )
            }
        }

    @Test
    fun `onFetchApiModels discovers models from API draft`() =
        runTest {
            val asset = MockSettingsData.apiModels.first()
            val preset = asset.configurations.first()
            val requestSlot = io.mockk.slot<ApiModelDiscoveryRequest>()
            coEvery { discoverApiModelsUseCase(capture(requestSlot)) } returns
                ApiModelDiscoveryResult(
                    models =
                        listOf(
                            DiscoveredApiModel(id = "gpt-4o"),
                            DiscoveredApiModel(id = "gpt-4o-mini"),
                        ),
                    scope =
                        ApiModelDiscoveryScope(
                            provider = ApiProvider.OPENAI,
                            baseUrl = "",
                            credentialAlias = null,
                        ),
                )

            viewModel.onSelectApiModelAsset(asset)
            viewModel.onSelectApiModelConfig(preset)
            viewModel.onApiKeyChange("secret")
            viewModel.onFetchApiModels()
            advanceUntilIdle()

            assertEquals(ApiProvider.OPENAI, requestSlot.captured.provider)
            assertEquals("secret", requestSlot.captured.currentApiKey)
            assertEquals(asset.credentialAlias, requestSlot.captured.credentialAlias)
            assertEquals(preset.modelId, requestSlot.captured.selectedModelId)
            assertEquals(2, viewModel.uiState.value.apiProviderEditor.discovery.models.size)
            assertEquals(
                "gpt-4o",
                viewModel.uiState.value.apiProviderEditor.discovery.models[0]
                    .modelId,
            )
        }

    @Test
    fun `onFetchMediaModels for xAI and IMAGE should return grok models`() =
        runTest {
            val xaiModels =
                listOf(
                    DiscoveredApiModel(id = "grok-2", isImageGenerationCapable = true, isVideoGenerationCapable = true),
                    DiscoveredApiModel(id = "grok-2-1212", isImageGenerationCapable = true, isVideoGenerationCapable = true),
                    DiscoveredApiModel(id = "grok-beta", isImageGenerationCapable = true, isVideoGenerationCapable = true),
                    DiscoveredApiModel(id = "gpt-4o", isChatCapable = true), // Should be filtered out
                )

            coEvery { discoverApiModelsUseCase(any()) } returns
                ApiModelDiscoveryResult(
                    models = xaiModels,
                    scope =
                        ApiModelDiscoveryScope(
                            provider = ApiProvider.XAI,
                            baseUrl = "",
                            credentialAlias = "xai-alias",
                        ),
                )

            // Mock starting media creation for xAI IMAGE
            viewModel.onStartCreateMediaProviderAsset()
            viewModel.onMediaAssetFieldChange(
                MediaProviderAssetUi(
                    displayName = "xAI Images",
                    provider = ApiProvider.XAI,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "xai-alias",
                ),
            )
            viewModel.onApiKeyChange("xai-key")

            viewModel.onFetchMediaModels()
            advanceUntilIdle()

            val discoveredModels = viewModel.uiState.value.mediaProviderEditor.discovery.models

            assertTrue(
                discoveredModels.any { it.modelId.contains("grok") },
                "Expected grok models to be discovered, but found: ${discoveredModels.map { it.modelId }}",
            )
            assertEquals(3, discoveredModels.size)
        }

    @Test
    fun `onSelectApiModelAsset updates state and resets discovery if scope changes`() =
        runTest {
            val asset1 = MockSettingsData.apiModels[0] // OpenAI
            val asset2 = MockSettingsData.apiModels[1] // Anthropic

            viewModel.onSelectApiModelAsset(asset1)
            advanceUntilIdle()
            assertEquals(
                asset1.credentialsId,
                viewModel.uiState.value.apiProvidersSheet.selectedAsset
                    ?.credentialsId,
            )

            viewModel.onSelectApiModelAsset(asset2)
            advanceUntilIdle()
            assertEquals(
                asset2.credentialsId,
                viewModel.uiState.value.apiProvidersSheet.selectedAsset
                    ?.credentialsId,
            )
            assertEquals(0, viewModel.uiState.value.apiProviderEditor.discovery.models.size)
        }

    @Test
    fun `onApiModelAssetFieldChange resets discovery when provider changes`() =
        runTest {
            val asset = MockSettingsData.apiModels.first()
            viewModel.onSelectApiModelAsset(asset)
            advanceUntilIdle()

            val changedAsset = asset.copy(provider = ApiProvider.ANTHROPIC)
            viewModel.onApiModelAssetFieldChange(changedAsset)
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.apiProviderEditor.discovery.models.size)
            assertEquals(
                ApiProvider.ANTHROPIC,
                viewModel.uiState.value.apiProviderEditor.assetDraft
                    ?.provider,
            )
        }

    @Test
    fun `onSelectModelType resolves and selects correct asset`() =
        runTest {
            val asset =
                com.browntowndev.enclave.domain.model.config.ApiModelAsset(
                    credentials =
                        com.browntowndev.enclave.domain.model.config.ApiCredentials(
                            id = ApiCredentialsId("api-1"),
                            displayName = "OpenAI",
                            provider = ApiProvider.OPENAI,
                            credentialAlias = "alias-1",
                        ),
                    configurations =
                        listOf(
                            com.browntowndev.enclave.domain.model.config.ApiModelConfiguration(
                                apiCredentialsId = ApiCredentialsId("api-1"),
                                modelId = "gpt-4o",
                                displayName = "Default",
                            ),
                        ),
                )
            val selection =
                ResolvedAssignedModelSelection(
                    apiAsset = asset,
                    apiConfig = null,
                )
            coEvery { resolveAssignedModelSelectionUseCase(ModelType.MAIN) } returns selection

            viewModel.onSelectModelType(ModelType.MAIN)
            advanceUntilIdle()

            assertEquals(
                asset.credentials.id,
                viewModel.uiState.value.apiProvidersSheet.selectedAsset
                    ?.credentialsId,
            )
        }

    @Test
    fun `onFetchMediaModels discovers models from media draft`() =
        runTest {
            val mediaAsset =
                MediaProviderAssetUi(
                    displayName = "DALL-E 3",
                    provider = ApiProvider.OPENAI,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "media-alias",
                )

            coEvery { discoverApiModelsUseCase(any()) } returns
                ApiModelDiscoveryResult(
                    models =
                        listOf(
                            DiscoveredApiModel(id = "dall-e-3"),
                            DiscoveredApiModel(id = "gpt-4o"), // Should be filtered out if not image capable
                        ),
                    scope =
                        ApiModelDiscoveryScope(
                            provider = ApiProvider.OPENAI,
                            baseUrl = "",
                            credentialAlias = "media-alias",
                        ),
                )

            viewModel.onSelectMediaProviderAsset(mediaAsset)
            viewModel.onFetchMediaModels()
            advanceUntilIdle()

            coVerify {
                discoverApiModelsUseCase(
                    match { request ->
                        request.provider == ApiProvider.OPENAI &&
                            request.credentialAlias == "media-alias"
                    },
                )
            }
        }

    @Test
    fun `onShowByokSheet toggles api providers sheet visibility`() =
        runTest {
            assertFalse(viewModel.uiState.value.home.isApiProvidersSheetOpen)

            viewModel.onShowByokSheet(show = true)

            assertTrue(viewModel.uiState.value.home.isApiProvidersSheetOpen)
            assertTrue(viewModel.uiState.value.apiProvidersSheet.isVisible)
        }

    @Test
    @DisplayName("SET-007 Gen2 hides Juggernaut from assignment and shows disabled reason")
    fun set007_disabledJuggernautHiddenFromAssignment() =
        runTest {
            val juggernautSha = ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_SHA
            val juggernautAsset = LocalModelAsset(
                metadata = LocalModelMetadata(
                    id = LocalModelId("juggernaut-model"),
                    displayName = ModelPackTestFixtures.JUGGERNAUT_PACK_DISPLAY,
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_FILE,
                    localFileName = ModelPackTestFixtures.JUGGERNAUT_DIFFUSION_FILE,
                    sha256 = juggernautSha,
                    sizeInBytes = 3_747_687_306L,
                    modelFileFormat = ModelFileFormat.BIN,
                    source = DownloadSource.HUGGING_FACE,
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        id = com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId(ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID),
                        localModelId = LocalModelId("juggernaut-model"),
                        displayName = ModelPackTestFixtures.JUGGERNAUT_PACK_DISPLAY,
                        maxTokens = 0,
                        contextWindow = 0,
                        temperature = 0.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                        defaultAssignments = listOf(ModelType.IMAGE_EDIT),
                    ),
                ),
            )
            val juggernautPack = ModelPack(
                packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
                displayName = ModelPackTestFixtures.JUGGERNAUT_PACK_DISPLAY,
                slot = ModelType.IMAGE_EDIT,
                members = listOf(ModelPackMember(ModelPackRole.DIFFUSION, juggernautAsset)),
            )
            val packRow = ModelPackSettingsRow(
                pack = juggernautPack,
                completeness = ModelPackCompleteness(
                    packId = ModelPackTestFixtures.JUGGERNAUT_PACK_ID,
                    installedCount = 0,
                    totalCount = 2,
                    isComplete = false,
                    members = emptyList(),
                ),
                isDiffusionSoftDeleted = false,
                areUtilityRegistryEntriesAbsent = true,
                showDownloadAction = false,
                disabledReason = "Requires Snapdragon 8 Gen 3+ with QNN.",
                isEligible = false,
            )

            coEvery { getModelPacksForSettingsUseCase() } returns listOf(packRow)
            localModelAssetsStateFlow.value = listOf(juggernautAsset)

            viewModel.onShowModelConfigSheet(show = true)
            advanceUntilIdle()

            val assignmentOptions =
                selectableLocalAssetsForSlot(
                    modelType = ModelType.IMAGE_EDIT,
                    assets = viewModel.uiState.value.localModelsSheet.models,
                    modelPacks = viewModel.uiState.value.localModelsSheet.modelPacks,
                )
            assertTrue(
                assignmentOptions.none { asset ->
                    asset.configurations.any { it.id.value == ModelPackTestFixtures.JUGGERNAUT_CONFIG_ID }
                },
            )
            assertEquals(
                "Requires Snapdragon 8 Gen 3+ with QNN.",
                viewModel.uiState.value.localModelsSheet.modelPacks.single().disabledReason,
            )
        }

    @Test
    @DisplayName("SET-002 Gen2 hides SDXL from assignment and shows disabled reason")
    fun set002_disabledSdxlHiddenFromAssignment() =
        runTest {
            val sdxlSha = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"
            val sdxlAsset = LocalModelAsset(
                metadata = LocalModelMetadata(
                    id = LocalModelId("sdxl-model"),
                    displayName = "Stable Diffusion XL",
                    huggingFaceModelName = "xororz/sdxl-qnn",
                    remoteFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    localFileName = "realvis_xl_v5_dmd2_qnn2.28_8gen3.zip",
                    sha256 = sdxlSha,
                    sizeInBytes = 3_504_485_672L,
                    modelFileFormat = ModelFileFormat.BIN,
                    source = DownloadSource.HUGGING_FACE,
                ),
                configurations = listOf(
                    LocalModelConfiguration(
                        id = com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId("sdxl-local-config"),
                        localModelId = LocalModelId("sdxl-model"),
                        displayName = "Stable Diffusion XL",
                        maxTokens = 0,
                        contextWindow = 0,
                        temperature = 0.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                        defaultAssignments = listOf(ModelType.IMAGE_GENERATION),
                    )
                )
            )
            val sdxlPack = ModelPack(
                packId = "sdxl-t2i",
                displayName = "Stable Diffusion XL",
                slot = ModelType.IMAGE_GENERATION,
                members = listOf(ModelPackMember(ModelPackRole.DIFFUSION, sdxlAsset)),
            )
            val packRow = ModelPackSettingsRow(
                pack = sdxlPack,
                completeness = ModelPackCompleteness(
                    packId = "sdxl-t2i",
                    installedCount = 0,
                    totalCount = 1,
                    isComplete = false,
                    members = emptyList(),
                ),
                isDiffusionSoftDeleted = false,
                areUtilityRegistryEntriesAbsent = true,
                showDownloadAction = false,
                disabledReason = "Requires Snapdragon 8 Gen 3+ with QNN.",
                isEligible = false,
            )

            coEvery { getModelPacksForSettingsUseCase() } returns listOf(packRow)
            localModelAssetsStateFlow.value = listOf(sdxlAsset)

            viewModel.onShowModelConfigSheet(show = true)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val assignmentOptions =
                selectableLocalAssetsForSlot(
                    modelType = ModelType.IMAGE_GENERATION,
                    assets = state.localModelsSheet.models,
                    modelPacks = state.localModelsSheet.modelPacks,
                )
            assertTrue(
                assignmentOptions.none { asset ->
                    asset.configurations.any { it.id.value == "sdxl-local-config" }
                },
            )
            // The pack row must still surface with the exact Gen2 disabled reason and be ineligible.
            val mappedPack = state.localModelsSheet.modelPacks.firstOrNull { it.packId == "sdxl-t2i" }
            assertNotNull(mappedPack)
            assertEquals("Requires Snapdragon 8 Gen 3+ with QNN.", mappedPack!!.disabledReason)
            assertFalse(mappedPack.isEligible)
        }
}

internal fun testUsageSpendSummaryUseCase(): ObserveUsageSpendSummaryUseCase {
    val useCase = io.mockk.mockk<ObserveUsageSpendSummaryUseCase>()
    every { useCase.invoke(any()) } returns
        flowOf(
            UsageSpendSummary(
                period = UsagePeriod.LIFETIME,
                periodStartUtcMs = null,
                periodEndUtcMs = null,
                totalCostUsd = BigDecimal.ZERO,
                telemetryBackedTurnCount = 0,
                missingCostTurnCount = 0,
                providerBreakdown = emptyList(),
            ),
        )
    return useCase
}
