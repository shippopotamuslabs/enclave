package com.browntowndev.pocketcrew.core.data.download
import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadCheckResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.download.FileProgressInitResult
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeFileProgressUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.ValidateDownloadConditionsUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.CheckModelEligibilityUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir


@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadOrchestratorImplTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherRule(testDispatcher)



    private lateinit var mockContext: Context
    private lateinit var mockSessionManager: DownloadSessionManager
    private lateinit var mockValidateConditions: ValidateDownloadConditionsUseCase
    private lateinit var mockInitializeFileProgress: InitializeFileProgressUseCase
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

    private fun createModelAsset(
        sha256: String = "testSha256",
        localFileName: String = "test.bin"
    ): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "test/model",
                remoteFileName = localFileName,
                localFileName = localFileName,
                sha256 = sha256,
                sizeInBytes = 1024,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    localModelId = 0,
                    displayName = "Test Model",
                    maxTokens = 2048,
                    contextWindow = 2048,
                    temperature = 0.0,
                    topP = 0.95,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    systemPrompt = "You are a helpful assistant."
                )
            )
        )
    }

    private fun createDownloadModelsResult(
        modelsToDownload: List<LocalModelAsset> = emptyList(),
        allModels: Map<ModelType, LocalModelAsset> = emptyMap()
    ): DownloadModelsResult {
        return DownloadModelsResult(
            allModels = allModels,
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
    fun `updateFromProgressUpdate READY status triggers registry update for new assets`() = runTest {
        // Given - initialized with a new remote model
        val remoteAsset = createModelAsset(sha256 = "new-sha")
        val downloadResult = createDownloadModelsResult(
            modelsToDownload = listOf(remoteAsset),
            allModels = mapOf(ModelType.FAST to remoteAsset)
        )
        orchestrator.initializeWithStartupResult(downloadResult)

        // When - download completes successfully
        val update = com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate(
            status = com.browntowndev.pocketcrew.domain.model.download.DownloadStatus.READY,
            clearSession = true
        )
        orchestrator.updateFromProgressUpdate(update)

        // Then - registry should activate the new asset atomically
        coVerify {
            mockModelRegistry.activateLocalModel(ModelType.FAST, remoteAsset)
        }
    }

    @Test
    fun `updateFromProgressUpdate ERROR status emits fallback snackbar if local model exists`() = runTest {
        // Given - initialized with a model that has an existing local version
        val remoteAsset = createModelAsset(sha256 = "new-sha")
        val localAsset = createModelAsset(sha256 = "old-sha")
        val downloadResult = createDownloadModelsResult(
            modelsToDownload = listOf(remoteAsset),
            allModels = mapOf(ModelType.FAST to remoteAsset)
        )
        orchestrator.initializeWithStartupResult(downloadResult)

        // Mock that there's a local model for FAST in the registry
        coEvery { mockModelRegistry.getRegisteredAsset(ModelType.FAST) } returns localAsset

        // When - download fails
        val update = com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate(
            status = com.browntowndev.pocketcrew.domain.model.download.DownloadStatus.ERROR,
            clearSession = true
        )
        
        val messages = mutableListOf<String>()
        val collectJob = backgroundScope.launch {
            orchestrator.snackbarMessages.collect { messages.add(it) }
        }
        
        orchestrator.updateFromProgressUpdate(update)
        
        // Wait for the message to appear
        var attempts = 0
        while (messages.isEmpty() && attempts < 10) {
            testDispatcher.scheduler.advanceTimeBy(100)
            testDispatcher.scheduler.runCurrent()
            attempts++
        }

        // Then - should emit a fallback snackbar
        assertTrue(messages.any { it.contains("Using existing model") })
    }

    @Test
    fun `startDownloads enqueues every shared slot for a single physical asset`() = runTest {
        val sharedSha = "shared-sha"
        val sharedFileName = "gemma-4-E4B-it.litertlm"
        val visionAsset = createModelAsset(sha256 = sharedSha, localFileName = sharedFileName).copy(
            configurations = listOf(
                createModelAsset(sha256 = sharedSha, localFileName = sharedFileName)
                    .configurations.first()
                    .copy(displayName = "Gemma 4 E4B (Vision)")
            )
        )
        val fastAsset = createModelAsset(sha256 = sharedSha, localFileName = sharedFileName).copy(
            configurations = listOf(
                createModelAsset(sha256 = sharedSha, localFileName = sharedFileName)
                    .configurations.first()
                    .copy(displayName = "Gemma 4 E4B (Fast)")
            )
        )
        val thinkingAsset = createModelAsset(sha256 = sharedSha, localFileName = sharedFileName).copy(
            configurations = listOf(
                createModelAsset(sha256 = sharedSha, localFileName = sharedFileName)
                    .configurations.first()
                    .copy(displayName = "Gemma 4 E4B (Thinking)", thinkingEnabled = true)
            )
        )

        val downloadResult = createDownloadModelsResult(
            modelsToDownload = listOf(visionAsset),
            allModels = mapOf(
                ModelType.VISION to visionAsset,
                ModelType.FAST to fastAsset,
                ModelType.THINKING to thinkingAsset
            )
        )
        every { mockSessionManager.createNewSession() } returns "session-1"
        coEvery { mockValidateConditions.invoke(any(), any()) } returns DownloadCheckResult(
            canStart = true,
            errorMessage = null,
            missingModels = listOf(visionAsset)
        )
        every { mockInitializeFileProgress.invoke(any(), any(), any()) } returns FileProgressInitResult(
            fileProgressList = emptyList(),
            modelsTotal = 3,
            modelsComplete = 0,
            overallProgress = 0f
        )

        orchestrator.startDownloads(downloadResult, wifiOnly = true)

        verify {
            mockWorkScheduler.enqueue(
                match { models ->
                    models.keys == setOf(ModelType.VISION, ModelType.FAST, ModelType.THINKING)
                },
                "session-1",
                true
            )
        }
    }
}
