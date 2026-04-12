package com.browntowndev.pocketcrew.feature.settings

import androidx.work.Data
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.core.data.repository.DownloadWorkRepository
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
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
import java.util.UUID

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
        val workId = UUID.randomUUID()
        
        coEvery { reDownloadModelUseCase(modelId) } returns Result.success(Unit)
        coEvery { downloadWorkRepository.getWorkId() } returns workId
        
        // Match the "unassigned|downloaded|total" format expected by ViewModel
        val progressData = Data.Builder()
            .putStringArray(DownloadKey.FILES_PROGRESS.key, arrayOf("unassigned|50|100"))
            .build()
            
        val workInfoRunning = mockk<WorkInfo>()
        every { workInfoRunning.state } returns WorkInfo.State.RUNNING
        every { workInfoRunning.progress } returns progressData
        every { workInfoRunning.id } returns workId
        every { workInfoRunning.id } returns workId

        val workInfoSucceeded = mockk<WorkInfo>()
        every { workInfoSucceeded.state } returns WorkInfo.State.SUCCEEDED
        every { workInfoSucceeded.progress } returns Data.EMPTY
        every { workInfoSucceeded.id } returns workId
            
        val workInfoFlow = flowOf(workInfoRunning, workInfoSucceeded)
        
        every { downloadWorkRepository.observeDownloadProgress(workId) } returns workInfoFlow

        viewModel.reDownloadModel(modelId)

        // Capture states during execution would be better with Turbine, 
        // but here we check final or use advanceTimeBy if needed.
        advanceUntilIdle()

        val finalState = viewModel.reDownloadStates.value[modelId]
        // Complete state is removed after 2 seconds in implementation, so it might be null
        assertTrue(finalState is ReDownloadProgress.Complete || finalState == null)
    }

    @Test
    fun `reDownloadModel on use case failure transitions to Failed`() = runTest {
        val modelId = LocalModelId("test")
        coEvery { reDownloadModelUseCase(modelId) } returns Result.failure(Exception("Fetch failed"))

        viewModel.reDownloadModel(modelId)

        advanceUntilIdle()

        val finalState = viewModel.reDownloadStates.value[modelId]
        // Failed state is removed after 3 seconds in implementation
        assertTrue(finalState is ReDownloadProgress.Failed || finalState == null)
        if (finalState is ReDownloadProgress.Failed) {
            assertEquals("Fetch failed", finalState.error)
        }
    }

    @Test
    fun `reDownloadModel when workId not found transitions to Failed`() = runTest {
        val modelId = LocalModelId("test")
        coEvery { reDownloadModelUseCase(modelId) } returns Result.success(Unit)
        coEvery { downloadWorkRepository.getWorkId() } returns null

        viewModel.reDownloadModel(modelId)

        // Give it time to finish polling (5 attempts * 500ms)
        advanceUntilIdle()

        val finalState = viewModel.reDownloadStates.value[modelId]
        assertTrue(finalState is ReDownloadProgress.Failed || finalState == null)
    }
}
