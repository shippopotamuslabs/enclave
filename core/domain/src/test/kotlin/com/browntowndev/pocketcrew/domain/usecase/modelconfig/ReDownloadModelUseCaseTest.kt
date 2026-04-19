package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import com.browntowndev.pocketcrew.domain.model.download.DownloadRequestKind
import com.browntowndev.pocketcrew.domain.model.download.DownloadWorkRequest
import com.browntowndev.pocketcrew.domain.model.download.ScheduledDownload
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
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
 * Tests for ReDownloadModelUseCase.
 * These tests verify the NEW expected behavior where:
 * - Re-download does NOT call restoreSoftDeletedModel() before bytes land
 * - Re-download returns ScheduledDownload with session info
 * - Re-download schedules via structured DownloadWorkRequest
 */
class ReDownloadModelUseCaseTest {

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

    @Test
    fun `invoke with non-existent model returns failure with IllegalStateException`() = runTest {
        coEvery { localModelRepository.getAssetById(modelId) } returns null

        val result = useCase(modelId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    /**
     * Scenario: does NOT call restoreSoftDeletedModel() before bytes land
     *
     * This is the CENTRAL CORRECTNESS BUG being fixed.
     * The old implementation called restoreSoftDeletedModel() BEFORE download.
     * The new implementation schedules the download pipeline and returns session metadata.
     * Restoration happens in DownloadFinalizeWorker AFTER bytes are verified.
     */
    @Test
    fun `does NOT call restoreSoftDeletedModel() before bytes land`() = runTest {
        // Given: Soft-deleted asset exists
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        val result = useCase(modelId)

        // Then - verify restoreSoftDeletedModel was NOT called
        // This is the key correctness fix: no early restoration
        coVerify(exactly = 0) {
            localModelRepository.restoreSoftDeletedModel(any(), any())
        }

        assertTrue(result.isSuccess)
    }

    /**
     * Scenario: no repository mutation before bytes are downloaded
     *
     * Regression test for the central correctness bug.
     */
    @Test
    fun `no repository mutation before bytes are downloaded`() = runTest {
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
     * Scenario: returns ScheduledDownload with session info for progress tracking
     */
    @Test
    fun `returns ScheduledDownload with session info`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        val result = useCase(modelId)

        // Then
        assertTrue(result.isSuccess)

        val scheduledDownload = result.getOrNull()
        assertNotNull(scheduledDownload)

        // Verify session metadata is returned
        assertNotNull(scheduledDownload!!.sessionId)
        assertEquals(DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL, scheduledDownload.requestKind)
        assertEquals(modelId, scheduledDownload.targetModelId)
    }

    /**
     * Scenario: schedules download via structured DownloadWorkRequest
     */
    @Test
    fun `schedules download via structured DownloadWorkRequest`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        useCase(modelId)

        // Then - verify the structured scheduler was called with DownloadWorkRequest
        coVerify {
            downloadWorkScheduler.enqueue(
                match<DownloadWorkRequest> { request ->
                    request.requestKind == DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL &&
                            request.targetModelId == modelId &&
                            request.files.size == 1 &&
                            request.files[0].sha256 == sha256
                }
            )
        }
    }

    /**
     * Scenario: builds DownloadFileSpec from soft-deleted asset
     */
    @Test
    fun `builds DownloadFileSpec from soft-deleted asset`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        useCase(modelId)

        // Then - verify the file spec contains correct data from the soft-deleted asset
        coVerify {
            downloadWorkScheduler.enqueue(
                match<DownloadWorkRequest> { request ->
                    val fileSpec = request.files[0]
                    fileSpec.remoteFileName == softDeletedAsset.metadata.remoteFileName &&
                            fileSpec.localFileName == softDeletedAsset.metadata.localFileName &&
                            fileSpec.sha256 == softDeletedAsset.metadata.sha256 &&
                            fileSpec.sizeInBytes == softDeletedAsset.metadata.sizeInBytes
                }
            )
        }
    }

    /**
     * Scenario: returns Result<ScheduledDownload> (not Result<Unit>)
     */
    @Test
    fun `returns Result of ScheduledDownload`() = runTest {
        // Given
        coEvery { localModelRepository.getAssetById(modelId) } returns softDeletedAsset

        // When
        val result = useCase(modelId)

        // Then - the return type is ScheduledDownload
        assertTrue(result.isSuccess)
        val scheduled = result.getOrNull()
        assertTrue(scheduled is ScheduledDownload)
    }
}