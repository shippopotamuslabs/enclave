package com.browntowndev.enclave.feature.download

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadProgressUpdate
import com.browntowndev.enclave.domain.model.download.DownloadState
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.core.testing.download.FakeModelDownloadOrchestrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelStartupTest {
    private val orchestrator = FakeModelDownloadOrchestrator()
    private val errorHandler = FakeViewModelErrorHandler()
    private val downloadStateFlow = orchestrator.downloadStateFlow

    private val testAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("model-1"),
                    huggingFaceModelName = "test/model",
                    remoteFileName = "model.bin",
                    localFileName = "model.bin",
                    sha256 = "abc123",
                    sizeInBytes = 1_000_000L,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations = emptyList(),
        )

    private val modelsResult =
        DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = listOf(testAsset),
            scanResult =
                ModelScanResult(
                    missingModels = listOf(testAsset),
                    partialDownloads = emptyMap(),
                    allValid = true,
                ),
        )

    @BeforeEach
    fun setup() {
        orchestrator.onStartDownloads = {
            downloadStateFlow.value = downloadStateFlow.value.copy(status = DownloadStatus.DOWNLOADING)
        }
    }

    private var workObserver =
        com.browntowndev.enclave.core.testing.download.FakeDownloadWorkObserverPort()
    private var progressParserPort: com.browntowndev.enclave.domain.port.download.DownloadProgressParserPort =
        object : com.browntowndev.enclave.domain.port.download.DownloadProgressParserPort {
            override fun parseProgress(currentDownloads: List<com.browntowndev.enclave.domain.model.download.FileProgress>) = null
        }

    private fun createViewModel(autoStartDownloads: Boolean): DownloadViewModel =
        DownloadViewModel(
            modelDownloadOrchestrator = orchestrator,
            observeDownloadWork = com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase(workObserver),
            parseDownloadProgress = com.browntowndev.enclave.domain.usecase.download.ParseDownloadProgressUseCase(progressParserPort),
            errorHandler = errorHandler,
            modelsResult = modelsResult,
            initialErrorMessage = null,
            autoStartDownloads = autoStartDownloads,
        )

    @Test
    @DisplayName("auto-start queues one download session and UI reflects DOWNLOADING")
    fun autoStart_queuesOneSessionAndShowsDownloading() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = true)
            advanceUntilIdle()

            assertEquals(listOf(modelsResult to true), orchestrator.startCalls)
            assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
        }

    @Test
    @DisplayName("checkModels after auto-start does not enqueue a second session")
    fun checkModels_afterAutoStart_doesNotDoubleEnqueue() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = true)
            advanceUntilIdle()

            viewModel.checkModels()
            advanceUntilIdle()

            assertEquals(listOf(modelsResult to true), orchestrator.startCalls)
        }

    @Test
    @DisplayName("init re-initializes orchestrator with the (filtered) modelsResult before downloads start")
    fun init_reInitializesOrchestratorWithFilteredResult() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            // The VM must push its modelsResult back into the orchestrator so the cached
            // startupModelsResult and currentDownloads reflect API-onboarding suppression.
            assertEquals(listOf(modelsResult), orchestrator.initializeCalls)
            assertEquals(DownloadStatus.IDLE, viewModel.downloadState.value.status)
        }

    @Test
    @DisplayName("init does not re-initialize orchestrator while downloads are in flight")
    fun init_skipsReInitWhenDownloadsInProgress() =
        runTest {
            // Simulate an orchestrator already mid-download (e.g. VM recreated by a running app).
            downloadStateFlow.value = DownloadState(status = DownloadStatus.DOWNLOADING)
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            // Re-init must be skipped to avoid cancelling in-flight WorkManager jobs.
            assertTrue(orchestrator.initializeCalls.isEmpty())
            assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
        }

    @Test
    @DisplayName("init re-initializes orchestrator even when an initial error is present")
    fun init_reInitializesOrchestratorOnErrorMessagePath() =
        runTest {
            val viewModel =
                DownloadViewModel(
                    modelDownloadOrchestrator = orchestrator,
                    observeDownloadWork = com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase(com.browntowndev.enclave.core.testing.download.FakeDownloadWorkObserverPort()),
                    parseDownloadProgress = com.browntowndev.enclave.domain.usecase.download.ParseDownloadProgressUseCase(object : com.browntowndev.enclave.domain.port.download.DownloadProgressParserPort {
                        override fun parseProgress(currentDownloads: List<com.browntowndev.enclave.domain.model.download.FileProgress>) = null
                    }),
                    errorHandler = errorHandler,
                    modelsResult = modelsResult,
                    initialErrorMessage = "boom",
                    autoStartDownloads = false,
                )
            advanceUntilIdle()

            // Even on the error path, the cache must be refreshed so a later Retry uses the
            // filtered set rather than the unfiltered startup snapshot.
            assertEquals(listOf(modelsResult), orchestrator.initializeCalls)
            assertEquals(listOf("boom"), orchestrator.setErrorCalls)
        }

    @Test
    @DisplayName("work progress flow updates downloadState to DOWNLOADING via parser and orchestrator")
    fun observeWorkProgress_parserDownloadingUpdate_reflectsDownloadingState() =
        runTest {
            val downloadingUpdate =
                DownloadProgressUpdate(status = DownloadStatus.DOWNLOADING)

            workObserver =
                com.browntowndev.enclave.core.testing.download.FakeDownloadWorkObserverPort(
                    com.browntowndev.enclave.domain.model.download.DownloadWorkSnapshot(
                        id = "work-1",
                        lifecycle = com.browntowndev.enclave.domain.model.download.DownloadWorkLifecycle.RUNNING,
                        isTerminalSuccess = false,
                        isTerminalFailure = false,
                        isIntermediateDownloadSuccess = false,
                        isFinalizing = false,
                        postProcessProgress = null,
                        aggregateProgress = null,
                        unassignedFileProgress = null,
                    ),
                )
            progressParserPort =
                object : com.browntowndev.enclave.domain.port.download.DownloadProgressParserPort {
                    override fun parseProgress(
                        currentDownloads: List<com.browntowndev.enclave.domain.model.download.FileProgress>,
                    ) = downloadingUpdate
                }

            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
            assertEquals(listOf(downloadingUpdate), orchestrator.progressUpdates)
        }
}
