package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.theme.FakeThemePreferencesRepository
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.usecase.security.ObserveEntitlementStateUseCase
import com.browntowndev.enclave.domain.theme.ObserveThemePreferencesUseCase
import com.browntowndev.enclave.domain.theme.SetThemeFamilyUseCase

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    private val getSettingsUseCase = FakeGetSettingsUseCase()
    private val updateThemeUseCase = FakeUpdateThemeUseCase()
    private val updateHapticResponseUseCase = FakeUpdateHapticResponseUseCase()
    private val updateCustomizationEnabledUseCase = FakeUpdateCustomizationEnabledUseCase()
    private val updateSelectedPromptOptionUseCase = FakeUpdateSelectedPromptOptionUseCase()
    private val updateCustomPromptTextUseCase = FakeUpdateCustomPromptTextUseCase()
    private val updateAllowMemoriesUseCase = FakeUpdateAllowMemoriesUseCase()
    private val updateSearchEnabledUseCase = FakeUpdateSearchEnabledUseCase()
    private val updateXSearchEnabledUseCase = FakeUpdateXSearchEnabledUseCase()
    private val saveTavilyApiKeyUseCase = FakeSaveTavilyApiKeyUseCase()
    private val clearTavilyApiKeyUseCase = FakeClearTavilyApiKeyUseCase()
    private val saveXApiKeyUseCase = FakeSaveXApiKeyUseCase()
    private val clearXApiKeyUseCase = FakeClearXApiKeyUseCase()
    private val updateBackgroundInferenceEnabledUseCase = FakeUpdateBackgroundInferenceEnabledUseCase()
    private val updateAutomaticErrorTrackingEnabledUseCase = FakeUpdateAutomaticErrorTrackingEnabledUseCase()
    private val getLocalModelAssetsUseCase = FakeGetLocalModelAssetsUseCase()
    private val getRestorableLocalModelsUseCase = FakeGetRestorableLocalModelsUseCase()
    private val saveLocalModelPresetUseCase = FakeSaveLocalModelPresetUseCase()
    private val reDownloadModelUseCase = FakeReDownloadModelUseCase()
    private val getModelPacksForSettingsUseCase = FakeGetModelPacksForSettingsUseCase()
    private val getApiModelAssetsUseCase = FakeGetApiModelAssetsUseCase()
    private val saveApiProviderDraftUseCase = FakeSaveApiProviderDraftUseCase()
    private val saveApiPresetUseCase = FakeSaveApiPresetUseCase()
    private val discoverApiModelsUseCase = FakeDiscoverApiModelsUseCase()
    private val applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase()
    private val getDefaultModelsUseCase = FakeGetDefaultModelsUseCase()
    private val setDefaultModelUseCase = FakeSetDefaultModelUseCase()
    private val resolveAssignedModelSelectionUseCase = FakeResolveAssignedModelSelectionUseCase()
    private val clearDefaultModelUseCase = FakeClearDefaultModelUseCase()
    private val prepareModelDeletionUseCase = FakePrepareModelDeletionUseCase()
    private val executeModelDeletionWithReassignmentUseCase = FakeExecuteModelDeletionWithReassignmentUseCase()

    private val getTtsProvidersUseCase = FakeGetTtsProvidersUseCase()
    private val saveTtsProviderUseCase = FakeSaveTtsProviderUseCase()
    private val deleteTtsProviderUseCase = FakeDeleteTtsProviderUseCase()

    private val getMediaProvidersUseCase = FakeGetMediaProvidersUseCase()
    private val saveMediaProviderUseCase = FakeSaveMediaProviderUseCase()
    private val deleteMediaProviderUseCase = FakeDeleteMediaProviderUseCase()

    private val loginWithGoogleUseCase = FakeLoginWithGoogleUseCase()
    private val logoutUseCase = FakeLogoutUseCase()
    private val getAuthStateUseCase = FakeGetAuthStateUseCase()
    private val entitlementPort = FakeEntitlementPort()

    private val memoriesRepository = com.browntowndev.enclave.core.testing.FakeMemoriesRepository()

    private val localModelAssetUiMapper = LocalModelAssetUiMapper()
    private val apiModelAssetUiMapper = ApiModelAssetUiMapper()
    private val ttsProviderAssetUiMapper = TtsProviderAssetUiMapper()
    private val mediaProviderAssetUiMapper = MediaProviderAssetUiMapper()
    private val reassignmentOptionUiMapper = ReassignmentOptionUiMapper()
    private val errorHandler = FakeViewModelErrorHandler()

    private val localModelAssetsStateFlow = MutableStateFlow<List<LocalModelAsset>>(emptyList())

    // ENC-27: recorded invocations replace MockK coVerify; the fakes record them (ENC-28).
    private val discoverModelsCalls get() = discoverApiModelsUseCase.calls
    private val saveTtsProviderCalls get() = saveTtsProviderUseCase.calls
    private val setDefaultModelCalls get() = setDefaultModelUseCase.calls
    private val clearDefaultModelCalls get() = clearDefaultModelUseCase.calls
    private val saveApiProviderDraftCalls get() = saveApiProviderDraftUseCase.calls
    private val executeDeletionCalls get() = executeModelDeletionWithReassignmentUseCase.calls
    private val saveLocalModelPresetCalls get() = saveLocalModelPresetUseCase.calls.map { it.first }
    private val insertMemoryCalls get() = memoriesRepository.insertCalls
    private val updateMemoryCalls get() = memoriesRepository.updateCalls
    private val deleteMemoryCalls get() = memoriesRepository.deleteCalls
    private val updateThemeCalls get() = updateThemeUseCase.calls
    private val updateHapticCalls get() = updateHapticResponseUseCase.calls
    private val updateBackgroundInferenceCalls get() = updateBackgroundInferenceEnabledUseCase.calls
    private val updateXSearchEnabledCalls get() = updateXSearchEnabledUseCase.calls
    private val saveXApiKeyCalls get() = saveXApiKeyUseCase.calls
    private val loginWithGoogleCallCount get() = loginWithGoogleUseCase.callCount
    private val logoutCallCount get() = logoutUseCase.callCount
    private val clearXApiKeyCallCount get() = clearXApiKeyUseCase.callCount

    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        val settingsFlow = MutableStateFlow(SettingsData())
        getSettingsUseCase.flow = settingsFlow
        getLocalModelAssetsUseCase.flow = localModelAssetsStateFlow
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
        getApiModelAssetsUseCase.flow = MutableStateFlow(apiAssets)

        val updateSearchProviderUseCase = FakeUpdateSearchProviderUseCase()
        val saveExaApiKeyUseCase = FakeSaveExaApiKeyUseCase()
        val clearExaApiKeyUseCase = FakeClearExaApiKeyUseCase()
        val updateShowSearchProviderSwitchAlertUseCase = FakeUpdateShowSearchProviderSwitchAlertUseCase()

        val observeActiveUtilityModelsUseCase = FakeObserveActiveUtilityModelsUseCase()

        val dataControlsUseCases = FakeSettingsDataControlsUseCases()

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
                        reDownloadModelPack = FakeReDownloadModelPackUseCase(),
                        deleteModelPack = FakeDeleteModelPackUseCase(),
                    ),
                apiProviders =
                    SettingsApiProviderUseCasesImpl(
                        getApiModelAssets = getApiModelAssetsUseCase,
                        saveApiProviderDraft = saveApiProviderDraftUseCase,
                        saveApiPreset = saveApiPresetUseCase,
                        discoverApiModels = discoverApiModelsUseCase,
                        applyApiModelMetadataDefaults = applyApiModelMetadataDefaultsUseCase,
                        testApiProviderConnection = FakeTestApiProviderConnectionUseCase(),
                        fetchOpenRouterModelEndpoints = FakeFetchOpenRouterModelEndpointsUseCase(),
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

        val embeddingProgressPort = com.browntowndev.enclave.core.testing.FakeEmbeddingProgressPort()


        val detectVaultReconciliationStateUseCase = FakeDetectVaultReconciliationStateUseCase()
        val adoptCloudVaultUseCase = FakeAdoptCloudVaultUseCase()
        val bootstrapCloudVaultFromLocalUseCase = FakeBootstrapCloudVaultFromLocalUseCase()
        val createVaultUseCase = FakeCreateVaultUseCase()
        val cryptoManager = com.browntowndev.enclave.core.testing.FakeCryptoManagerPort()
        val vaultMetadataPort = com.browntowndev.enclave.core.testing.FakeVaultMetadataPort()
        val cloudSyncPort = com.browntowndev.enclave.core.testing.FakeCloudSyncPort()
        val generalDelegate =
            SettingsGeneralDelegate(
                settingsUseCases.preferences,
                settingsUseCases.auth,
                errorHandler,
                detectVaultReconciliationStateUseCase,
                adoptCloudVaultUseCase,
                bootstrapCloudVaultFromLocalUseCase,
                createVaultUseCase,
                FakeUnlockVaultFromStoredMetadataUseCase(),
                FakeStartCloudSyncListeningUseCase(),
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
                com.browntowndev.enclave.core.testing.FakeCloudSyncPort(),
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
                observeEntitlementStateUseCase = ObserveEntitlementStateUseCase(entitlementPort),
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
            loginWithGoogleUseCase.result = Result.success(UserProfile("uid", "Name", "email", null))

            viewModel.onLoginWithGoogle()
            advanceUntilIdle()

            assertEquals(1, loginWithGoogleCallCount)
            assertTrue(viewModel.uiState.value.vaultReauthSheet is VaultReauthSheetState.EnterPassword)
        }

    @Test
    fun `onLogout signs out and clears data`() =
        runTest {
            logoutUseCase.result = Result.success(Unit)

            viewModel.onLogout()
            advanceUntilIdle()

            assertEquals(1, logoutCallCount)
        }

    @Test
    fun `initial state is correct`() =
        runTest {
            assertEquals(AppThemeUi.SYSTEM, viewModel.uiState.value.home.theme)
            assertTrue(viewModel.uiState.value.home.hapticResponse)
        }

    @Test
    fun `entitlement state flows into home subscription`() =
        runTest {
            entitlementPort.setEntitlementState(EntitlementState.NotEntitled)
            runCurrent()
            assertEquals(SubscriptionUiState.NotSubscribed, viewModel.uiState.value.home.subscription)

            entitlementPort.setEntitlementState(EntitlementState.Entitled())
            runCurrent()
            assertEquals(SubscriptionUiState.Subscribed(expiryLabel = null), viewModel.uiState.value.home.subscription)

            entitlementPort.setEntitlementState(EntitlementState.Unknown)
            runCurrent()
            assertEquals(SubscriptionUiState.Error, viewModel.uiState.value.home.subscription)

            entitlementPort.setEntitlementState(EntitlementState.SignedOut)
            runCurrent()
            assertEquals(SubscriptionUiState.Hidden, viewModel.uiState.value.home.subscription)
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
            discoverApiModelsUseCase.result =
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

            assertEquals(1, discoverModelsCalls.size)
            assertEquals(ApiProvider.GOOGLE, discoverModelsCalls.single().provider)
            assertEquals("google-key", discoverModelsCalls.single().currentApiKey)
            assertEquals(null, discoverModelsCalls.single().credentialAlias)
            assertEquals(null, discoverModelsCalls.single().selectedModelId)
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
            saveTtsProviderUseCase.result = Result.success(ttsId)

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

            assertEquals(1, saveTtsProviderCalls.size)
            assertEquals("Google TTS", saveTtsProviderCalls.single().displayName)
            assertEquals(
                listOf(FakeSetDefaultModelUseCase.Args(ModelType.TTS, null, null, ttsId, null, null)),
                setDefaultModelCalls,
            )
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

            assertEquals(
                listOf(FakeSetDefaultModelUseCase.Args(ModelType.TTS, null, null, ttsId, null, null)),
                setDefaultModelCalls,
            )
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

            assertEquals(listOf(ModelType.TTS), clearDefaultModelCalls)
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

            assertEquals(1, clearXApiKeyCallCount)
            assertEquals("", viewModel.currentXApiKey.value)
        }

    @Test
    fun `onSaveXSearchSettings saves enabled status and api key`() =
        runTest {
            viewModel.onXSearchEnabledChange(true)
            viewModel.onXApiKeyChange("x-api-key")

            viewModel.onSaveXSearchSettings(onSuccess = {})
            advanceUntilIdle()

            assertEquals(listOf(true), updateXSearchEnabledCalls)
            assertEquals(listOf("x-api-key"), saveXApiKeyCalls)
            assertEquals("", viewModel.currentXApiKey.value)
            assertEquals(false, viewModel.uiState.value.xSearchEditor.enabled)
        }

    @Test
    fun `onSaveMemory inserts new memory when id is empty`() =
        runTest {
            val draft = StoredMemory(id = "", text = "New memory", category = MemoryCategory.PREFERENCES)

            viewModel.onAddMemory()
            viewModel.onUpdateMemoryDraft(draft.text, draft.category)
            viewModel.onSaveMemory()
            advanceUntilIdle()

            assertEquals(listOf(draft.category to draft.text), insertMemoryCalls)
        }

    @Test
    fun `onSaveMemory updates existing memory when id is not empty`() =
        runTest {
            val existingMemory = StoredMemory(id = "mem-1", text = "Old memory", category = MemoryCategory.PREFERENCES)
            val updatedDraft = existingMemory.copy(text = "Updated memory")


            viewModel.onEditMemory(existingMemory)
            viewModel.onUpdateMemoryDraft(updatedDraft.text, updatedDraft.category)
            viewModel.onSaveMemory()
            advanceUntilIdle()

            assertEquals(listOf(Triple(updatedDraft.id, updatedDraft.text, updatedDraft.category)), updateMemoryCalls)
        }

    @Test
    fun `onThemeChange updates preferences`() =
        runTest {
            viewModel.onThemeChange(AppThemeUi.DARK)
            advanceUntilIdle()
            assertEquals(listOf(AppTheme.DARK), updateThemeCalls)
        }

    @Test
    fun `onHapticResponseChange updates preferences`() =
        runTest {
            viewModel.onHapticResponseChange(false)
            advanceUntilIdle()
            assertEquals(listOf(false), updateHapticCalls)
        }

    @Test
    fun `onBackgroundInferenceChange updates preferences`() =
        runTest {
            viewModel.onBackgroundInferenceChange(true)
            advanceUntilIdle()
            assertEquals(listOf(true), updateBackgroundInferenceCalls)
        }

    @Test
    fun `onDeleteMemory calls repository`() =
        runTest {
            viewModel.onDeleteMemory("mem-1")
            advanceUntilIdle()
            assertEquals(listOf("mem-1"), deleteMemoryCalls)
        }

    @Test
    fun `onSaveLocalModelConfig calls use case`() =
        runTest {
            val asset = MockSettingsData.localModels.first()
            val config = LocalModelConfigUi(displayName = "Config 1")

            viewModel.onSelectLocalModelAsset(asset)
            viewModel.onSelectLocalModelConfig(config)
            viewModel.onSaveLocalModelConfig(onSuccess = {})
            advanceUntilIdle()

            assertEquals(listOf(asset.metadataId), saveLocalModelPresetCalls)
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

            viewModel.onSelectLocalModelAsset(asset)
            viewModel.onSelectLocalModelConfig(config)
            viewModel.onShowModelConfigSheet(false)

            viewModel.onSaveLocalModelConfig(onSuccess = {})
            advanceUntilIdle()

            assertEquals(listOf(asset.metadataId), saveLocalModelPresetCalls)
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
            saveApiProviderDraftUseCase.result =
                Result.success(
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

            assertEquals(1, saveApiProviderDraftCalls.size)
            assertEquals(draft.credentialsId, saveApiProviderDraftCalls.single().id)
            assertEquals("new-key", saveApiProviderDraftCalls.single().apiKey)
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
            prepareModelDeletionUseCase.result = prepared

            // Trigger deletion prep
            viewModel.onDeleteLocalModelAsset(target.id)
            advanceUntilIdle()

            // Confirm deletion
            val replacementConfigId =
                com.browntowndev.enclave.domain.model.config
                    .LocalModelConfigurationId("2")
            viewModel.onConfirmDeletionWithReassignment(replacementLocalConfigId = replacementConfigId, replacementApiConfigId = null)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    FakeExecuteModelDeletionWithReassignmentUseCase.Args(
                        target = target,
                        modelTypesNeedingReassignment = listOf(ModelType.MAIN),
                        replacementLocalConfigId = replacementConfigId,
                        replacementApiConfigId = null,
                        replacementMediaProviderId = null,
                    ),
                ),
                executeDeletionCalls,
            )
        }

    @Test
    fun `onFetchApiModels discovers models from API draft`() =
        runTest {
            val asset = MockSettingsData.apiModels.first()
            val preset = asset.configurations.first()
            discoverApiModelsUseCase.result =
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

            assertEquals(ApiProvider.OPENAI, discoverApiModelsUseCase.calls.single().provider)
            assertEquals("secret", discoverApiModelsUseCase.calls.single().currentApiKey)
            assertEquals(asset.credentialAlias, discoverApiModelsUseCase.calls.single().credentialAlias)
            assertEquals(preset.modelId, discoverApiModelsUseCase.calls.single().selectedModelId)
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

            discoverApiModelsUseCase.result =
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
            resolveAssignedModelSelectionUseCase.result = selection

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

            discoverApiModelsUseCase.result =
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

            assertTrue(
                discoverModelsCalls.any {
                    it.provider == ApiProvider.OPENAI && it.credentialAlias == "media-alias"
                },
            )
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

            getModelPacksForSettingsUseCase.result = listOf(packRow)
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

            getModelPacksForSettingsUseCase.result = listOf(packRow)
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

internal fun testUsageSpendSummaryUseCase(): ObserveUsageSpendSummaryUseCase = FakeObserveUsageSpendSummaryUseCase()
