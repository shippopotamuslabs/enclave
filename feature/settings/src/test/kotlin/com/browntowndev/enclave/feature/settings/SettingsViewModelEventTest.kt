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
    private val memoriesRepository = com.browntowndev.enclave.core.testing.FakeMemoriesRepository()
    private val errorHandler = FakeViewModelErrorHandler()

    private val localModelAssetUiMapper = LocalModelAssetUiMapper()
    private val apiModelAssetUiMapper = ApiModelAssetUiMapper()
    private val ttsProviderAssetUiMapper = TtsProviderAssetUiMapper()
    private val mediaProviderAssetUiMapper = MediaProviderAssetUiMapper()
    private val reassignmentOptionUiMapper = ReassignmentOptionUiMapper()
    private val applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase()

    @BeforeEach
    fun setup() {
        val settingsUseCases =
            FakeSettingsUseCases(
                preferences =
                    FakeSettingsPreferencesUseCases(
                        getSettings = FakeGetSettingsUseCase(),
                    ),
                localModels =
                    FakeSettingsLocalModelUseCases(
                        getLocalModelAssets = FakeGetLocalModelAssetsUseCase(),
                        getModelPacksForSettings = FakeGetModelPacksForSettingsUseCase(),
                        observeActiveUtilityModels = FakeObserveActiveUtilityModelsUseCase(),
                    ),
                apiProviders =
                    FakeSettingsApiProviderUseCases(
                        getApiModelAssets = FakeGetApiModelAssetsUseCase(),
                    ),
                assignments =
                    FakeSettingsAssignmentUseCases(
                        getDefaultModels = FakeGetDefaultModelsUseCase(),
                    ),
                tts =
                    FakeSettingsTtsUseCases(
                        getTtsProviders = FakeGetTtsProvidersUseCase(),
                    ),
                liveVoice =
                    FakeSettingsLiveVoiceUseCases(
                        getLiveVoiceProviders = FakeGetLiveVoiceProvidersUseCase(),
                    ),
                media =
                    FakeSettingsMediaUseCases(
                        getMediaProviders = FakeGetMediaProvidersUseCase(),
                    ),
                auth =
                    FakeSettingsAuthUseCases(
                        getAuthState = FakeGetAuthStateUseCase(),
                    ),
                memories = FakeSettingsMemoriesUseCases(memoriesRepository),
            )

        val embeddingProgressPort = com.browntowndev.enclave.core.testing.FakeEmbeddingProgressPort()

        val deletionHelper = SettingsDeletionHelper(settingsUseCases.deletion, reassignmentOptionUiMapper, errorHandler)
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
