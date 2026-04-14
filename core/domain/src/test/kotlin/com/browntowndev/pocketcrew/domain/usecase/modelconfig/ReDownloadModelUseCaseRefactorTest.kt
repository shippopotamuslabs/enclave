package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.download.DownloadRequestKind
import com.browntowndev.pocketcrew.domain.model.download.ScheduledDownload
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * TDD Red Phase Tests for Download Worker Refactor - Refactored Use Case.
 *
 * These tests verify the NEW expected behavior where:
 * - Re-download does NOT call restoreSoftDeletedModel() before bytes land
 * - Re-download returns ScheduledDownload with session info
 * - Re-download schedules via structured DownloadWorkRequest
 *
 * The refactored ReDownloadModelUseCase no longer depends on ModelConfigFetcherPort
 * because restoration now happens in DownloadFinalizeWorker.
 */
class ReDownloadModelUseCaseRefactorTest {

    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var downloadWorkScheduler: DownloadWorkSchedulerPort
    private lateinit var loggingPort: LoggingPort
    private lateinit var useCase: ReDownloadModelUseCase

    private val modelId = LocalModelId("test-model-id")
    private val sha256 = "abc123def456"

    private val softDeletedAsset = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = modelId,
            huggingFaceModelName = "test/model",
            remoteFileName = "test.gguf",
            localFileName = "test.gguf",
            sha256 = sha256,
            sizeInBytes = 1_000_000_000L,
            modelFileFormat = ModelFileFormat.GGUF,
            source = DownloadSource.HUGGING_FACE,
        ),
        configurations = emptyList() // Soft-deleted - no configurations
    )

    @BeforeEach
    fun setup() {
        localModelRepository = mockk(relaxed = true)
        downloadWorkScheduler = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)

        useCase = ReDownloadModelUseCase(
            localModelRepository = localModelRepository,
            downloadWorkScheduler = downloadWorkScheduler,
            loggingPort = loggingPort
        )
    }

    /**
     * REDOWN_01: Does NOT call restoreSoftDeletedModel() before bytes land.
     *
     * This is the CENTRAL CORRECTNESS BUG in the current implementation.
     * The refactored use case schedules the download pipeline and returns session metadata.
     * Restoration happens in DownloadFinalizeWorker AFTER bytes are verified on disk.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_01 - does NOT call restoreSoftDeletedModel() before bytes land`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        val result = useCase(modelId)

        // Then - verify restoreSoftDeletedModel was NOT called
        // The new design moves this call to DownloadFinalizeWorker
        coVerify(exactly = 0) {
            localModelRepository.restoreSoftDeletedModel(any(), any())
        }

        assertTrue(result.isSuccess)
    }

    /**
     * REDOWN_03: No repository mutation occurs before bytes are downloaded.
     *
     * This is a regression test for the central correctness bug.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_03 - no repository mutation before bytes are downloaded`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When - scheduling happens, not completion
        val result = useCase(modelId)

        // Then - no repository mutations should have occurred
        assertTrue(result.isSuccess)

        // Verify restoreSoftDeletedModel was NOT called (main mutation)
        coVerify(exactly = 0) {
            localModelRepository.restoreSoftDeletedModel(any(), any())
        }

        // Verify upsertLocalAsset was NOT called (another mutation)
        coVerify(exactly = 0) {
            localModelRepository.upsertLocalAsset(any())
        }
    }

    /**
     * REDOWN_02: Returns ScheduledDownload with session info for progress tracking.
     *
     * The new design returns session metadata so the UI can observe
     * progress for the specific scheduled request.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_02 - returns session info for progress tracking`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        val result = useCase(modelId)

        // Then
        assertTrue(result.isSuccess)

        val scheduledDownload = result.getOrNull()
        assertNotNull(scheduledDownload)
        assertNotNull(scheduledDownload!!.sessionId)
        assertEquals(DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL, scheduledDownload.requestKind)
        assertEquals(modelId, scheduledDownload.targetModelId)
    }

    /**
     * REDOWN_04: Schedules download via structured DownloadWorkRequest.
     *
     * The refactored use case should call downloadWorkScheduler.enqueue(DownloadWorkRequest(...))
     * with requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL and targetModelId = modelId.
     *
     * EXPECTED: This test should PASS with the refactored implementation.
     */
    @Test
    fun `REDOWN_04 - schedules download via structured request`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        useCase(modelId)

        // Then - verify the structured scheduler was called
        coVerify {
            downloadWorkScheduler.enqueue(
                match { request ->
                    request.requestKind == DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL &&
                            request.targetModelId == modelId &&
                            request.files.isNotEmpty() &&
                            request.files[0].sha256 == sha256
                }
            )
        }
    }
}