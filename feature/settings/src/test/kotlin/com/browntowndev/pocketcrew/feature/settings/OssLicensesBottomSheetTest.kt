package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.repository.EmbeddingProgressPort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import com.browntowndev.pocketcrew.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.DeleteMediaProviderUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ReDownloadModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.*
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsMemoriesUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCases
import io.mockk.every
import io.mockk.mockk
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

        private val preferencesUseCases = mockk<SettingsPreferencesUseCases>(relaxed = true)
        private val authUseCases = mockk<SettingsAuthUseCases>(relaxed = true)
        private val errorHandler = mockk<ViewModelErrorHandler>()
        private lateinit var delegate: SettingsGeneralDelegate
        private lateinit var sheetVisibility: MutableStateFlow<SheetVisibilityState>

        @BeforeEach
        fun setup() {
            every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
                CoroutineExceptionHandler { _, _ -> }
            delegate = SettingsGeneralDelegate(
                preferencesUseCases = preferencesUseCases,
                authUseCases = authUseCases,
                errorHandler = errorHandler,
                detectVaultReconciliationStateUseCase = mockk(relaxed = true),
                adoptCloudVaultUseCase = mockk(relaxed = true),
                bootstrapCloudVaultFromLocalUseCase = mockk(relaxed = true),
                createVaultUseCase = mockk(relaxed = true),
                cryptoManager = mockk(relaxed = true),
                vaultMetadataPort = mockk(relaxed = true),
                cloudSyncPort = mockk(relaxed = true),
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
            factory = SettingsUiStateFactory(
                localModelAssetUiMapper = LocalModelAssetUiMapper(),
                apiModelAssetUiMapper = ApiModelAssetUiMapper(),
                ttsProviderAssetUiMapper = TtsProviderAssetUiMapper(),
                mediaProviderAssetUiMapper = mockk(relaxed = true),
                apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
                applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
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
        fun `onShowOssLicensesSheet true reflects in uiState`() = runTest {
            viewModel.onShowOssLicensesSheet(true)
            assertEquals(true, viewModel.uiState.value.home.isOssLicensesSheetOpen)
        }

        // S3-02
        @Test
        fun `onShowOssLicensesSheet false resets flag after being true`() = runTest {
            viewModel.onShowOssLicensesSheet(true)
            viewModel.onShowOssLicensesSheet(false)
            assertEquals(false, viewModel.uiState.value.home.isOssLicensesSheetOpen)
        }

        // S3-03
        @Test
        fun `onShowOssLicensesSheet does not disturb isFeedbackSheetOpen`() = runTest {
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
            listOf(
                "OkHttp",
                "Kotlin",
                "JNA (Java Native Access)",
                "CommonMark Java",
                "jtokkit",
                "Anthropic Java SDK",
                "Firebase Android SDK",
            ).forEach { expected ->
                assertTrue(expected in names, "Missing required library: $expected")
            }
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
    }
}

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

/**
 * Minimal factory.create() call for G2 tests that only care about the
 * isOssLicensesSheetOpen field.
 */
private fun SettingsUiStateFactory.createMinimal(
    sheetVisibility: SheetVisibilityState,
): SettingsUiState = create(
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
    currentTavilyApiKey = "",
    currentExaApiKey = "",
    currentXApiKey = "",
    xSearchState = XSearchTransientState(),
    isReindexing = false,
    reindexProgress = 0f,
    vaultReauthSheet = VaultReauthSheetState.Hidden,
)

/**
 * Builds a minimal but real SettingsViewModel identical to the pattern
 * used in SettingsViewModelTest.setup() — enough to exercise the G3
 * onShowOssLicensesSheet / onShowFeedbackSheet wiring.
 */
private fun buildMinimalViewModel(): SettingsViewModel {
    val errorHandler = mockk<ViewModelErrorHandler>()
    every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
        CoroutineExceptionHandler { _, _ -> }

    val getSettingsUseCase = mockk<GetSettingsUseCase>()
    every { getSettingsUseCase() } returns MutableStateFlow(SettingsData())

    val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
    every { getLocalModelAssetsUseCase() } returns MutableStateFlow(emptyList())

    val getApiModelAssetsUseCase = mockk<GetApiModelAssetsUseCase>()
    every { getApiModelAssetsUseCase() } returns MutableStateFlow(emptyList())

    val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
    every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())

    val getTtsProvidersUseCase = mockk<GetTtsProvidersUseCase>()
    every { getTtsProvidersUseCase() } returns MutableStateFlow(emptyList())

    val getMediaProvidersUseCase = mockk<GetMediaProvidersUseCase>()
    every { getMediaProvidersUseCase() } returns MutableStateFlow(emptyList())

    val memoriesRepository = mockk<MemoriesRepository>()
    every { memoriesRepository.getAllMemoriesFlow() } returns MutableStateFlow(emptyList())

    val getAuthStateUseCase = mockk<GetAuthStateUseCase>(relaxed = true)
    every { getAuthStateUseCase() } returns MutableStateFlow(AuthState.Loading)

    val updateSearchProviderUseCase = mockk<UpdateSearchProviderUseCase>(relaxed = true)
    val saveExaApiKeyUseCase = mockk<SaveExaApiKeyUseCase>(relaxed = true)
    val clearExaApiKeyUseCase = mockk<ClearExaApiKeyUseCase>(relaxed = true)
    val updateShowSearchProviderSwitchAlertUseCase = mockk<UpdateShowSearchProviderSwitchAlertUseCase>(relaxed = true)

    val settingsUseCases = SettingsUseCasesImpl(
        preferences = SettingsPreferencesUseCasesImpl(
            getSettings = getSettingsUseCase,
            updateTheme = mockk(relaxed = true),
            updateHapticPress = mockk(relaxed = true),
            updateHapticResponse = mockk(relaxed = true),
            updateCustomizationEnabled = mockk(relaxed = true),
            updateSelectedPromptOption = mockk(relaxed = true),
            updateCustomPromptText = mockk(relaxed = true),
            updateAllowMemories = mockk(relaxed = true),
            updateSearchEnabled = mockk(relaxed = true),
            updateSearchProvider = updateSearchProviderUseCase,
            saveTavilyApiKey = mockk(relaxed = true),
            clearTavilyApiKey = mockk(relaxed = true),
            saveExaApiKey = saveExaApiKeyUseCase,
            clearExaApiKey = clearExaApiKeyUseCase,
            updateXSearchEnabled = mockk(relaxed = true),
            saveXApiKey = mockk(relaxed = true),
            clearXApiKey = mockk(relaxed = true),
            updateShowSearchProviderSwitchAlert = updateShowSearchProviderSwitchAlertUseCase,
            updateBackgroundInferenceEnabled = mockk(relaxed = true),
        ),
        localModels = SettingsLocalModelUseCasesImpl(
            getLocalModelAssets = getLocalModelAssetsUseCase,
            getRestorableLocalModels = mockk(relaxed = true),
            saveLocalModelPreset = mockk(relaxed = true),
            reDownloadModel = mockk<ReDownloadModelUseCase>(relaxed = true),
        ),
        apiProviders = SettingsApiProviderUseCasesImpl(
            getApiModelAssets = getApiModelAssetsUseCase,
            saveApiProviderDraft = mockk(relaxed = true),
            saveApiPreset = mockk(relaxed = true),
            discoverApiModels = mockk(relaxed = true),
            applyApiModelMetadataDefaults = mockk(relaxed = true),
        ),
        assignments = SettingsAssignmentUseCasesImpl(
            getDefaultModels = getDefaultModelsUseCase,
            setDefaultModel = mockk(relaxed = true),
            clearDefaultModel = mockk(relaxed = true),
            resolveAssignedModelSelection = mockk(relaxed = true),
        ),
        deletion = SettingsDeletionUseCasesImpl(
            prepareModelDeletion = mockk(relaxed = true),
            executeModelDeletionWithReassignment = mockk(relaxed = true),
        ),
        tts = SettingsTtsUseCasesImpl(
            getTtsProviders = getTtsProvidersUseCase,
            saveTtsProvider = mockk(relaxed = true),
            deleteTtsProvider = mockk(relaxed = true),
        ),
        media = SettingsMediaUseCasesImpl(
            getMediaProviders = getMediaProvidersUseCase,
            saveMediaProvider = mockk<SaveMediaProviderUseCase>(relaxed = true),
            deleteMediaProvider = mockk<DeleteMediaProviderUseCase>(relaxed = true),
        ),
        auth = SettingsAuthUseCasesImpl(
            loginWithGoogle = mockk(relaxed = true),
            logout = mockk(relaxed = true),
            getAuthState = getAuthStateUseCase,
        ),
        memories = SettingsMemoriesUseCasesImpl(memoriesRepository),
    )

    val embeddingProgressPort = mockk<EmbeddingProgressPort>()
    every { embeddingProgressPort.isReindexing } returns MutableStateFlow(false)
    every { embeddingProgressPort.progress } returns MutableStateFlow(0f)

    val detectVaultReconciliationStateUseCase = mockk<DetectVaultReconciliationStateUseCase>(relaxed = true)
    val adoptCloudVaultUseCase = mockk<AdoptCloudVaultUseCase>(relaxed = true)
    val bootstrapCloudVaultFromLocalUseCase = mockk<BootstrapCloudVaultFromLocalUseCase>(relaxed = true)
    val createVaultUseCase = mockk<CreateVaultUseCase>(relaxed = true)
    val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    val vaultMetadataPort = mockk<VaultMetadataPort>(relaxed = true)
    val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)

    val generalDelegate = SettingsGeneralDelegate(
        settingsUseCases.preferences,
        settingsUseCases.auth,
        errorHandler,
        detectVaultReconciliationStateUseCase,
        adoptCloudVaultUseCase,
        bootstrapCloudVaultFromLocalUseCase,
        createVaultUseCase,
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
        settingsUiStateFactory = SettingsUiStateFactory(
            localModelAssetUiMapper,
            apiModelAssetUiMapper,
            ttsProviderAssetUiMapper,
            mediaProviderAssetUiMapper,
            ApiDiscoveryUiFilter(),
            ApplyApiModelMetadataDefaultsUseCase(),
        ),
        generalDelegate = generalDelegate,
        memoriesDelegate = SettingsMemoriesDelegate(settingsUseCases.preferences, settingsUseCases.memories, errorHandler),
        localModelsDelegate = SettingsLocalModelsDelegate(settingsUseCases.localModels, deletionHelper, localModelAssetUiMapper, errorHandler),
        apiProvidersDelegate = SettingsApiProvidersDelegate(
            settingsUseCases.apiProviders,
            settingsUseCases.tts,
            settingsUseCases.media,
            deletionHelper,
            apiModelAssetUiMapper,
            ttsProviderAssetUiMapper,
            errorHandler,
        ),
        searchDelegate = SettingsSearchDelegate(settingsUseCases.preferences, errorHandler),
        assignmentsDelegate = SettingsAssignmentsDelegate(settingsUseCases.assignments, errorHandler),
        deletionHelper = deletionHelper,
        errorHandler = errorHandler,
        embeddingProgressPort = embeddingProgressPort,
    )
}
