package com.browntowndev.enclave.core.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.browntowndev.enclave.core.testing.FakeDeviceEnvironmentRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
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
import com.browntowndev.enclave.domain.usecase.download.InitializeFileProgressUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.Executor

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ModelDownloadOrchestratorImplTest {
    /** Recording [DownloadWorkScheduler] subclass fake (ENC-28 open seam). */
    private class FakeDownloadWorkScheduler(
        context: Context,
        workManager: WorkManager,
    ) : DownloadWorkScheduler(context, workManager) {
        val enqueuedRequests = mutableListOf<DownloadWorkRequest>()
        var enqueueFailure: Throwable? = null
        var cancelCalls = 0
            private set
        var cleanupCalls = 0
            private set

        override fun enqueue(request: DownloadWorkRequest) {
            enqueueFailure?.let { throw it }
            enqueuedRequests += request
        }

        override fun cancel() {
            cancelCalls++
        }

        override suspend fun cleanupTempFiles() {
            cleanupCalls++
        }
    }

    /** Minimal [DownloadSpeedTrackerPort] fake (ENC-28) recording clearAll. */
    private class FakeDownloadSpeedTracker : DownloadSpeedTrackerPort {
        var clearAllCalls = 0
            private set

        override fun calculateSpeedAndEta(
            filename: String,
            bytesDownloaded: Long,
            totalSize: Long,
        ): Pair<Double, Long> = Pair(0.0, 0L)

        override fun calculateAggregateSpeedAndEta(
            totalBytesDownloaded: Long,
            totalSize: Long,
        ): Pair<Double, Long> = Pair(0.0, 0L)

        override fun formatEta(seconds: Long): String = ""

        override fun clear(filename: String) = Unit

        override fun clearAll() {
            clearAllCalls++
        }
    }

    private lateinit var sessionManager: DownloadSessionManager
    private val deviceEnvironment = FakeDeviceEnvironmentRepository()
    private lateinit var workScheduler: FakeDownloadWorkScheduler
    private val logger = FakeLoggingPort()
    private val speedTracker = FakeDownloadSpeedTracker()
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .setExecutor(Executor { })
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        sessionManager = DownloadSessionManager(context)
        workScheduler = FakeDownloadWorkScheduler(context, WorkManager.getInstance(context))
        orchestrator =
            ModelDownloadOrchestratorImpl(
                sessionManager = sessionManager,
                deviceEnvironmentRepository = deviceEnvironment,
                initializeFileProgress = initializeFileProgress,
                workScheduler = workScheduler,
                logger = logger,
                speedTracker = speedTracker,
            )
    }

    @Test
    @DisplayName("initializeWithStartupResult leaves IDLE until startDownloads when models are missing")
    fun initializeWithStartupResult_idleUntilStart() {
        sessionManager.createNewSession() // an active session exists before init

        orchestrator.initializeWithStartupResult(modelsResult)

        assertEquals(DownloadStatus.IDLE, orchestrator.downloadState.value.status)
        assertEquals(1, workScheduler.cancelCalls)
        // clearSession ran: the previously active session is gone
        assertNull(sessionManager.currentSessionId)
        assertEquals(0, workScheduler.enqueuedRequests.size)
    }

    @Test
    @DisplayName("startDownloads surfaces ERROR when WorkManager payload is too large")
    fun startDownloads_payloadTooLarge_setsError() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            workScheduler.enqueueFailure = DownloadWorkPayloadTooLargeException(fileSpecCount = 30)

            val started = orchestrator.startDownloads(wifiOnly = true)

            assertFalse(started)
            assertEquals(DownloadStatus.ERROR, orchestrator.downloadState.value.status)
            assertTrue(
                orchestrator.downloadState.value.errorMessage
                    .orEmpty()
                    .contains("Too many models"),
            )
        }

    @Test
    @DisplayName("startDownloads after init enqueues one session and sets DOWNLOADING")
    fun startDownloads_enqueuesOnceAndSetsDownloading() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)

            val started = orchestrator.startDownloads(wifiOnly = true)

            assertTrue(started)
            assertEquals(DownloadStatus.DOWNLOADING, orchestrator.downloadState.value.status)
            assertEquals(1, workScheduler.enqueuedRequests.size)
            val enqueued = workScheduler.enqueuedRequests.single()
            assertEquals(sessionManager.currentSessionId, enqueued.sessionId)
            assertEquals(DownloadRequestKind.INITIALIZE_MODELS, enqueued.requestKind)
            assertTrue(enqueued.wifiOnly)
        }

    @Test
    @DisplayName("second startDownloads uses startup result without requiring re-init")
    fun startDownloads_usesCachedStartupResult() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            orchestrator.startDownloads(wifiOnly = true)

            val startedAgain = orchestrator.startDownloads(wifiOnly = true)

            assertTrue(startedAgain)
            assertEquals(2, workScheduler.enqueuedRequests.size)
            // Each start created a fresh session (createNewSession called twice)
            assertNotEquals(
                workScheduler.enqueuedRequests[0].sessionId,
                workScheduler.enqueuedRequests[1].sessionId,
            )
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
            assertEquals(2, workScheduler.cancelCalls)
        }

    @Test
    @DisplayName("D3: cancelDownloads cancels work, cleans temp files, and resets to IDLE")
    fun cancelDownloads_cancelsCleansUpAndResetsIdle() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)
            orchestrator.startDownloads(wifiOnly = true)

            orchestrator.cancelDownloads()

            assertEquals(DownloadStatus.IDLE, orchestrator.downloadState.value.status)
            assertTrue(
                orchestrator.downloadState.value.currentDownloads
                    .isEmpty(),
            )
            assertEquals(0f, orchestrator.downloadState.value.overallProgress)
            assertEquals(0, orchestrator.downloadState.value.modelsComplete)
            assertEquals(2, workScheduler.cancelCalls)
            assertEquals(1, workScheduler.cleanupCalls)
            assertTrue(speedTracker.clearAllCalls >= 1)
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
            assertEquals(2, workScheduler.enqueuedRequests.size)
            // Resume created a second session (createNewSession called twice)
            assertNotEquals(
                workScheduler.enqueuedRequests[0].sessionId,
                workScheduler.enqueuedRequests[1].sessionId,
            )
        }

    @Test
    @DisplayName("retryFailed preserves wifiOnly preference from initial startDownloads")
    fun retryFailed_preservesWifiOnlyPreference() =
        runTest {
            orchestrator.initializeWithStartupResult(modelsResult)

            orchestrator.startDownloads(wifiOnly = true)
            orchestrator.retryFailed()

            assertEquals(2, workScheduler.enqueuedRequests.size)
            assertTrue(workScheduler.enqueuedRequests.last().wifiOnly)
        }

    @Test
    @DisplayName("D4: hasRequiredStorage false sets ERROR and does not enqueue")
    fun startDownloads_insufficientStorage_setsErrorWithoutEnqueue() =
        runTest {
            deviceEnvironment.hasStorage = false
            orchestrator.initializeWithStartupResult(modelsResult)

            val started = orchestrator.startDownloads(wifiOnly = true)

            assertFalse(started)
            assertEquals(DownloadStatus.ERROR, orchestrator.downloadState.value.status)
            assertEquals(
                "Insufficient storage space. Need at least 15 GB free.",
                orchestrator.downloadState.value.errorMessage,
            )
            assertEquals(0, workScheduler.enqueuedRequests.size)
        }

    @Test
    @DisplayName("startDownloads(modelsResult) refreshes cached startup result so resume uses the filtered set")
    fun startDownloads_refreshesCachedStartupResultForResume() =
        runTest {
            // Orchestrator is initialized at cold start with the unfiltered result (LLM + diffusion).
            val llmAsset =
                LocalModelAsset(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("llm-1"),
                            huggingFaceModelName = "org/llm",
                            remoteFileName = "llm.bin",
                            localFileName = "llm.bin",
                            sha256 = "llm-sha",
                            sizeInBytes = 2_000L,
                            modelFileFormat = ModelFileFormat.GGUF,
                        ),
                    configurations = emptyList(),
                )
            val unfilteredResult =
                DownloadModelsResult(
                    allModels = mapOf(ModelType.FAST to testAsset, ModelType.MAIN to llmAsset),
                    modelsToDownload = listOf(testAsset, llmAsset),
                    scanResult =
                        ModelScanResult(
                            missingModels = listOf(testAsset, llmAsset),
                            partialDownloads = emptyMap(),
                            allValid = true,
                        ),
                )
            orchestrator.initializeWithStartupResult(unfilteredResult)

            // API onboarding filters the LLM out before the first startDownloads call.
            val filteredResult =
                unfilteredResult.copy(
                    allModels = mapOf(ModelType.FAST to testAsset),
                    modelsToDownload = listOf(testAsset),
                    scanResult =
                        ModelScanResult(
                            missingModels = listOf(testAsset),
                            partialDownloads = emptyMap(),
                            allValid = true,
                        ),
                )
            val enqueued = workScheduler.enqueuedRequests

            orchestrator.startDownloads(modelsResult = filteredResult, wifiOnly = true)
            // Pause, then resume — resume falls back to the cached startupModelsResult.
            orchestrator.pauseDownloads()
            orchestrator.resumeDownloads()

            // Two enqueue calls (initial + resume); neither should reference the suppressed LLM.
            assertEquals(2, enqueued.size)
            enqueued.forEach { request ->
                val shas = request.files.map { it.sha256 }.toSet()
                assertFalse("resume must not re-enqueue suppressed LLM" in shas.toString()) {
                    "Suppressed LLM sha256 leaked into enqueued files: $shas"
                }
                assertTrue("llm-sha" !in shas)
            }
        }
}
