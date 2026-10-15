package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.download.DownloadRequestKind
import com.browntowndev.enclave.domain.model.download.DownloadWorkLifecycle
import com.browntowndev.enclave.domain.model.download.DownloadWorkSnapshot
import com.browntowndev.enclave.domain.model.download.ScheduledDownload
import com.browntowndev.enclave.domain.port.download.DownloadWorkObserverPort
import com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelPackUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelUseCase
import kotlinx.coroutines.flow.Flow
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
    private val reDownloadModelPackUseCase = mockk<ReDownloadModelPackUseCase>(relaxed = true)
    private var workFlow: Flow<DownloadWorkSnapshot?> = flowOf(null)
    private val observerPort =
        object : DownloadWorkObserverPort {
            override fun observeDownloadWork(): Flow<DownloadWorkSnapshot?> = workFlow
        }
    private lateinit var viewModel: ModelReDownloadViewModel

    @BeforeEach
    fun setup() {
        viewModel =
            ModelReDownloadViewModel(
                reDownloadModelUseCase,
                reDownloadModelPackUseCase,
                ObserveDownloadWorkUseCase(observerPort),
            )
    }

    private fun snapshot(
        lifecycle: DownloadWorkLifecycle,
        isTerminalSuccess: Boolean = false,
        isIntermediateDownloadSuccess: Boolean = false,
        unassignedFileProgress: String? = null,
        aggregateProgress: Float? = null,
    ): DownloadWorkSnapshot =
        DownloadWorkSnapshot(
            id = "work-1",
            lifecycle = lifecycle,
            isTerminalSuccess = isTerminalSuccess,
            isTerminalFailure = false,
            isIntermediateDownloadSuccess = isIntermediateDownloadSuccess,
            isFinalizing = false,
            postProcessProgress = null,
            aggregateProgress = aggregateProgress,
            unassignedFileProgress = unassignedFileProgress,
        )

    @Test
    fun `reDownloadModel transitions to Preparing then Downloading and Complete`() =
        runTest {
            val modelId = LocalModelId("test")

            coEvery { reDownloadModelUseCase(modelId) } returns
                Result.success(
                    ScheduledDownload(
                        sessionId = "test-session",
                        requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
                        targetModelId = modelId,
                    ),
                )

            val running = snapshot(DownloadWorkLifecycle.RUNNING, unassignedFileProgress = "unassigned|50|100")
            val succeeded = snapshot(DownloadWorkLifecycle.SUCCEEDED, isTerminalSuccess = true)
            workFlow = flowOf(running, succeeded)

            viewModel.reDownloadModel(modelId)
            advanceUntilIdle()

            val finalState = viewModel.reDownloadStates.value[modelId]
            assertTrue(finalState is ReDownloadProgress.Complete || finalState == null)
        }

    @Test
    fun `reDownloadModel on use case failure transitions to Failed`() =
        runTest {
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
    fun `reDownloadModel keeps observing while downloader succeeds before finalizer`() =
        runTest {
            val modelId = LocalModelId("test")

            coEvery { reDownloadModelUseCase(modelId) } returns
                Result.success(
                    ScheduledDownload(
                        sessionId = "test-session",
                        requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
                        targetModelId = modelId,
                    ),
                )

            val downloaderSucceeded =
                snapshot(DownloadWorkLifecycle.SUCCEEDED, isIntermediateDownloadSuccess = true)
            val finalizerSucceeded = snapshot(DownloadWorkLifecycle.SUCCEEDED, isTerminalSuccess = true)
            workFlow = flowOf(downloaderSucceeded, finalizerSucceeded)

            viewModel.reDownloadModel(modelId)
            advanceUntilIdle()

            val finalState = viewModel.reDownloadStates.value[modelId]
            assertTrue(finalState is ReDownloadProgress.Complete || finalState == null)
        }

    @Test
    fun `reDownloadPack waits for finalizer after downloader succeeds`() =
        runTest {
            coEvery { reDownloadModelPackUseCase("sdxl-t2i") } returns
                Result.success(
                    listOf(
                        ScheduledDownload(
                            sessionId = "pack-session",
                            requestKind = DownloadRequestKind.INITIALIZE_MODELS,
                        ),
                    ),
                )

            val downloaderSucceeded =
                snapshot(
                    DownloadWorkLifecycle.SUCCEEDED,
                    isIntermediateDownloadSuccess = true,
                    aggregateProgress = 1f,
                )
            val finalizerSucceeded = snapshot(DownloadWorkLifecycle.SUCCEEDED, isTerminalSuccess = true)
            workFlow = flowOf(downloaderSucceeded, finalizerSucceeded)

            viewModel.reDownloadPack("sdxl-t2i")
            advanceUntilIdle()

            val finalState = viewModel.packReDownloadStates.value["sdxl-t2i"]
            assertTrue(finalState is ReDownloadProgress.Complete || finalState == null)
        }
}
