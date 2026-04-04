package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.download.CheckModelEligibilityUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeFileProgressUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.ValidateDownloadConditionsUseCase
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadOrchestratorSharedAssetTest {

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

    @Test
    fun `updateModelRegistry activates ALL model types sharing the same downloaded asset`() = runTest {
        // Given - initialized with a model asset shared by VISION, FAST, and THINKING
        val sharedAsset = createModelAsset(sha256 = "shared-sha")
        val thinkingAsset = sharedAsset.copy(
            configurations = listOf(
                sharedAsset.configurations.first().copy(
                    displayName = "Gemma 4 E4B (Thinking)",
                    thinkingEnabled = true,
                    systemPrompt = "thinking"
                )
            )
        )
        val downloadResult = DownloadModelsResult(
            allModels = mapOf(
                ModelType.VISION to sharedAsset,
                ModelType.FAST to sharedAsset,
                ModelType.THINKING to thinkingAsset
            ),
            modelsToDownload = listOf(sharedAsset),
            scanResult = ModelScanResult(
                missingModels = listOf(sharedAsset),
                partialDownloads = emptyMap(),
                allValid = false
            )
        )
        orchestrator.initializeWithStartupResult(downloadResult)

        // When - download completes successfully
        val update = com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate(
            status = com.browntowndev.pocketcrew.domain.model.download.DownloadStatus.READY,
            clearSession = true
        )
        orchestrator.updateFromProgressUpdate(update)

        // Then - registry should activate the shared asset for EVERY role
        coVerify {
            mockModelRegistry.activateLocalModel(ModelType.VISION, sharedAsset)
        }
        coVerify {
            mockModelRegistry.activateLocalModel(ModelType.FAST, sharedAsset)
        }
        coVerify {
            mockModelRegistry.activateLocalModel(ModelType.THINKING, thinkingAsset)
        }
    }
}
