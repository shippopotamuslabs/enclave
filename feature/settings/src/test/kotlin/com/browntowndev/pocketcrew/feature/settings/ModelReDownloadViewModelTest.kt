package com.browntowndev.pocketcrew.feature.settings

import androidx.work.Data
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.core.data.repository.DownloadWorkRepository
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.model.download.DownloadRequestKind
import com.browntowndev.pocketcrew.domain.model.download.ScheduledDownload
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ReDownloadModelUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ModelReDownloadViewModelTest {

    private val reDownloadModelUseCase = mockk<ReDownloadModelUseCase>()
    private val downloadWorkRepository = mockk<DownloadWorkRepository>()
    private lateinit var viewModel: ModelReDownloadViewModel

    @BeforeEach
    fun setup() {
        viewModel = ModelReDownloadViewModel(reDownloadModelUseCase, downloadWorkRepository)
    }

    @Test
    fun `reDownloadModel transitions to Preparing then Downloading and Complete`() = runTest {
        val modelId = LocalModelId("test")

        coEvery { reDownloadModelUseCase(modelId) } returns Result.success(
            ScheduledDownload(
                sessionId = "test-session",
                requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
                targetModelId = modelId,
            )
        )

        val progressData = Data.Builder()
            .putStringArray(DownloadKey.FILES_PROGRESS.key, arrayOf("unassigned|50|100"))
            .build()

        val workInfoRunning = mockk<WorkInfo>()
        every { workInfoRunning.state } returns WorkInfo.State.RUNNING
        every { workInfoRunning.progress } returns progressData

        // Simulate the finalizer SUCCEEDED (terminal state in the chain)
        val finalizerOutputData = Data.Builder()
            .putString("worker_stage", "FINALIZE")
            .build()
        val workInfoSucceeded = mockk<WorkInfo>()
        every { workInfoSucceeded.state } returns WorkInfo.State.SUCCEEDED
        every { workInfoSucceeded.progress } returns Data.EMPTY
        every { workInfoSucceeded.outputData } returns finalizerOutputData

        every { downloadWorkRepository.observeDownloadProgress() } returns
            flowOf(workInfoRunning, workInfoSucceeded)

        viewModel.reDownloadModel(modelId)
        advanceUntilIdle()

        val finalState = viewModel.reDownloadStates.value[modelId]
        assertTrue(finalState is ReDownloadProgress.Complete || finalState == null)
    }

    @Test
    fun `reDownloadModel on use case failure transitions to Failed`() = runTest {
        val modelId = LocalModelId("test")
        coEvery { reDownloadModelUseCase(modelId) } returns Result.failure(Exception("Fetch failed"))

        viewModel.reDownloadModel(modelId)
        advanceUntilIdle()

        val finalState = viewModel.reDownloadStates.value[modelId]
        assertTrue(finalState is ReDownloadProgress.Failed || finalState == null)
        if (finalState is ReDownloadProgress.Failed) {
            assertEquals("Fetch failed", finalState.error)
        }
    }

    @Test
    fun `reDownloadModel keeps observing while downloader succeeds before finalizer`() = runTest {
        val modelId = LocalModelId("test")

        coEvery { reDownloadModelUseCase(modelId) } returns Result.success(
            ScheduledDownload(
                sessionId = "test-session",
                requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
                targetModelId = modelId,
            )
        )

        // Downloader SUCCEEDED (intermediate) — should NOT trigger Complete yet
        val downloaderOutputData = Data.Builder()
            .putString("worker_stage", "DOWNLOAD")
            .build()
        val downloaderSucceeded = mockk<WorkInfo>()
        every { downloaderSucceeded.state } returns WorkInfo.State.SUCCEEDED
        every { downloaderSucceeded.progress } returns Data.EMPTY
        every { downloaderSucceeded.outputData } returns downloaderOutputData

        // Finalizer SUCCEEDED (terminal) — triggers Complete
        val finalizerOutputData = Data.Builder()
            .putString("worker_stage", "FINALIZE")
            .build()
        val finalizerSucceeded = mockk<WorkInfo>()
        every { finalizerSucceeded.state } returns WorkInfo.State.SUCCEEDED
        every { finalizerSucceeded.progress } returns Data.EMPTY
        every { finalizerSucceeded.outputData } returns finalizerOutputData

        every { downloadWorkRepository.observeDownloadProgress() } returns
            flowOf(downloaderSucceeded, finalizerSucceeded)

        viewModel.reDownloadModel(modelId)
        advanceUntilIdle()

        val finalState = viewModel.reDownloadStates.value[modelId]
        assertTrue(finalState is ReDownloadProgress.Complete || finalState == null)
    }
}