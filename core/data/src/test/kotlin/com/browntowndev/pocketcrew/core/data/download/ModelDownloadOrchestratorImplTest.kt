package com.browntowndev.pocketcrew.core.data.download

import org.junit.jupiter.api.extension.ExtendWith
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ModelDownloadOrchestratorImplTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockSessionManager: DownloadSessionManager
    private lateinit var mockValidateConditions: com.browntowndev.pocketcrew.domain.usecase.download.ValidateDownloadConditionsUseCase
    private lateinit var mockInitializeFileProgress: com.browntowndev.pocketcrew.domain.usecase.download.InitializeFileProgressUseCase
    private lateinit var mockWorkScheduler: DownloadWorkScheduler
    private lateinit var mockProgressParser: WorkProgressParser
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockLogger: LoggingPort
    private lateinit var mockSpeedTracker: DownloadSpeedTrackerPort

    private lateinit var orchestrator: ModelDownloadOrchestratorImpl

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockSessionManager = mockk(relaxed = true)
        mockValidateConditions = mockk(relaxed = true)
        mockInitializeFileProgress = mockk(relaxed = true)
        mockWorkScheduler = mockk(relaxed = true)
        mockProgressParser = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockSpeedTracker = mockk(relaxed = true)

        // Mock context.getExternalFilesDir to return our temp dir
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        orchestrator = ModelDownloadOrchestratorImpl(
            context = mockContext,
            sessionManager = mockSessionManager,
            validateConditions = mockValidateConditions,
            initializeFileProgress = mockInitializeFileProgress,
            workScheduler = mockWorkScheduler,
            progressParser = mockProgressParser,
            modelRegistry = mockModelRegistry,
            logger = mockLogger,
            speedTracker = mockSpeedTracker
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createModelConfig(
        modelType: ModelType = ModelType.MAIN,
        sha256: String = "testSha256",
        localFileName: String = "test.bin"
    ): ModelConfiguration {
        return ModelConfiguration(
            modelType = modelType,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = localFileName,
                localFileName = localFileName,
                displayName = "Test Model",
                sha256 = sha256,
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
    }

    private fun createDownloadModelsResult(modelsToDownload: List<ModelConfiguration> = emptyList()): DownloadModelsResult {
        return DownloadModelsResult(
            modelsToDownload = modelsToDownload,
            scanResult = ModelScanResult(
                missingModels = modelsToDownload,
                partialDownloads = emptyMap(),
                allValid = modelsToDownload.isEmpty()
            )
        )
    }

    @Test
    fun `retryFailed sets error state when startupModelsResult is null`() = runTest {
        // Given - startupModelsResult is null (not initialized)
        // When - retryFailed is called
        orchestrator.retryFailed()

        // Then - should set error state instead of crashing
        verify {
            mockLogger.warning(any<String>(), any<String>())
        }
        // Should NOT call sessionManager.createNewSession (which would crash)
    }

    @Test
    fun `downloadOnMobileData sets error state when startupModelsResult is null`() = runTest {
        // Given - startupModelsResult is null (not initialized)
        // When - downloadOnMobileData is called
        orchestrator.downloadOnMobileData()

        // Then - should set error state instead of crashing
        verify {
            mockLogger.warning(any<String>(), any<String>())
        }
    }

    @Test
    fun `initializeWithStartupResult sets startupModelsResult`() = runTest {
        // Given
        val modelConfig = createModelConfig()
        val downloadResult = createDownloadModelsResult(listOf(modelConfig))

        // When
        orchestrator.initializeWithStartupResult(downloadResult)

        // Then - the orchestrator should be initialized
        // We can verify this by checking that startDownloads would work without throwing
        // IllegalStateException (which would happen if startupModelsResult was null)
        coEvery { mockSessionManager.createNewSession() } returns "test-session"
        coEvery { mockValidateConditions.invoke(any(), any()) } returns mockk {
            every { canStart } returns true
        }
        coEvery { mockInitializeFileProgress.invoke(any(), any(), any()) } returns com.browntowndev.pocketcrew.domain.usecase.download.FileProgressInitResult(
            fileProgressList = emptyList(),
            modelsTotal = 0,
            modelsComplete = 0,
            overallProgress = 0f
        )

        // This should not throw IllegalStateException - proves startupModelsResult is set
        // It may return false due to other mocks, but shouldn't throw
        try {
            orchestrator.startDownloads(wifiOnly = false)
        } catch (e: IllegalStateException) {
            throw AssertionError("startupModelsResult should be set, but got: ${e.message}")
        }
    }
}
