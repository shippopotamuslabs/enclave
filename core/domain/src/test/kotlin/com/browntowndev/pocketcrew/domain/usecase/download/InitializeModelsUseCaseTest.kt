package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.testing.createFakeLocalModelAsset
import com.browntowndev.pocketcrew.testing.createFakeLocalModelConfiguration
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InitializeModelsUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockModelConfigFetcher: ModelConfigFetcherPort
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockModelDownloadOrchestrator: ModelDownloadOrchestratorPort
    private lateinit var mockCheckModelsUseCase: CheckModelsUseCase
    private lateinit var mockLogPort: LoggingPort

    private lateinit var useCase: InitializeModelsUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        mockModelConfigFetcher = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockModelDownloadOrchestrator = mockk(relaxed = true)
        mockCheckModelsUseCase = mockk(relaxed = true)
        mockLogPort = mockk(relaxed = true)

        useCase = InitializeModelsUseCase(
            modelConfigFetcher = mockModelConfigFetcher,
            modelRegistry = mockModelRegistry,
            modelDownloadOrchestrator = mockModelDownloadOrchestrator,
            checkModelsUseCase = mockCheckModelsUseCase,
            logPort = mockLogPort
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `invoke fetches remote config and checks models when no remote models`() = runTest {
        // Given - mock returns empty list (all models ready)
        val emptyResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyMap())
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        val result = useCase.invoke()

        // Then - should return the result (empty models to download)
        assert(result.modelsToDownload.isEmpty())
    }

    @Test
    fun `invoke fetches remote config and returns models to download when needed`() = runTest {
        // Given - mock returns a model that needs downloading
        val modelToDownload = createFakeLocalModelAsset(
            configurations = listOf(createFakeLocalModelConfiguration())
        )

        val downloadResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = listOf(modelToDownload),
            scanResult = ModelScanResult(
                missingModels = listOf(modelToDownload),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyMap())
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        val result = useCase.invoke()

        // Then - should return the model that needs downloading
        assert(result.modelsToDownload.isNotEmpty())
    }

    @Test
    fun `invoke initializes orchestrator with result`() = runTest {
        // Given
        val expectedResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyMap())
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns expectedResult

        // When
        useCase.invoke()

        // Then - should initialize the orchestrator with the result
        verify {
            mockModelDownloadOrchestrator.initializeWithStartupResult(expectedResult)
        }
    }

    @Test
    fun `invoke handles remote config fetch failure gracefully`() = runTest {
        // Given - fetch fails but we have existing models in registry
        // Should gracefully fallback to existing models
        val emptyResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        // Mock existing models in registry (this triggers the fallback path)
        val existingAsset = createFakeLocalModelAsset(
            configurations = listOf(createFakeLocalModelConfiguration())
        )
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns existingAsset
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.failure(Exception("Network error"))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        val result = useCase.invoke()

        // Then - should still return empty (graceful degradation using existing models)
        assert(result.modelsToDownload.isEmpty())
    }

    @Test
    fun `invoke activates existing config in place when SHA256 is unchanged but tunings changed`() = runTest {
        // Given - remote config has same SHA256 but different temperature (tuning change only)
        val existingConfig = createFakeLocalModelAsset(
            sha256 = "sameSha256",
            configurations = listOf(createFakeLocalModelConfiguration(temperature = 0.0))
        )

        val remoteConfig = existingConfig.copy(
            configurations = listOf(existingConfig.configurations.first().copy(temperature = 0.5))
        )

        val emptyResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(), // Same SHA256, so no download needed
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        // Registry has an existing active config
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.MAIN) } returns existingConfig
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        useCase.invoke()

        // Then - activation is applied atomically because SHA256 is unchanged
        coVerify(exactly = 1) {
            mockModelRegistry.activateLocalModel(ModelType.MAIN, remoteConfig)
        }
    }

    @Test
    fun `invoke defers activation when filename changes even if SHA256 is unchanged`() = runTest {
        val existingConfig = createFakeLocalModelAsset(
            sha256 = "sameSha256",
            localFileName = "old-model.bin",
            remoteFileName = "old-model.bin",
            configurations = listOf(createFakeLocalModelConfiguration(temperature = 0.0))
        )

        val remoteConfig = existingConfig.copy(
            metadata = existingConfig.metadata.copy(
                localFileName = "new-model.bin",
                remoteFileName = "new-model.bin"
            )
        )

        val emptyResult = DownloadModelsResult(
            allModels = mapOf(ModelType.MAIN to remoteConfig),
            modelsToDownload = listOf(remoteConfig),
            scanResult = ModelScanResult(
                missingModels = listOf(remoteConfig),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.MAIN) } returns existingConfig
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        useCase.invoke()

        coVerify(exactly = 0) {
            mockModelRegistry.activateLocalModel(ModelType.MAIN, remoteConfig)
        }
    }

    @Test
    fun `invoke activates THINKING immediately when shared asset already exists under FAST`() = runTest {
        val sharedFast = createFakeLocalModelAsset(
            sha256 = "sharedSha256",
            localFileName = "gemma-4-E4B-it.litertlm",
            remoteFileName = "gemma-4-E4B-it.litertlm",
            configurations = listOf(
                createFakeLocalModelConfiguration(
                    displayName = "Gemma 3 2B (Fast)",
                    systemPrompt = "fast"
                )
            )
        )
        val sharedThinking = sharedFast.copy(
            configurations = listOf(
                sharedFast.configurations.first().copy(
                    displayName = "Gemma 3 2B (Thinking)",
                    temperature = 0.05,
                    maxTokens = 6144,
                    thinkingEnabled = true,
                    systemPrompt = "thinking"
                )
            )
        )

        val emptyResult = DownloadModelsResult(
            allModels = mapOf(
                ModelType.FAST to sharedFast,
                ModelType.THINKING to sharedThinking
            ),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns sharedFast
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(
            mapOf(
                ModelType.FAST to sharedFast,
                ModelType.THINKING to sharedThinking
            )
        )
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        useCase.invoke()

        coVerify(exactly = 1) {
            mockModelRegistry.activateLocalModel(ModelType.THINKING, sharedThinking)
        }
    }

    @Test
    fun `invoke does NOT activate changed-SHA assets immediately`() = runTest {
        // Given - remote config has different SHA256 (new model file)
        val existingConfig = createFakeLocalModelAsset(
            sha256 = "oldSha256",
            localFileName = "old-model.bin"
        )

        val remoteConfig = createFakeLocalModelAsset(
            sha256 = "newSha256", // Different SHA256 = new file
            localFileName = "new-model.bin",
            remoteFileName = "new-model.bin"
        )

        val downloadResult = DownloadModelsResult(
            allModels = mapOf(ModelType.MAIN to remoteConfig),
            modelsToDownload = listOf(remoteConfig),
            scanResult = ModelScanResult(
                missingModels = listOf(remoteConfig),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.MAIN) } returns existingConfig
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then - NO updates are called yet (deferred activation)
        coVerify(exactly = 0) {
            mockModelRegistry.upsertLocalAsset(any())
        }
    }

    @Test
    fun `invoke calls activateLocalModel only for unchanged-SHA config-only updates`() = runTest {
        // Given - SHA same, but tuning changed
        val existingConfig = createFakeLocalModelAsset(
            sha256 = "sameSha256",
            configurations = listOf(createFakeLocalModelConfiguration(temperature = 0.0))
        )
        val remoteConfig = existingConfig.copy(
            configurations = listOf(existingConfig.configurations.first().copy(temperature = 0.5))
        )

        val emptyResult = DownloadModelsResult(
            allModels = mapOf(ModelType.MAIN to remoteConfig),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.MAIN) } returns existingConfig
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        useCase.invoke()

        // Then - the slot activation is applied immediately for tuning-only updates
        coVerify(exactly = 1) {
            mockModelRegistry.activateLocalModel(ModelType.MAIN, remoteConfig)
        }
    }

    /**
     * VERIFIED FIX:
     * When SHA256 is unchanged but tunings change, the use case now applies the
     * activation in place instead of staging a replacement row.
     */
    @Test
    fun `FIXED - when SHA256 unchanged, activateLocalModel updates in place`() = runTest {
        // Given - same SHA256 but different temperature
        val existingConfig = createFakeLocalModelAsset(
            sha256 = "sameSha256",
            configurations = listOf(createFakeLocalModelConfiguration(temperature = 0.0))
        )

        val remoteConfig = existingConfig.copy(
            configurations = listOf(existingConfig.configurations.first().copy(temperature = 0.7)) // Tunings changed
        )

        val emptyResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        // Registry has an existing active config
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.MAIN) } returns existingConfig
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When - invoke the use case
        useCase.invoke()

        // Verify: unchanged SHA uses the immediate in-place activation path
        coVerify(exactly = 1) {
            mockModelRegistry.activateLocalModel(ModelType.MAIN, remoteConfig)
        }
    }

    /**
     * BUG FIX TEST:
     * When DRAFT_ONE's SHA256 changes from "abc" to "xyz", but FAST still uses SHA256 "abc",
     * the existing DRAFT_ONE slot should not be disrupted because the file is still in use by FAST.
     */
    @Test
    fun `invoke does NOT mark existing config as OLD when SHA256 changed but other model still uses that SHA256`() = runTest {
        // Given - Current registry: DRAFT_ONE and FAST both use SHA256 "sharedSha256" (same file)
        val existingDraftOne = createFakeLocalModelAsset(
            sha256 = "sharedSha256",
            localFileName = "shared.bin"
        )

        val existingFast = createFakeLocalModelAsset(
            sha256 = "sharedSha256",
            localFileName = "shared.bin"
        )

        // Remote config: DRAFT_ONE changed to new SHA256, FAST still uses same SHA256
        val remoteDraftOne = existingDraftOne.copy(
            metadata = existingDraftOne.metadata.copy(
                sha256 = "newSha256ForDraftOne",  // SHA256 changed!
                localFileName = "new-draft-one.bin",
                remoteFileName = "new-draft-one.bin"
            )
        )

        val remoteFast = existingFast.copy()  // FAST unchanged

        // DRAFT_ONE's new file needs downloading (missing from filesystem)
        // FAST's old file exists (same SHA256 "sharedSha256" still valid)
        // The download list contains only the new DRAFT_ONE config
        val downloadResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = listOf(remoteDraftOne),  // New DRAFT_ONE file missing
            scanResult = ModelScanResult(
                missingModels = listOf(remoteDraftOne),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        // Registry has both models with shared SHA256
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.DRAFT_ONE) } returns existingDraftOne
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns existingFast
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.DRAFT_ONE to remoteDraftOne, ModelType.FAST to remoteFast))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then - DRAFT_ONE should remain untouched because FAST still uses the shared SHA256
        // The unchanged FAST asset should be refreshed atomically without affecting DRAFT_ONE.
        coVerify(exactly = 1) {
            mockModelRegistry.activateLocalModel(ModelType.FAST, remoteFast)
        }
    }

    /**
     * TEST: When SHA256 changes AND no other model uses that SHA256, defer activation until download succeeds.
     */
    @Test
    fun `invoke marks existing config as OLD when SHA256 changed and no other model uses that SHA256`() = runTest {
        // Given - Current registry: only DRAFT_ONE exists with SHA256 "oldSha256"
        val existingDraftOne = createFakeLocalModelAsset(
            sha256 = "oldSha256",
            localFileName = "old.bin"
        )

        // Remote config: DRAFT_ONE changed to new SHA256
        val remoteDraftOne = createFakeLocalModelAsset(
            sha256 = "newSha256", // SHA256 changed!
            localFileName = "new.bin",
            remoteFileName = "new.bin"
        )

        // Download needed for new SHA256
        val downloadResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = listOf(remoteDraftOne),
            scanResult = ModelScanResult(
                missingModels = listOf(remoteDraftOne),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        // Registry has only DRAFT_ONE (no other models)
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.DRAFT_ONE) } returns existingDraftOne
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.DRAFT_ONE to remoteDraftOne))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then - activateLocalModel should NOT be called for changed-SHA immediately
        coVerify(exactly = 0) {
            mockModelRegistry.activateLocalModel(any(), any())
        }
    }

    // ============================================================
    // SOFT-DELETE SCENARIOS (TDD Red - these tests will FAIL
    // against current implementation until feature is implemented)
    // ============================================================

    /**
     * Scenario: InitializeModelsUseCase excludes soft-deleted from scanner
     * Given: LocalModelEntity(id=42) exists with 0 configs (soft-deleted)
     * And: Physical file for model 42 does NOT exist (was deleted)
     * When: InitializeModelsUseCase() is invoked
     * Then: CheckModelsUseCase is NOT called for ModelType.FAST
     * And: FAST is NOT added to modelsToDownload (file is not flagged as missing by scanner)
     * And: Model 42 appears in availableToRedownload
     *
     * TDD Red: This test FAILS against current implementation because:
     * - The use case does not pre-filter soft-deleted models
     * - The use case does not return availableToRedownload field
     * - CheckModelsUseCase would be called for ALL models including soft-deleted
     */
    @Test
    fun `invoke excludes soft-deleted models from CheckModelsUseCase and adds to availableToRedownload`() = runTest {
        // Given - FAST model was soft-deleted (0 configs) but model entity still exists
        val softDeletedModel = createFakeLocalModelAsset(
            sha256 = "abc123softdeleted",
            configurations = emptyList()  // 0 configs = soft-deleted
        )

        val remoteFast = createFakeLocalModelAsset(
            sha256 = "abc123softdeleted",
            configurations = listOf(createFakeLocalModelConfiguration(isSystemPreset = true))
        )

        val emptyResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            ),
            availableToRedownload = emptyList()  // Will be populated by implementation
        )

        // Registry returns the soft-deleted model (it still has an entity row)
        val remoteConfigs = mapOf(ModelType.FAST to remoteFast)
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns softDeletedModel
        coEvery { mockModelRegistry.getSoftDeletedModels() } returns listOf(softDeletedModel)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(remoteConfigs)
        // CheckModelsUseCase should NOT be called for soft-deleted models
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        val result = useCase.invoke()

        // Then: availableToRedownload should contain the soft-deleted model
        assert(result.availableToRedownload.isNotEmpty()) { "availableToRedownload should contain soft-deleted model" }
        assert(result.availableToRedownload.first().metadata.sha256 == "abc123softdeleted") {
            "availableToRedownload should contain the soft-deleted model sha256"
        }

        // And: modelsToDownload should NOT contain the soft-deleted model
        val fastModels = result.modelsToDownload.filter { it.metadata.sha256 == "abc123softdeleted" }
        assert(fastModels.isEmpty()) { "Soft-deleted model should NOT be in modelsToDownload" }

        // VERIFY: CheckModelsUseCase was called WITHOUT the soft-deleted model in expectedModels
        coVerify(exactly = 1) {
            mockCheckModelsUseCase.invoke(
                downloadedModels = mapOf(ModelType.FAST to softDeletedModel),
                expectedModels = emptyMap()
            )
        }
    }

    /**
     * Scenario: Soft-deleted model is REMOVED from remote configuration.
     * Given: FAST model was soft-deleted locally.
     * And: FAST is NOT in the remote configuration.
     * When: InitializeModelsUseCase() is invoked.
     * Then: availableToRedownload should NOT contain FAST.
     */
    @Test
    fun `invoke excludes soft-deleted models from availableToRedownload if not on remote`() = runTest {
        // Given - FAST model was soft-deleted (0 configs)
        val softDeletedModel = createFakeLocalModelAsset(
            sha256 = "deletedOnRemote",
            configurations = emptyList()
        )

        // Remote config ONLY has MAIN (FAST is gone)
        val remoteMain = createFakeLocalModelAsset()
        val remoteConfigs = mapOf(ModelType.MAIN to remoteMain)

        val emptyResult = DownloadModelsResult(
            allModels = remoteConfigs,
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns softDeletedModel
        coEvery { mockModelRegistry.getSoftDeletedModels() } returns listOf(softDeletedModel)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(remoteConfigs)
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        val result = useCase.invoke()

        // Then: availableToRedownload should NOT contain FAST because it's not on remote
        assert(result.availableToRedownload.isEmpty()) { "Should not contain FAST in availableToRedownload" }
    }

    /**
     * Scenario: Remote fetch fails on pristine install.
     * Given: Registry is empty.
     * And: Remote config fetch fails.
     * When: InitializeModelsUseCase() is invoked.
     * Then: It should throw the exception from fetch failure.
     */
    @Test
    fun `invoke throws exception when remote fetch fails on pristine install`() = runTest {
        // Given - Registry is empty
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getSoftDeletedModels() } returns emptyList()
        
        // Remote fetch fails
        val error = Exception("Network unavailable")
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.failure(error)

        // When/Then - Should throw the error
        try {
            useCase.invoke()
            assert(false) { "Should have thrown an exception" }
        } catch (e: Exception) {
            assert(e.message == "Network unavailable")
        }
    }

    /**
     * BUG REPRODUCTION TEST:
     * When there are 0 models in the registry (pristine install),
     * InitializeModelsUseCase should pass ALL remote configs to CheckModelsUseCase
     * so that the initial download is triggered.
     *
     * TDD Red: This test fails against current implementation because:
     * - filteredRemoteConfigs would be empty (since activeModelTypes is empty)
     * - CheckModelsUseCase is called with an empty map for expectedModels
     */
    @Test
    fun `BUG REPRO - invoke passes ALL remote configs even if registry is empty`() = runTest {
        // Given - Registry is empty (pristine install)
        coEvery { mockModelRegistry.getRegisteredAsset(any()) } returns null
        coEvery { mockModelRegistry.getSoftDeletedModels() } returns emptyList()

        // Remote config has one model
        val remoteModel = createFakeLocalModelAsset(
            sha256 = "remoteSha",
            configurations = listOf(createFakeLocalModelConfiguration())
        )
        val remoteConfigs = mapOf(ModelType.FAST to remoteModel)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(remoteConfigs)

        // CheckModelsUseCase would return this model as needing download
        val downloadResult = DownloadModelsResult(
            allModels = remoteConfigs,
            modelsToDownload = listOf(remoteModel),
            scanResult = ModelScanResult(
                missingModels = listOf(remoteModel),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )
        
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then: CheckModelsUseCase should be called with ALL remote configs
        coVerify(exactly = 1) {
            mockCheckModelsUseCase.invoke(
                downloadedModels = emptyMap(),
                expectedModels = remoteConfigs
            )
        }
    }
}
