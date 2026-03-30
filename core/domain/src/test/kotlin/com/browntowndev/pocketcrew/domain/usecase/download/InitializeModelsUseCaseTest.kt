package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
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
            displayName = "Test Model",
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
        val existingModels = mapOf(
            ModelType.FAST to createFakeLocalModelAsset(
                displayName = "Existing Model",
                configurations = listOf(createFakeLocalModelConfiguration())
            )
        )
        // getAssetsPreferringOld is a suspend function
        coEvery { mockModelRegistry.getAssetsPreferringOld() } returns existingModels
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.failure(Exception("Network error"))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        val result = useCase.invoke()

        // Then - should still return empty (graceful degradation using existing models)
        assert(result.modelsToDownload.isEmpty())
    }

    @Test
    fun `invoke does NOT mark existing config as OLD when SHA256 is unchanged but tunings changed`() = runTest {
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

        // Registry has existing CURRENT config
        coEvery { mockModelRegistry.getAssetsPreferringOld() } returns mapOf(ModelType.MAIN to existingConfig)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        useCase.invoke()

        // Then - setRegisteredModel is called with markExistingAsOld=false
        // because SHA256 is unchanged - the file is still valid
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(ModelType.MAIN, remoteConfig, ModelStatus.CURRENT, markExistingAsOld = false)
        }
    }

    @Test
    fun `invoke calls setRegisteredModel for remote config regardless of SHA256 changes`() = runTest {
        // Given - remote config has different SHA256 (new model file)
        val existingConfig = createFakeLocalModelAsset(
            displayName = "Old Model",
            sha256 = "oldSha256",
            localFileName = "old-model.bin"
        )

        val remoteConfig = createFakeLocalModelAsset(
            displayName = "Old Model",
            sha256 = "newSha256", // Different SHA256 = new file
            localFileName = "new-model.bin",
            remoteFileName = "new-model.bin"
        )

        val downloadResult = DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = listOf(remoteConfig), // New SHA256 = needs download
            scanResult = ModelScanResult(
                missingModels = listOf(remoteConfig),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        // Registry has existing CURRENT config
        coEvery { mockModelRegistry.getAssetsPreferringOld() } returns mapOf(ModelType.MAIN to existingConfig)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then - setRegisteredModel is called with the new config
        // The repository internally handles marking the old one as OLD
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(ModelType.MAIN, remoteConfig, ModelStatus.CURRENT)
        }
    }

    /**
     * VERIFIED FIX:
     * When SHA256 is unchanged but tunings change, the use case now correctly
     * passes markExistingAsOld=false to prevent creating an OLD entry.
     * This prevents the file cleanup logic from deleting valid model files.
     */
    @Test
    fun `FIXED - when SHA256 unchanged, markExistingAsOld is false to prevent file deletion`() = runTest {
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

        // Registry has existing CURRENT config
        coEvery { mockModelRegistry.getAssetsPreferringOld() } returns mapOf(ModelType.MAIN to existingConfig)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.MAIN to remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When - invoke the use case
        useCase.invoke()

        // Verify: markExistingAsOld should be false because SHA256 is unchanged
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(ModelType.MAIN, remoteConfig, ModelStatus.CURRENT, markExistingAsOld = false)
        }
    }

    /**
     * BUG FIX TEST:
     * When DRAFT_ONE's SHA256 changes from "abc" to "xyz", but FAST still uses SHA256 "abc",
     * the old DRAFT_ONE config should NOT be marked as OLD because the file is still in use by FAST.
     * Only mark as OLD if the SHA256 changed AND no other model type uses that SHA256.
     */
    @Test
    fun `invoke does NOT mark existing config as OLD when SHA256 changed but other model still uses that SHA256`() = runTest {
        // Given - Current registry: DRAFT_ONE and FAST both use SHA256 "sharedSha256" (same file)
        val existingDraftOne = createFakeLocalModelAsset(
            displayName = "Draft One",
            sha256 = "sharedSha256",
            localFileName = "shared.bin"
        )

        val existingFast = createFakeLocalModelAsset(
            displayName = "Fast",
            sha256 = "sharedSha256",
            localFileName = "shared.bin"
        )

        // Remote config: DRAFT_ONE changed to new SHA256, FAST still uses same SHA256
        val remoteDraftOne = existingDraftOne.copy(
            metadata = existingDraftOne.metadata.copy(
                sha256 = "newSha256ForDraftOne",  // SHA256 changed!
                localFileName = "new-draft-one.bin",
                remoteFileName = "new-draft-one.bin",
                displayName = "Draft One Updated"
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
        coEvery { mockModelRegistry.getAssetsPreferringOld() } returns mapOf(ModelType.DRAFT_ONE to existingDraftOne, ModelType.FAST to existingFast)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.DRAFT_ONE to remoteDraftOne, ModelType.FAST to remoteFast))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then - DRAFT_ONE's old config should NOT be marked as OLD because FAST still uses that SHA256
        // The fix: markExistingAsOld should be FALSE for DRAFT_ONE since FAST still references "sharedSha256"
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(
                ModelType.DRAFT_ONE,
                remoteDraftOne,
                ModelStatus.CURRENT,
                markExistingAsOld = false  // NOT true! Because FAST still uses sharedSha256
            )
        }
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(
                ModelType.FAST,
                remoteFast,
                ModelStatus.CURRENT,
                markExistingAsOld = false  // SHA256 unchanged
            )
        }
    }

    /**
     * TEST: When SHA256 changes AND no other model uses that SHA256, mark as OLD (current behavior for single models)
     */
    @Test
    fun `invoke marks existing config as OLD when SHA256 changed and no other model uses that SHA256`() = runTest {
        // Given - Current registry: only DRAFT_ONE exists with SHA256 "oldSha256"
        val existingDraftOne = createFakeLocalModelAsset(
            displayName = "Draft One",
            sha256 = "oldSha256",
            localFileName = "old.bin"
        )

        // Remote config: DRAFT_ONE changed to new SHA256
        val remoteDraftOne = createFakeLocalModelAsset(
            displayName = "Draft One Updated",
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
        coEvery { mockModelRegistry.getAssetsPreferringOld() } returns mapOf(ModelType.DRAFT_ONE to existingDraftOne)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(mapOf(ModelType.DRAFT_ONE to remoteDraftOne))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then - DRAFT_ONE's old config SHOULD be marked as OLD because no other model uses oldSha256
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(
                ModelType.DRAFT_ONE,
                remoteDraftOne,
                ModelStatus.CURRENT,
                markExistingAsOld = true  // TRUE! No other model uses oldSha256
            )
        }
    }
}
