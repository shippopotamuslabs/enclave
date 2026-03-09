package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
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
        // Given - fetch fails but we should still return empty result
        val emptyResult = DownloadModelsResult(
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        coEvery { mockModelConfigFetcher.fetchRemoteConfig() } returns Result.failure(Exception("Network error"))
        coEvery { mockCheckModelsUseCase.invoke(any(), any()) } returns emptyResult

        // When
        val result = useCase.invoke()

        // Then - should still return empty (graceful degradation)
        assert(result.modelsToDownload.isEmpty())
    }
}
