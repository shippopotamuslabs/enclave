package com.browntowndev.enclave.core.data.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadProgressUpdate
import com.browntowndev.enclave.domain.model.download.DownloadRequestKind
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.model.download.DownloadWorkRequest
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.DeviceEnvironmentRepositoryPort
import com.browntowndev.enclave.domain.usecase.download.InitializeFileProgressUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ModelDownloadOrchestratorImplTest {
    private val sessionManager = mockk<DownloadSessionManager>()
    private val deviceEnvironment = mockk<DeviceEnvironmentRepositoryPort>()
    private val workScheduler = mockk<DownloadWorkScheduler>(relaxed = true)
    private val logger = mockk<LoggingPort>(relaxed = true)
    private val speedTracker = mockk<DownloadSpeedTrackerPort>(relaxed = true)
    private val initializeFileProgress = InitializeFileProgressUseCase()

    private lateinit var orchestrator: ModelDownloadOrchestratorImpl

    private val testAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("model-1"),
                    huggingFaceModelName = "org/model",
                    remoteFileName = "model.bin",
                    localFileName = "model.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1_000L,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations = emptyList(),
        )

    private val modelsResult =
        DownloadModelsResult(
            allModels = mapOf(ModelType.FAST to testAsset),
            modelsToDownload = listOf(testAsset),
            scanResult =
                ModelScanResult(
                    missingModels = listOf(testAsset),
                    partialDownloads = emptyMap(),
                    allValid = true,
                ),
        )

    @BeforeEach
    fun setUp() {
        orchestrator =
            ModelDownloadOrchestratorImpl(
                sessionManager = sessionManager,
                deviceEnvironmentRepository = deviceEnvironment,
                initializeFileProgress = initializeFileProgress,
                workScheduler = workScheduler,
                logger = logger,
                speedTracker = speedTracker,
            )
        every { sessionManager.createNewSession() } returnsMany listOf("session-1", "session-2")
        coEvery { deviceEnvironment.hasRequiredStorage(any()) } returns true
    }

    @Test
    @DisplayName("initializeWithStartupResult leaves IDLE until startDownloads when models are missing")
    fun initializeWithStartupResult_idleUntilStart() {
        orchestrator.initializeWithStartupResult(modelsResult)

        assertEquals(DownloadStatus.IDLE, orchestrator.downloadState.value.status)
        verify(exactly = 0) { workScheduler.enqueue(any()) }
    }

    @Test
    @DisplayName("startDownloads after init enqueues one session and sets DOWNLOADING")
    fun startDownloads_enqueuesOnceAndSetsDownloading() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)

            val enqueued = slot<DownloadWorkRequest>()
            every { workScheduler.enqueue(capture(enqueued)) } returns Unit

            val started = orchestrator.startDownloads(wifiOnly = true)

            assertTrue(started)
            assertEquals(DownloadStatus.DOWNLOADING, orchestrator.downloadState.value.status)
            verify(exactly = 1) { workScheduler.enqueue(any()) }
            assertEquals("session-1", enqueued.captured.sessionId)
            assertEquals(DownloadRequestKind.INITIALIZE_MODELS, enqueued.captured.requestKind)
            assertTrue(enqueued.captured.wifiOnly)
        }

    @Test
    @DisplayName("second startDownloads uses startup result without requiring re-init")
    fun startDownloads_usesCachedStartupResult() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            orchestrator.startDownloads(wifiOnly = true)

            val startedAgain = orchestrator.startDownloads(wifiOnly = true)

            assertTrue(startedAgain)
            verify(exactly = 2) { workScheduler.enqueue(any()) }
            verify(exactly = 2) { sessionManager.createNewSession() }
        }

    @Test
    @DisplayName("initializeWithStartupResult sets READY when nothing to download")
    fun initializeWithStartupResult_readyWhenEmpty() {
        val emptyResult =
            modelsResult.copy(
                modelsToDownload = emptyList(),
                scanResult = modelsResult.scanResult.copy(missingModels = emptyList()),
            )

        orchestrator.initializeWithStartupResult(emptyResult)

        assertEquals(DownloadStatus.READY, orchestrator.downloadState.value.status)
    }

    @Test
    @DisplayName("D2: updateFromProgressUpdate with READY terminal state updates downloadState")
    fun updateFromProgressUpdate_readyTerminal_setsReadyStatus() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            orchestrator.startDownloads(wifiOnly = true)

            orchestrator.updateFromProgressUpdate(
                DownloadProgressUpdate(
                    status = DownloadStatus.READY,
                    overallProgress = 1f,
                    modelsComplete = 1,
                    modelsTotal = 1,
                ),
            )

            assertEquals(DownloadStatus.READY, orchestrator.downloadState.value.status)
            assertEquals(1f, orchestrator.downloadState.value.overallProgress)
            assertEquals(1, orchestrator.downloadState.value.modelsComplete)
            assertEquals(1, orchestrator.downloadState.value.modelsTotal)
        }

    @Test
    @DisplayName("D3: pauseDownloads cancels work and sets PAUSED")
    fun pauseDownloads_cancelsWorkAndSetsPaused() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            orchestrator.startDownloads(wifiOnly = true)

            orchestrator.pauseDownloads()

            assertEquals(DownloadStatus.PAUSED, orchestrator.downloadState.value.status)
            verify(exactly = 1) { workScheduler.cancel() }
        }

    @Test
    @DisplayName("D3: cancelDownloads cancels work, cleans temp files, and resets to IDLE")
    fun cancelDownloads_cancelsCleansUpAndResetsIdle() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            orchestrator.startDownloads(wifiOnly = true)

            orchestrator.cancelDownloads()

            assertEquals(DownloadStatus.IDLE, orchestrator.downloadState.value.status)
            assertTrue(orchestrator.downloadState.value.currentDownloads.isEmpty())
            assertEquals(0f, orchestrator.downloadState.value.overallProgress)
            assertEquals(0, orchestrator.downloadState.value.modelsComplete)
            verify(exactly = 1) { workScheduler.cancel() }
            coVerify(exactly = 1) { workScheduler.cleanupTempFiles() }
            verify(atLeast = 1) { speedTracker.clearAll() }
        }

    @Test
    @DisplayName("D3: resumeDownloads re-enqueues after pause using stored wifiOnly preference")
    fun resumeDownloads_reenqueuesAfterPause() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            orchestrator.startDownloads(wifiOnly = true)
            orchestrator.pauseDownloads()

            orchestrator.resumeDownloads()

            assertEquals(DownloadStatus.DOWNLOADING, orchestrator.downloadState.value.status)
            verify(exactly = 2) { workScheduler.enqueue(any()) }
            verify(exactly = 2) { sessionManager.createNewSession() }
        }

    @Test
    @DisplayName("D4: hasRequiredStorage false sets ERROR and does not enqueue")
    fun startDownloads_insufficientStorage_setsErrorWithoutEnqueue() =
        runTest {
            coEvery { deviceEnvironment.hasRequiredStorage(any()) } returns false
            orchestrator.initializeWithStartupResult(modelsResult)

            val started = orchestrator.startDownloads(wifiOnly = true)

            assertFalse(started)
            assertEquals(DownloadStatus.ERROR, orchestrator.downloadState.value.status)
            assertEquals(
                "Insufficient storage space. Need at least 15 GB free.",
                orchestrator.downloadState.value.errorMessage,
            )
            verify(exactly = 0) { workScheduler.enqueue(any()) }
        }
}
