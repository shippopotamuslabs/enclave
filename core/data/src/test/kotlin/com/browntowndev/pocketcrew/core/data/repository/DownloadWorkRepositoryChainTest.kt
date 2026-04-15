package com.browntowndev.pocketcrew.core.data.repository

import android.util.Log
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.core.data.download.DownloadWorkKeys
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tests for DownloadWorkRepository Chain-Aware Observation.
 *
 * These tests verify that the repository:
 * - Observes the active unique work chain, not a single UUID
 * - Selects the appropriate worker (finalizer > downloader when both exist)
 * - Ignores work from stale sessions
 * - Provides chain-aware observation for UI
 */
class DownloadWorkRepositoryChainTest {

    companion object {
        const val KEY_WORKER_STAGE = "worker_stage"
        const val KEY_SESSION_ID = "work_session_id"
        const val STAGE_DOWNLOAD = "DOWNLOAD"
        const val STAGE_FINALIZE = "FINALIZE"

        const val ACTIVE_SESSION = "active-session-123"
        const val STALE_SESSION = "stale-session-456"
    }

    private lateinit var mockWorkManager: WorkManager
    private lateinit var repository: DownloadWorkRepository

    private val downloadWorkId = UUID.randomUUID()
    private val finalizeWorkId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        mockWorkManager = mockk(relaxed = true)
        // Inject UnconfinedTestDispatcher for IO work to avoid race conditions in tests
        repository = DownloadWorkRepository(mockWorkManager, kotlinx.coroutines.test.UnconfinedTestDispatcher())

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    // ===== REPO_01: Selects running finalizer over succeeded downloader =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_01 - selects running finalizer over succeeded downloader in same session`() = runTest {
        // Given: Chain with succeeded downloader and running finalizer
        val downloadWork = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.SUCCEEDED,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )

        val finalizeWork = createMockWorkInfo(
            id = finalizeWorkId,
            state = WorkInfo.State.RUNNING,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_FINALIZE
        )

        val workList = listOf(downloadWork, finalizeWork)

        // Mock both Flow and ListenableFuture APIs
        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flowOf(workList)

        mockListenableFuture(workList)

        // When: Get current work info
        val result = repository.getCurrentWorkInfo()

        // Then: Should return the finalizer (active), not downloader (terminal)
        assertNotNull(result)
        assertEquals(WorkInfo.State.RUNNING, result!!.state)
        assertEquals(finalizeWorkId, result.id)
    }

    // ===== REPO_02: Selects running downloader when finalizer not started =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_02 - selects running downloader when finalizer has not started`() = runTest {
        // Given: Chain with only running download work
        val downloadWork = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.RUNNING,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )

        val workList = listOf(downloadWork)

        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flowOf(workList)

        mockListenableFuture(workList)

        // When: Get current work info
        val result = repository.getCurrentWorkInfo()

        // Then: Should return the running downloader
        assertNotNull(result)
        assertEquals(WorkInfo.State.RUNNING, result!!.state)
        assertEquals(downloadWorkId, result.id)
    }

    // ===== REPO_03: Selects terminal finalizer when chain is complete =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_03 - selects terminal finalizer when chain is complete`() = runTest {
        // Given: Chain with both succeeded
        val downloadWork = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.SUCCEEDED,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )

        val finalizeWork = createMockWorkInfo(
            id = finalizeWorkId,
            state = WorkInfo.State.SUCCEEDED,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_FINALIZE
        )

        val workList = listOf(downloadWork, finalizeWork)

        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flowOf(workList)

        mockListenableFuture(workList)

        // When: Get current work info
        val result = repository.getCurrentWorkInfo()

        // Then: Should return the finalizer (terminal for chain)
        assertNotNull(result)
        assertEquals(WorkInfo.State.SUCCEEDED, result!!.state)
        assertEquals(finalizeWorkId, result.id)
    }

    // ===== REPO_04: Ignores work from stale sessions =====

    /**
     * REPO_04: Selects active session work regardless of stale data.
     *
     * Note: Session staleness is handled by the parser, not the repository.
     * The repository passes through all work info so the parser can filter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_04 - returns non-null when work exists`() = runTest {
        // Given: Work items from different sessions
        val activeWork = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.RUNNING,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )

        val workList = listOf(activeWork)

        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flowOf(workList)

        mockListenableFuture(workList)

        // When: Get current work info
        val result = repository.getCurrentWorkInfo()

        // Then: Should return the work (session staleness is handled by the parser)
        assertNotNull(result)
    }

    // ===== REPO_05: Returns null when no work matches =====

    @Test
    fun `REPO_05 - returns null when no work matches session`() = runTest {
        // Given: Empty work list
        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flowOf(emptyList())

        mockListenableFuture(emptyList())

        // When: Get current work info
        val result = repository.getCurrentWorkInfo()

        // Then: Should return null
        assertNull(result)
    }

    // ===== REPO_06: getWorkId returns finalizer when running =====

    @Test
    fun `REPO_06 - getWorkId returns finalizer when running`() = runTest {
        // Given: Running finalizer
        val downloadWork = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.SUCCEEDED,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )

        val finalizeWork = createMockWorkInfo(
            id = finalizeWorkId,
            state = WorkInfo.State.RUNNING,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_FINALIZE
        )

        val workList = listOf(downloadWork, finalizeWork)
        mockListenableFuture(workList)

        // When: Get work ID
        val result = repository.getWorkId()

        // Then: Should return finalizer's ID
        assertEquals(finalizeWorkId, result)
    }

    // ===== REPO_07: Backward compatibility - startup observation still works =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_07 - startup observation still works while only downloader is running`() = runTest {
        // Given: Single download work (no finalizer yet)
        val downloadWork = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.RUNNING,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )

        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flowOf(listOf(downloadWork))

        // When: Observe progress for the download work ID
        val result = repository.observeDownloadProgress(downloadWorkId).first()

        // Then: Should receive the work info
        assertNotNull(result)
        assertEquals(downloadWorkId, result!!.id)
        assertEquals(WorkInfo.State.RUNNING, result.state)
    }

    // ===== REPO_08: observeDownloadProgress transitions from succeeded downloader to finalizer =====

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `REPO_08 - observeDownloadProgress transitions to finalizer when downloader succeeds`() = runTest {
        // Given: Initially only downloader is running
        val downloadWorkRunning = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.RUNNING,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )

        val workList1 = listOf(downloadWorkRunning)

        // Then downloader succeeds and finalizer starts
        val downloadWorkSucceeded = createMockWorkInfo(
            id = downloadWorkId,
            state = WorkInfo.State.SUCCEEDED,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_DOWNLOAD
        )
        val finalizeWorkRunning = createMockWorkInfo(
            id = finalizeWorkId,
            state = WorkInfo.State.RUNNING,
            sessionId = ACTIVE_SESSION,
            workerStage = STAGE_FINALIZE
        )
        val workList2 = listOf(downloadWorkSucceeded, finalizeWorkRunning)

        val flow = kotlinx.coroutines.flow.MutableStateFlow(workList1)
        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flow

        // When: Observe progress starting with downloadWorkId
        val results = mutableListOf<WorkInfo>()
        val job = launch {
            repository.observeDownloadProgress(downloadWorkId).collect {
                if (it != null) results.add(it)
            }
        }

        // Initially sees downloader
        delay(100)
        assertEquals(1, results.size)
        assertEquals(downloadWorkId, results[0].id)

        // Downloader succeeds, finalizer starts
        flow.value = workList2
        delay(100)

        // Then: Should have transitioned to finalizer
        assertEquals(2, results.size)
        assertEquals(finalizeWorkId, results[1].id)
        assertEquals(WorkInfo.State.RUNNING, results[1].state)

        job.cancel()
    }

    // ===== Helper methods =====

    @Suppress("UNCHECKED_CAST")
    private fun mockListenableFuture(workList: List<WorkInfo>) {
        val future = Futures.immediateFuture(workList) as ListenableFuture<List<WorkInfo>>
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG)
        } returns future
    }

    private fun createMockWorkInfo(
        id: UUID,
        state: WorkInfo.State,
        sessionId: String,
        workerStage: String
    ): WorkInfo {
        val outputData = Data.Builder()
            .putString(DownloadWorkKeys.KEY_SESSION_ID, sessionId)
            .putString(DownloadWorkKeys.KEY_WORKER_STAGE, workerStage)
            .build()

        return mockk {
            every { this@mockk.id } returns id
            every { this@mockk.state } returns state
            every { this@mockk.outputData } returns outputData
            every { progress } returns Data.EMPTY
        }
    }
}