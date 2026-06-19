package com.browntowndev.enclave.feature.download

import androidx.work.WorkInfo
import com.browntowndev.enclave.core.data.download.WorkProgressParser
import com.browntowndev.enclave.core.data.repository.DownloadWorkRepository
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadProgressUpdate
import com.browntowndev.enclave.domain.model.download.DownloadState
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelStartupTest {
    private val orchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)
    private val workRepo = mockk<DownloadWorkRepository>(relaxed = true)
    private val parser = mockk<WorkProgressParser>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
    private val downloadStateFlow = MutableStateFlow(DownloadState(status = DownloadStatus.IDLE))

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
        every { orchestrator.downloadState } returns downloadStateFlow
        every { orchestrator.snackbarMessages } returns flowOf()
        every { workRepo.observeDownloadProgress() } returns flowOf()
        coEvery { orchestrator.startDownloads(modelsResult = modelsResult, wifiOnly = true) } answers {
            downloadStateFlow.value = downloadStateFlow.value.copy(status = DownloadStatus.DOWNLOADING)
            true
        }
    }

    private fun createViewModel(autoStartDownloads: Boolean): DownloadViewModel =
        DownloadViewModel(
            modelDownloadOrchestrator = orchestrator,
            downloadWorkRepository = workRepo,
            progressParser = parser,
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

            coVerify(exactly = 1) {
                orchestrator.startDownloads(modelsResult = modelsResult, wifiOnly = true)
            }
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

            coVerify(exactly = 1) {
                orchestrator.startDownloads(modelsResult = modelsResult, wifiOnly = true)
            }
        }

    @Test
    @DisplayName("init re-initializes orchestrator with the (filtered) modelsResult before downloads start")
    fun init_reInitializesOrchestratorWithFilteredResult() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            // The VM must push its modelsResult back into the orchestrator so the cached
            // startupModelsResult and currentDownloads reflect API-onboarding suppression.
            verify(exactly = 1) { orchestrator.initializeWithStartupResult(modelsResult) }
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
            verify(exactly = 0) { orchestrator.initializeWithStartupResult(any()) }
            assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
        }

    @Test
    @DisplayName("init re-initializes orchestrator even when an initial error is present")
    fun init_reInitializesOrchestratorOnErrorMessagePath() =
        runTest {
            val viewModel =
                DownloadViewModel(
                    modelDownloadOrchestrator = orchestrator,
                    downloadWorkRepository = workRepo,
                    progressParser = parser,
                    errorHandler = errorHandler,
                    modelsResult = modelsResult,
                    initialErrorMessage = "boom",
                    autoStartDownloads = false,
                )
            advanceUntilIdle()

            // Even on the error path, the cache must be refreshed so a later Retry uses the
            // filtered set rather than the unfiltered startup snapshot.
            verify(exactly = 1) { orchestrator.initializeWithStartupResult(modelsResult) }
            verify { orchestrator.setError("boom") }
        }

    @Test
    @DisplayName("work progress flow updates downloadState to DOWNLOADING via parser and orchestrator")
    fun observeWorkProgress_parserDownloadingUpdate_reflectsDownloadingState() =
        runTest {
            val workInfo =
                mockk<WorkInfo> {
                    every { id } returns UUID.randomUUID()
                    every { state } returns WorkInfo.State.RUNNING
                    every { tags } returns emptySet()
                    every { outputData } returns androidx.work.Data.EMPTY
                    every { progress } returns androidx.work.Data.EMPTY
                }
            val downloadingUpdate =
                DownloadProgressUpdate(status = DownloadStatus.DOWNLOADING)

            every { workRepo.observeDownloadProgress() } returns flowOf(workInfo)
            every { parser.parse(workInfo, any()) } returns downloadingUpdate
            coEvery { orchestrator.updateFromProgressUpdate(any()) } coAnswers {
                val update = firstArg<DownloadProgressUpdate>()
                downloadStateFlow.value = downloadStateFlow.value.copy(status = update.status)
            }

            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
            verify { parser.parse(workInfo, any()) }
            coVerify(exactly = 1) { orchestrator.updateFromProgressUpdate(downloadingUpdate) }
        }
}
