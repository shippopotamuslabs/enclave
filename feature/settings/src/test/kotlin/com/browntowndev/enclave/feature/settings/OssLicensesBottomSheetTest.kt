package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.theme.FakeThemePreferencesRepository
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.domain.usecase.security.ObserveEntitlementStateUseCase
import com.browntowndev.enclave.domain.theme.ObserveThemePreferencesUseCase
import com.browntowndev.enclave.domain.theme.SetThemeFamilyUseCase

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.media.DeleteMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelUseCase
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.settings.*
import com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OssLicensesBottomSheetTest {
    // ── Group 1: SettingsGeneralDelegate sheet toggle ──────────────────────

    @Nested
    inner class DelegateToggle {
        private val preferencesUseCases = FakeSettingsPreferencesUseCases()
        private val authUseCases = FakeSettingsAuthUseCases()
        private val errorHandler = FakeViewModelErrorHandler()
        private lateinit var delegate: SettingsGeneralDelegate
        private lateinit var sheetVisibility: MutableStateFlow<SheetVisibilityState>

        @BeforeEach
        fun setup() {
            delegate =
                SettingsGeneralDelegate(
                    preferencesUseCases = preferencesUseCases,
                    authUseCases = authUseCases,
                    errorHandler = errorHandler,
                    detectVaultReconciliationStateUseCase = FakeDetectVaultReconciliationStateUseCase(),
                    adoptCloudVaultUseCase = FakeAdoptCloudVaultUseCase(),
                    bootstrapCloudVaultFromLocalUseCase = FakeBootstrapCloudVaultFromLocalUseCase(),
                    createVaultUseCase = FakeCreateVaultUseCase(),
                    unlockVaultFromStoredMetadataUseCase = FakeUnlockVaultFromStoredMetadataUseCase(),
                    startCloudSyncListeningUseCase = FakeStartCloudSyncListeningUseCase(),
                    cryptoManager = com.browntowndev.enclave.core.testing.FakeCryptoManagerPort(),
                    vaultMetadataPort = com.browntowndev.enclave.core.testing.FakeVaultMetadataPort(),
                    cloudSyncPort = com.browntowndev.enclave.core.testing.FakeCloudSyncPort(),
                )
            sheetVisibility = MutableStateFlow(SheetVisibilityState())
        }

        // S1-01
        @Test
        fun `onShowOssLicensesSheet true sets ossLicenses flag`() {
            delegate.onShowOssLicensesSheet(sheetVisibility, true)
            assertEquals(true, sheetVisibility.value.ossLicenses)
        }

        // S1-02
        @Test
        fun `onShowOssLicensesSheet false resets ossLicenses flag`() {
            sheetVisibility.value = SheetVisibilityState(ossLicenses = true)
            delegate.onShowOssLicensesSheet(sheetVisibility, false)
            assertEquals(false, sheetVisibility.value.ossLicenses)
        }

        // S1-03
        @Test
        fun `onShowOssLicensesSheet does not disturb other sheet flags`() {
            sheetVisibility.value = SheetVisibilityState(feedback = true, memories = true)
            delegate.onShowOssLicensesSheet(sheetVisibility, true)
            assertEquals(true, sheetVisibility.value.feedback)
            assertEquals(true, sheetVisibility.value.memories)
            assertEquals(true, sheetVisibility.value.ossLicenses)
        }
    }

    // ── Group 2: SettingsUiStateFactory propagation ────────────────────────

    @Nested
    inner class FactoryPropagation {
        private lateinit var factory: SettingsUiStateFactory

        @BeforeEach
        fun setup() {
            factory =
                buildSettingsUiStateFactory(
                    localModelAssetUiMapper = LocalModelAssetUiMapper(),
                    apiModelAssetUiMapper = ApiModelAssetUiMapper(),
                    ttsProviderAssetUiMapper = TtsProviderAssetUiMapper(),
                    mediaProviderAssetUiMapper = MediaProviderAssetUiMapper(),
                    apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
                    applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
                    usageSpendUiMapper =
                        com.browntowndev.enclave.feature.settings.usage
                            .UsageSpendUiMapper(),
                )
        }

        // S2-01
        @Test
        fun `isOssLicensesSheetOpen is false when SheetVisibilityState ossLicenses is false`() {
            val result = factory.createMinimal(SheetVisibilityState(ossLicenses = false))
            assertFalse(result.home.isOssLicensesSheetOpen)
        }

        // S2-02
        @Test
        fun `isOssLicensesSheetOpen is true when SheetVisibilityState ossLicenses is true`() {
            val result = factory.createMinimal(SheetVisibilityState(ossLicenses = true))
            assertTrue(result.home.isOssLicensesSheetOpen)
        }
    }

    // ── Group 3: SettingsViewModel end-to-end ─────────────────────────────

    @Nested
    inner class ViewModelEndToEnd {
        private lateinit var viewModel: SettingsViewModel

        @BeforeEach
        fun setup() {
            viewModel = buildMinimalViewModel()
        }

        // S3-01
        @Test
        fun `onShowOssLicensesSheet true reflects in uiState`() =
            runTest {
                viewModel.onShowOssLicensesSheet(true)
                assertEquals(true, viewModel.uiState.value.home.isOssLicensesSheetOpen)
            }

        // S3-02
        @Test
        fun `onShowOssLicensesSheet false resets flag after being true`() =
            runTest {
                viewModel.onShowOssLicensesSheet(true)
                viewModel.onShowOssLicensesSheet(false)
                assertEquals(false, viewModel.uiState.value.home.isOssLicensesSheetOpen)
            }

        // S3-03
        @Test
        fun `onShowOssLicensesSheet does not disturb isFeedbackSheetOpen`() =
            runTest {
                viewModel.onShowFeedbackSheet(true)
                viewModel.onShowOssLicensesSheet(true)
                assertEquals(true, viewModel.uiState.value.home.isFeedbackSheetOpen)
                assertEquals(true, viewModel.uiState.value.home.isOssLicensesSheetOpen)
            }
    }

    // ── Group 4: OssLicenses static data contract ─────────────────────────

    @Nested
    inner class StaticDataContract {
        // S4-01
        @Test
        fun `OssLicenses entries is non-empty`() {
            assertTrue(OssLicenses.entries.isNotEmpty())
        }

        // S4-02
        @Test
        fun `all OssLicenseEntry fields are non-blank`() {
            OssLicenses.entries.forEach { entry ->
                assertTrue(entry.name.isNotBlank(), "name blank for: $entry")
                assertTrue(entry.version.isNotBlank(), "version blank for: $entry")
                assertTrue(entry.licenseType.isNotBlank(), "licenseType blank for: $entry")
                assertTrue(entry.copyrightHolder.isNotBlank(), "copyrightHolder blank for: $entry")
                assertTrue(entry.licenseText.isNotBlank(), "licenseText blank for: $entry")
            }
        }

        // S4-03
        @Test
        fun `OssLicenses entries are sorted alphabetically by name`() {
            val names = OssLicenses.entries.map { it.name }
            assertEquals(names.sortedBy { it.lowercase() }, names)
        }

        // S4-04
        @Test
        fun `OssLicenses entries have unique names`() {
            val names = OssLicenses.entries.map { it.name }
            assertEquals(names.size, names.distinct().size)
        }

        // S4-05
        @Test
        fun `OssLicenses contains all mandatory attributed libraries`() {
            val names = OssLicenses.entries.map { it.name }.toSet()
            val expected = setOf(
                "Android Jetpack Activity",
                "Android Jetpack Biometric",
                "Android Jetpack Compose",
                "Android Jetpack Core KTX",
                "Android Jetpack Core SplashScreen",
                "Android Jetpack Credentials",
                "Android Jetpack DataStore Preferences",
                "Android Jetpack Hilt Navigation Compose",
                "Android Jetpack Hilt Work",
                "Android Jetpack Lifecycle",
                "Android Jetpack Media3",
                "Android Jetpack Navigation Compose",
                "Android Jetpack Room",
                "Android Jetpack WorkManager",
                "Anthropic Java SDK",
                "Coil 3",
                "CommonMark Java",
                "Enclave diffusion engine",
                "Firebase Android SDK",
                "Google GenAI SDK",
                "Google Identity (googleid)",
                "gRPC",
                "Haze",
                "Hilt (Dagger)",
                "javax.inject",
                "JNA (Java Native Access)",
                "jtokkit",
                "Kotlin",
                "kotlinx-serialization",
                "LiteRT (TFLite)",
                "llama.cpp",
                "markdown-parser & markdown-renderer",
                "Model: Falconsai NSFW Image Detection",
                "Model: Gemma 4 E4B",
                "Model: Jamba Reasoning 3B",
                "Model: KoalaAI Text-Moderation",
                "Model: MiniCPM-V-4.6",
                "Model: Qwen 3.5 2B",
                "Model: Sentence Transformers (all-MiniLM-L6-v2)",
                "Model: Stable Diffusion 1.5",
                "Model: Stable Diffusion 1.5 NPU pack",
                "Model: Stable Diffusion XL NPU pack",
                "Model: Whisper (ggml-base.en)",
                "OkHttp",
                "OpenAI Java SDK",
                "Qualcomm AI Runtime (QNN)",
                "stb_image_write",
                "Telephoto",
                "uniffi-kotlin-runtime",
                "whisper.cpp",
            )
            val missing = expected - names
            assertTrue(missing.isEmpty(), "OssLicenses is missing required attributions: $missing")
            val unregistered = names - expected
            assertTrue(unregistered.isEmpty(), "OssLicenses has entries not registered in the test: $unregistered")
        }

        // S4-06
        @Test
        fun `JNA entry documents dual-license origin`() {
            val jna = OssLicenses.entries.first { it.name == "JNA (Java Native Access)" }
            assertTrue(jna.licenseType.contains("Apache 2.0"), "JNA licenseType must mention Apache 2.0")
            assertTrue(jna.licenseType.contains("LGPL"), "JNA licenseType must disclose dual-license origin")
            assertTrue(jna.licenseText.contains("Apache"), "JNA licenseText must reference Apache license")
        }

        // S4-07
        @Test
        fun `all MIT entries include copyright in licenseText`() {
            OssLicenses.entries
                .filter { it.licenseType == "MIT" }
                .forEach { entry ->
                    assertTrue(
                        entry.licenseText.contains("Copyright"),
                        "MIT entry '${entry.name}' must include Copyright in licenseText",
                    )
                }
        }

        // S4-08
        @Test
        fun `all BSD entries include copyright in licenseText`() {
            OssLicenses.entries
                .filter { it.licenseType.startsWith("BSD") }
                .forEach { entry ->
                    assertTrue(
                        entry.licenseText.contains("Copyright"),
                        "BSD entry '${entry.name}' must include Copyright in licenseText",
                    )
                }
        }

        // S4-09
        @Test
        fun `Enclave diffusion engine entry discloses Apache license`() {
            val engine = OssLicenses.entries.first { it.name == "Enclave diffusion engine" }
            assertTrue(engine.licenseType.contains("Apache"))
            assertTrue(engine.licenseText.contains("Apache License", ignoreCase = true))
        }

        // S4-10
        @Test
        fun `QNN entry discloses proprietary Qualcomm terms`() {
            val qnn = OssLicenses.entries.first { it.name == "Qualcomm AI Runtime (QNN)" }
            assertTrue(qnn.licenseType.contains("Proprietary"))
            assertTrue(qnn.licenseText.contains("Qualcomm"))
            assertTrue(qnn.licenseText.contains("QAIRT"))
        }
    }
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

/**
 * Minimal factory.create() call for G2 tests that only care about the
 * isOssLicensesSheetOpen field.
 */
private fun SettingsUiStateFactory.createMinimal(sheetVisibility: SheetVisibilityState): SettingsUiState =
    create(
        persistedSettings = SettingsData(),
        localAssets = emptyList(),
        apiAssets = emptyList(),
        ttsAssets = emptyList(),
        mediaAssets = emptyList(),
        defaultModels = emptyList(),
        authState = AuthState.Loading,
        sheetVisibility = sheetVisibility,
        memories = emptyList(),
        feedbackText = "",
        localModelsState = LocalModelsTransientState(),
        apiState = ApiProvidersTransientState(),
        ttsState = TtsProvidersTransientState(),
        mediaState = MediaProvidersTransientState(),
        searchSkillState = SearchSkillTransientState(),
        assignmentsState = AssignmentDialogTransientState(),
        deletionState = DeletionTransientState(),
        memoriesState = MemoriesTransientState(),
        dataControlsState = DataControlsTransientState(),
        currentTavilyApiKey = "",
        currentExaApiKey = "",
        currentXApiKey = "",
        xSearchState = XSearchTransientState(),
        isReindexing = false,
        reindexProgress = 0f,
        vaultReauthSheet = VaultReauthSheetState.Hidden,
        usageSpendSummary =
            com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary(
                period = com.browntowndev.enclave.domain.model.telemetry.UsagePeriod.LIFETIME,
                periodStartUtcMs = null,
                periodEndUtcMs = null,
                totalCostUsd = java.math.BigDecimal.ZERO,
                telemetryBackedTurnCount = 0,
                missingCostTurnCount = 0,
                providerBreakdown = emptyList(),
            ),
    )

/**
 * Builds a minimal but real SettingsViewModel identical to the pattern
 * used in SettingsViewModelTest.setup() — enough to exercise the G3
 * onShowOssLicensesSheet / onShowFeedbackSheet wiring.
 */
private fun buildMinimalViewModel(): SettingsViewModel {
    val errorHandler = FakeViewModelErrorHandler()

    val getSettingsUseCase = FakeGetSettingsUseCase()
    val getLocalModelAssetsUseCase = FakeGetLocalModelAssetsUseCase()
    val getApiModelAssetsUseCase = FakeGetApiModelAssetsUseCase()
    val getDefaultModelsUseCase = FakeGetDefaultModelsUseCase()
    val getTtsProvidersUseCase = FakeGetTtsProvidersUseCase()
    val getMediaProvidersUseCase = FakeGetMediaProvidersUseCase()
    val memoriesRepository = com.browntowndev.enclave.core.testing.FakeMemoriesRepository()
    val getAuthStateUseCase = FakeGetAuthStateUseCase()
    val updateSearchProviderUseCase = FakeUpdateSearchProviderUseCase()
    val saveExaApiKeyUseCase = FakeSaveExaApiKeyUseCase()
    val clearExaApiKeyUseCase = FakeClearExaApiKeyUseCase()
    val updateShowSearchProviderSwitchAlertUseCase = FakeUpdateShowSearchProviderSwitchAlertUseCase()
    val observeActiveUtilityModelsUseCase = FakeObserveActiveUtilityModelsUseCase()

    val settingsUseCases =
        SettingsUseCasesImpl(
            preferences =
                SettingsPreferencesUseCasesImpl(
                    getSettings = getSettingsUseCase,
                    updateTheme = FakeUpdateThemeUseCase(),
                    updateHapticResponse = FakeUpdateHapticResponseUseCase(),
                    updateCustomizationEnabled = FakeUpdateCustomizationEnabledUseCase(),
                    updateSelectedPromptOption = FakeUpdateSelectedPromptOptionUseCase(),
                    updateCustomPromptText = FakeUpdateCustomPromptTextUseCase(),
                    updateAllowMemories = FakeUpdateAllowMemoriesUseCase(),
                    updateSearchEnabled = FakeUpdateSearchEnabledUseCase(),
                    updateSearchProvider = updateSearchProviderUseCase,
                    saveTavilyApiKey = FakeSaveTavilyApiKeyUseCase(),
                    clearTavilyApiKey = FakeClearTavilyApiKeyUseCase(),
                    saveExaApiKey = saveExaApiKeyUseCase,
                    clearExaApiKey = clearExaApiKeyUseCase,
                    updateXSearchEnabled = FakeUpdateXSearchEnabledUseCase(),
                    saveXApiKey = FakeSaveXApiKeyUseCase(),
                    clearXApiKey = FakeClearXApiKeyUseCase(),
                    updateShowSearchProviderSwitchAlert = updateShowSearchProviderSwitchAlertUseCase,
                    updateBackgroundInferenceEnabled = FakeUpdateBackgroundInferenceEnabledUseCase(),
                    updateAutomaticErrorTrackingEnabled = FakeUpdateAutomaticErrorTrackingEnabledUseCase(),
                ),
            localModels =
                SettingsLocalModelUseCasesImpl(
                    getLocalModelAssets = getLocalModelAssetsUseCase,
                    getRestorableLocalModels = FakeGetRestorableLocalModelsUseCase(),
                    saveLocalModelPreset = FakeSaveLocalModelPresetUseCase(),
                    reDownloadModel = FakeReDownloadModelUseCase(),
                    getModelPacksForSettings = FakeGetModelPacksForSettingsUseCase(),
                    observeActiveUtilityModels = observeActiveUtilityModelsUseCase,
                    reDownloadModelPack = FakeReDownloadModelPackUseCase(),
                    deleteModelPack = FakeDeleteModelPackUseCase(),
                ),
            apiProviders =
                SettingsApiProviderUseCasesImpl(
                    getApiModelAssets = getApiModelAssetsUseCase,
                    saveApiProviderDraft = FakeSaveApiProviderDraftUseCase(),
                    saveApiPreset = FakeSaveApiPresetUseCase(),
                    discoverApiModels = FakeDiscoverApiModelsUseCase(),
                    applyApiModelMetadataDefaults = ApplyApiModelMetadataDefaultsUseCase(),
                    testApiProviderConnection = FakeTestApiProviderConnectionUseCase(),
                    fetchOpenRouterModelEndpoints = FakeFetchOpenRouterModelEndpointsUseCase(),
                ),
            assignments =
                SettingsAssignmentUseCasesImpl(
                    getDefaultModels = getDefaultModelsUseCase,
                    setDefaultModel = FakeSetDefaultModelUseCase(),
                    clearDefaultModel = FakeClearDefaultModelUseCase(),
                    resolveAssignedModelSelection = FakeResolveAssignedModelSelectionUseCase(),
                ),
            deletion =
                SettingsDeletionUseCasesImpl(
                    prepareModelDeletion = FakePrepareModelDeletionUseCase(),
                    executeModelDeletionWithReassignment = FakeExecuteModelDeletionWithReassignmentUseCase(),
                ),
            tts =
                SettingsTtsUseCasesImpl(
                    getTtsProviders = getTtsProvidersUseCase,
                    saveTtsProvider = FakeSaveTtsProviderUseCase(),
                    deleteTtsProvider = FakeDeleteTtsProviderUseCase(),
                ),
            media =
                SettingsMediaUseCasesImpl(
                    getMediaProviders = getMediaProvidersUseCase,
                    saveMediaProvider = FakeSaveMediaProviderUseCase(),
                    deleteMediaProvider = FakeDeleteMediaProviderUseCase(),
                ),
            auth =
                SettingsAuthUseCasesImpl(
                    loginWithGoogle = FakeLoginWithGoogleUseCase(),
                    logout = FakeLogoutUseCase(),
                    getAuthState = getAuthStateUseCase,
                ),
            memories = SettingsMemoriesUseCasesImpl(memoriesRepository),
            dataControls =
                SettingsDataControlsUseCasesImpl(
                    deleteAllChats = FakeDeleteAllChatsUseCase(),
                    deleteAllMedia = FakeDeleteAllMediaUseCase(),
                    deleteAllMemories = FakeDeleteAllMemoriesUseCase(),
                    deleteAllArtifacts = FakeDeleteAllArtifactsUseCase(),
                    deleteAccount = FakeDeleteAccountUseCase(),
                ),
        )

    val embeddingProgressPort = com.browntowndev.enclave.core.testing.FakeEmbeddingProgressPort()

    val observeDownloadWorkUseCase =
        com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase(
            com.browntowndev.enclave.core.testing.download.FakeDownloadWorkObserverPort(),
        )

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

    val localModelAssetUiMapper = LocalModelAssetUiMapper()
    val apiModelAssetUiMapper = ApiModelAssetUiMapper()
    val ttsProviderAssetUiMapper = TtsProviderAssetUiMapper()
    val mediaProviderAssetUiMapper = MediaProviderAssetUiMapper()
    val reassignmentOptionUiMapper = ReassignmentOptionUiMapper()
    val deletionHelper = SettingsDeletionHelper(settingsUseCases.deletion, reassignmentOptionUiMapper, errorHandler)

    return SettingsViewModel(
        settingsUseCases = settingsUseCases,
        settingsUiStateFactory =
            buildSettingsUiStateFactory(
                localModelAssetUiMapper,
                apiModelAssetUiMapper,
                ttsProviderAssetUiMapper,
                mediaProviderAssetUiMapper,
                ApiDiscoveryUiFilter(),
                ApplyApiModelMetadataDefaultsUseCase(),
                com.browntowndev.enclave.feature.settings.usage
                    .UsageSpendUiMapper(),
            ),
        observeEntitlementStateUseCase = ObserveEntitlementStateUseCase(FakeEntitlementPort()),
        observeUsageSpendSummaryUseCase =
            com.browntowndev.enclave.feature.settings
                .testUsageSpendSummaryUseCase(),
        generalDelegate = generalDelegate,
        memoriesDelegate = SettingsMemoriesDelegate(settingsUseCases.preferences, settingsUseCases.memories, errorHandler),
        dataControlsDelegate = SettingsDataControlsDelegate(settingsUseCases.dataControls, settingsUseCases.preferences, errorHandler),
        localModelsDelegate =
            SettingsLocalModelsDelegate(
                settingsUseCases.localModels,
                deletionHelper,
                errorHandler,
            ),
        apiProvidersDelegate =
            createSettingsApiProvidersDelegate(
                apiProviderUseCases = settingsUseCases.apiProviders,
                ttsUseCases = settingsUseCases.tts,
                liveVoiceUseCases = settingsUseCases.liveVoice,
                mediaUseCases = settingsUseCases.media,
                deletionHelper = deletionHelper,
                apiModelAssetUiMapper = apiModelAssetUiMapper,
                errorHandler = errorHandler,
            ),
        searchDelegate =
            SettingsSearchDelegate(
                settingsUseCases.preferences,
                com.browntowndev.enclave.core.testing.FakeCloudSyncPort(),
                errorHandler,
            ),
        assignmentsDelegate = SettingsAssignmentsDelegate(settingsUseCases.assignments, errorHandler),
        deletionHelper = deletionHelper,
        errorHandler = errorHandler,
        observeEmbeddingProgress = com.browntowndev.enclave.domain.usecase.settings.ObserveEmbeddingProgressUseCase(embeddingProgressPort),
        observeDownloadWorkUseCase = com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase(com.browntowndev.enclave.core.testing.download.FakeDownloadWorkObserverPort()),
                observeThemePreferencesUseCase = ObserveThemePreferencesUseCase(FakeThemePreferencesRepository()),
                setThemeFamilyUseCase = SetThemeFamilyUseCase(FakeThemePreferencesRepository()),
    )
}
