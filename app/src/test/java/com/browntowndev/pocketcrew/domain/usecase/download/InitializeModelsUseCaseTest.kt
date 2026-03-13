package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
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
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyList())
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        val result = useCase.invoke()

        // Then - should return the result (empty models to download)
        assert(result.modelsToDownload.isEmpty())
    }

    @Test
    fun `invoke fetches remote config and returns models to download when needed`() = runTest {
        // Given - mock returns a model that needs downloading
        val modelToDownload = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "model.bin",
                localFileName = "model.bin",
                displayName = "Test Model",
                sha256 = "abc123",
                sizeInBytes = 1024,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                repetitionPenalty = 1.0,
                maxTokens = 2048,
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val downloadResult = DownloadModelsResult(
            modelsToDownload = listOf(modelToDownload),
            scanResult = ModelScanResult(
                missingModels = listOf(modelToDownload),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyList())
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        val result = useCase.invoke()

        // Then - should return the model that needs downloading
        assert(result.modelsToDownload.isNotEmpty())
        assert(result.modelsToDownload.first().modelType == ModelType.MAIN)
    }

    @Test
    fun `invoke initializes orchestrator with result`() = runTest {
        // Given
        val expectedResult = DownloadModelsResult(
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(emptyList())
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
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        // Mock existing models in registry (this triggers the fallback path)
        val existingModels = listOf(
            ModelConfiguration(
                modelType = ModelType.FAST,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "existing/model",
                    remoteFileName = "existing.bin",
                    localFileName = "existing.bin",
                    displayName = "Existing Model",
                    sha256 = "existing123",
                    sizeInBytes = 1024,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                tunings = ModelConfiguration.Tunings(
                    temperature = 0.0,
                    topK = 40,
                    topP = 0.95,
                    repetitionPenalty = 1.0,
                    maxTokens = 2048,
                    contextWindow = 2048
                ),
                persona = ModelConfiguration.Persona(
                    systemPrompt = "You are a helpful assistant."
                )
            )
        )
        // getModelsPreferringOld is a suspend function
        coEvery { mockModelRegistry.getModelsPreferringOld() } returns existingModels
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
        val existingConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "model.bin",
                localFileName = "model.bin",
                displayName = "Test Model",
                sha256 = "sameSha256", // Same SHA256
                sizeInBytes = 1024,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                repetitionPenalty = 1.0,
                maxTokens = 2048,
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val remoteConfig = existingConfig.copy(
            tunings = existingConfig.tunings.copy(temperature = 0.5) // Different tuning
        )

        val emptyResult = DownloadModelsResult(
            modelsToDownload = emptyList(), // Same SHA256, so no download needed
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        // Registry has existing CURRENT config
        coEvery { mockModelRegistry.getModelsPreferringOld() } returns listOf(existingConfig)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        useCase.invoke()

        // Then - setRegisteredModel is called with markExistingAsOld=false
        // because SHA256 is unchanged - the file is still valid
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(remoteConfig, ModelStatus.CURRENT, markExistingAsOld = false)
        }
    }

    @Test
    fun `invoke calls setRegisteredModel for remote config regardless of SHA256 changes`() = runTest {
        // Given - remote config has different SHA256 (new model file)
        val existingConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "old-model.bin",
                localFileName = "old-model.bin",
                displayName = "Old Model",
                sha256 = "oldSha256",
                sizeInBytes = 1024,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                repetitionPenalty = 1.0,
                maxTokens = 2048,
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val remoteConfig = existingConfig.copy(
            metadata = existingConfig.metadata.copy(
                sha256 = "newSha256", // Different SHA256 = new file
                localFileName = "new-model.bin",
                remoteFileName = "new-model.bin"
            )
        )

        val downloadResult = DownloadModelsResult(
            modelsToDownload = listOf(remoteConfig), // New SHA256 = needs download
            scanResult = ModelScanResult(
                missingModels = listOf(remoteConfig),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )

        // Registry has existing CURRENT config
        coEvery { mockModelRegistry.getModelsPreferringOld() } returns listOf(existingConfig)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns downloadResult

        // When
        useCase.invoke()

        // Then - setRegisteredModel is called with the new config
        // The repository internally handles marking the old one as OLD
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(remoteConfig, ModelStatus.CURRENT)
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
        val existingConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "model.bin",
                localFileName = "model.bin",
                displayName = "Test Model",
                sha256 = "sameSha256",
                sizeInBytes = 1024,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                repetitionPenalty = 1.0,
                maxTokens = 2048,
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val remoteConfig = existingConfig.copy(
            tunings = existingConfig.tunings.copy(temperature = 0.7) // Tunings changed
        )

        val emptyResult = DownloadModelsResult(
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        // Registry has existing CURRENT config
        coEvery { mockModelRegistry.getModelsPreferringOld() } returns listOf(existingConfig)
        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.success(listOf(remoteConfig))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When - invoke the use case
        useCase.invoke()

        // Verify: markExistingAsOld should be false because SHA256 is unchanged
        coVerify(exactly = 1) {
            mockModelRegistry.setRegisteredModel(remoteConfig, ModelStatus.CURRENT, markExistingAsOld = false)
        }
    }
}
