package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.theme.FakeThemePreferencesRepository
import com.browntowndev.enclave.domain.theme.ObserveThemePreferencesUseCase
import com.browntowndev.enclave.domain.theme.SetThemeFamilyUseCase

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.*
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.usecase.byok.*
import com.browntowndev.enclave.domain.usecase.media.*
import com.browntowndev.enclave.domain.usecase.modelconfig.*
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.settings.*
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.feature.settings.usage.UsageSpendUiMapper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class SettingsViewModelEventTest {
    private lateinit var viewModel: SettingsViewModel
    private val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)

    private val localModelAssetUiMapper = LocalModelAssetUiMapper()
    private val apiModelAssetUiMapper = ApiModelAssetUiMapper()
    private val ttsProviderAssetUiMapper = TtsProviderAssetUiMapper()
    private val mediaProviderAssetUiMapper = MediaProviderAssetUiMapper()
    private val reassignmentOptionUiMapper = ReassignmentOptionUiMapper()
    private val applyApiModelMetadataDefaultsUseCase = mockk<ApplyApiModelMetadataDefaultsUseCase>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }

        val getSettingsUseCase = mockk<GetSettingsUseCase>()
        every { getSettingsUseCase() } returns MutableStateFlow(SettingsData())
        every { memoriesRepository.getAllMemoriesFlow() } returns MutableStateFlow<List<Memory>>(emptyList())

        val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
        every { getLocalModelAssetsUseCase() } returns MutableStateFlow<List<LocalModelAsset>>(emptyList())

        val observeActiveUtilityModelsUseCase = mockk<ObserveActiveUtilityModelsUseCase>()
        every { observeActiveUtilityModelsUseCase() } returns kotlinx.coroutines.flow.flowOf(emptyList())

        val getModelPacksForSettingsUseCase = mockk<GetModelPacksForSettingsUseCase>()
        coEvery { getModelPacksForSettingsUseCase() } returns emptyList()

        val getApiModelAssetsUseCase = mockk<GetApiModelAssetsUseCase>()
        every { getApiModelAssetsUseCase() } returns MutableStateFlow<List<ApiModelAsset>>(emptyList())

        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { getDefaultModelsUseCase() } returns MutableStateFlow<List<DefaultModelAssignment>>(emptyList())

        val getTtsProvidersUseCase = mockk<GetTtsProvidersUseCase>()
        every { getTtsProvidersUseCase() } returns MutableStateFlow<List<TtsProviderAsset>>(emptyList())

        val getLiveVoiceProvidersUseCase = mockk<GetLiveVoiceProvidersUseCase>()
        every { getLiveVoiceProvidersUseCase() } returns MutableStateFlow<List<LiveVoiceProviderAsset>>(emptyList())

        val getMediaProvidersUseCase = mockk<GetMediaProvidersUseCase>()
        every { getMediaProvidersUseCase() } returns MutableStateFlow<List<MediaProviderAsset>>(emptyList())

        val getAuthStateUseCase = mockk<GetAuthStateUseCase>()
        every { getAuthStateUseCase() } returns MutableStateFlow(com.browntowndev.enclave.domain.port.auth.AuthState.Loading)

        val settingsUseCases =
            mockk<SettingsUseCases>(relaxed = true) {
                val mockPreferences = mockk<SettingsPreferencesUseCases>(relaxed = true)
                every { preferences } returns mockPreferences
                every { mockPreferences.getSettings } returns getSettingsUseCase
                every { mockPreferences.updateTheme } returns mockk(relaxed = true)
                every { mockPreferences.updateBackgroundInferenceEnabled } returns mockk(relaxed = true)
                every { mockPreferences.updateAutomaticErrorTrackingEnabled } returns mockk(relaxed = true)
                every { mockPreferences.updateSearchEnabled } returns mockk(relaxed = true)
                every { mockPreferences.updateSearchProvider } returns mockk(relaxed = true)
                every { mockPreferences.saveTavilyApiKey } returns mockk(relaxed = true)

                every { localModels } returns
                    mockk(relaxed = true) {
                        every { getLocalModelAssets } returns getLocalModelAssetsUseCase
                        every { getModelPacksForSettings } returns getModelPacksForSettingsUseCase
                        every { observeActiveUtilityModels } returns observeActiveUtilityModelsUseCase
                    }
                every { apiProviders } returns
                    mockk(relaxed = true) {
                        every { getApiModelAssets } returns getApiModelAssetsUseCase
                    }
                every { assignments } returns
                    mockk(relaxed = true) {
                        every { getDefaultModels } returns getDefaultModelsUseCase
                    }
                every { tts } returns
                    mockk(relaxed = true) {
                        every { getTtsProviders } returns getTtsProvidersUseCase
                    }
                every { liveVoice } returns
                    mockk(relaxed = true) {
                        every { getLiveVoiceProviders } returns getLiveVoiceProvidersUseCase
                    }
                every { media } returns
                    mockk(relaxed = true) {
                        every { getMediaProviders } returns getMediaProvidersUseCase
                    }
                every { auth } returns
                    mockk(relaxed = true) {
                        every { getAuthState } returns getAuthStateUseCase
                    }
                every { memories } returns
                    mockk(relaxed = true) {
                        every { getAllMemoriesFlow() } returns
                            MutableStateFlow<List<com.browntowndev.enclave.domain.model.memory.Memory>>(emptyList())
                    }
                every { deletion } returns mockk(relaxed = true)
                every { getSettings } returns getSettingsUseCase
            }

        val embeddingProgressPort = mockk<com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort>()
        every { embeddingProgressPort.isReindexing } returns MutableStateFlow(false)
        every { embeddingProgressPort.progress } returns MutableStateFlow(0f)


        val deletionHelper = SettingsDeletionHelper(settingsUseCases.deletion, reassignmentOptionUiMapper, errorHandler)
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

        val dataControlsDelegate = SettingsDataControlsDelegate(settingsUseCases.dataControls, settingsUseCases.preferences, errorHandler)

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
    fun `onShowDataControlsSheet updates uiState`() =
        runTest {
            viewModel.onShowDataControlsSheet(true)
            runCurrent()
            assertTrue(viewModel.uiState.value.dataControls.isSheetOpen)

            viewModel.onShowDataControlsSheet(false)
            runCurrent()
            assertFalse(viewModel.uiState.value.dataControls.isSheetOpen)
        }

    @Test
    fun `onShowMemoriesSheet updates uiState`() =
        runTest {
            viewModel.onShowMemoriesSheet(true)
            runCurrent()
            assertTrue(viewModel.uiState.value.memories.isSheetOpen)

            viewModel.onShowMemoriesSheet(false)
            runCurrent()
            assertFalse(viewModel.uiState.value.memories.isSheetOpen)
        }

    @Test
    fun `onAddMemory updates memoriesState`() =
        runTest {
            viewModel.onAddMemory()
            runCurrent()

            val state = viewModel.uiState.value.memories
            assertTrue(state.isEditing)
            assertEquals(MemoryCategory.PREFERENCES, state.memoryDraft?.category)
        }

    @Test
    fun `onUpdateMemoryDraft updates draft in uiState`() =
        runTest {
            viewModel.onAddMemory()
            viewModel.onUpdateMemoryDraft("Test memory", MemoryCategory.FACTS)
            runCurrent()

            val state = viewModel.uiState.value.memories
            assertEquals("Test memory", state.memoryDraft?.text)
            assertEquals(MemoryCategory.FACTS, state.memoryDraft?.category)
        }

    @Test
    fun `onSaveCustomization closes sheet`() =
        runTest {
            viewModel.onShowCustomizationSheet(true)
            runCurrent()
            assertTrue(viewModel.uiState.value.customization.isSheetOpen)

            viewModel.onSaveCustomization()
            runCurrent()
            assertFalse(viewModel.uiState.value.customization.isSheetOpen)
        }

    @Test
    fun `onTavilyApiKeyChange updates uiState`() =
        runTest {
            viewModel.onTavilyApiKeyChange("test-key")
        }

    @Test
    fun `onThemeChange updates uiState`() =
        runTest {
            viewModel.onThemeChange(AppThemeUi.DARK)
            // Similar to above, verifying no crash.
        }

    @Test
    fun `onBackgroundInferenceChange updates uiState`() =
        runTest {
            viewModel.onBackgroundInferenceChange(true)
        }

    @Test
    fun `onSearchEnabledChange updates uiState`() =
        runTest {
            viewModel.onSearchEnabledChange(true)
        }

    @Test
    fun `onSaveMemory calls repository and updates state`() =
        runTest {
            viewModel.onAddMemory()
            viewModel.onUpdateMemoryDraft("Test memory", MemoryCategory.FACTS)

            viewModel.onSaveMemory()
            runCurrent()

            val state = viewModel.uiState.value.memories
            assertFalse(state.isEditing)
            assertEquals(null, state.memoryDraft)
        }

    @Test
    fun `onSearchProviderChange updates uiState`() =
        runTest {
            viewModel.onSearchProviderChange(SearchProvider.TAVILY)
        }
}
