package com.browntowndev.pocketcrew.data.repository

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class DownloadWorkRepositoryTest {

    private lateinit var mockWorkManager: WorkManager
    private lateinit var repository: DownloadWorkRepository

    private val workId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        mockWorkManager = mockk(relaxed = true)
        repository = DownloadWorkRepository(mockWorkManager)

        // Mock the Log class to prevent RuntimeException in unit tests
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    /**
     * Test that terminal states (SUCCEEDED, FAILED, CANCELLED) are emitted when WorkInfo
     * transitions to those states.
     * 
     * BUG: The original polling loop exited without emitting the final state, causing
     * the UI to never show completion status. This test verifies that the Flow emits
     * terminal states after the work transitions from RUNNING.
     * 
     * This test simulates the polling mechanism and verifies that Phase 3 (emitting terminal
     * state) is executed after the work completes.
     */
    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkSucceeds() = runBlocking {
        // Arrange: Simulate work going from RUNNING -> SUCCEEDED
        val workInfoRunning = createWorkInfo(WorkInfo.State.RUNNING)
        val workInfoSucceeded = createWorkInfo(WorkInfo.State.SUCCEEDED)

        // Track call count to return different states on successive calls
        val callCount = intArrayOf(0)
        
        every { mockWorkManager.getWorkInfoById(workId) } answers {
            callCount[0]++
            val mockFuture = mockk<ListenableFuture<WorkInfo?>>()
            coEvery { mockFuture.get() } returns when {
                callCount[0] == 1 -> workInfoRunning  // First call - initial work exists
                callCount[0] <= 3 -> workInfoRunning // Phase 2 - still running
                else -> workInfoSucceeded             // Phase 3 - terminal state
            }
            mockFuture
        }

        // Act: Collect all emissions from the Flow
        val emissions = mutableListOf<WorkInfo>()
        repository.observeDownloadProgress(workId).collect { workInfo ->
            emissions.add(workInfo)
            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                return@collect
            }
        }

        // Assert: Verify terminal state SUCCEEDED is emitted
        // The bug would cause this to FAIL because the loop exited without emitting final state
        assertTrue(
            emissions.any { it.state == WorkInfo.State.SUCCEEDED },
            "Expected SUCCEEDED state to be emitted but got: ${emissions.map { it.state }}"
        )
        assertTrue(
            emissions.last().state == WorkInfo.State.SUCCEEDED,
            "Expected last emission to be SUCCEEDED"
        )
    }

    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkFails() = runBlocking {
        // Arrange: Simulate work going from RUNNING -> FAILED
        val workInfoRunning = createWorkInfo(WorkInfo.State.RUNNING)
        val workInfoFailed = createWorkInfo(WorkInfo.State.FAILED)

        val callCount = intArrayOf(0)
        every { mockWorkManager.getWorkInfoById(workId) } answers {
            callCount[0]++
            val mockFuture = mockk<ListenableFuture<WorkInfo?>>()
            coEvery { mockFuture.get() } returns when {
                callCount[0] == 1 -> workInfoRunning
                callCount[0] <= 3 -> workInfoRunning
                else -> workInfoFailed
            }
            mockFuture
        }

        // Act
        val emissions = mutableListOf<WorkInfo>()
        repository.observeDownloadProgress(workId).collect { workInfo ->
            emissions.add(workInfo)
            if (workInfo.state == WorkInfo.State.FAILED) {
                return@collect
            }
        }

        // Assert
        assertTrue(
            emissions.any { it.state == WorkInfo.State.FAILED },
            "Expected FAILED state to be emitted but got: ${emissions.map { it.state }}"
        )
        assertTrue(
            emissions.last().state == WorkInfo.State.FAILED,
            "Expected last emission to be FAILED"
        )
    }

    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkCancelled() = runBlocking {
        // Arrange: Simulate work going from RUNNING -> CANCELLED
        val workInfoRunning = createWorkInfo(WorkInfo.State.RUNNING)
        val workInfoCancelled = createWorkInfo(WorkInfo.State.CANCELLED)

        val callCount = intArrayOf(0)
        every { mockWorkManager.getWorkInfoById(workId) } answers {
            callCount[0]++
            val mockFuture = mockk<ListenableFuture<WorkInfo?>>()
            coEvery { mockFuture.get() } returns when {
                callCount[0] == 1 -> workInfoRunning
                callCount[0] <= 3 -> workInfoRunning
                else -> workInfoCancelled
            }
            mockFuture
        }

        // Act
        val emissions = mutableListOf<WorkInfo>()
        repository.observeDownloadProgress(workId).collect { workInfo ->
            emissions.add(workInfo)
            if (workInfo.state == WorkInfo.State.CANCELLED) {
                return@collect
            }
        }

        // Assert
        assertTrue(
            emissions.any { it.state == WorkInfo.State.CANCELLED },
            "Expected CANCELLED state to be emitted but got: ${emissions.map { it.state }}"
        )
        assertTrue(
            emissions.last().state == WorkInfo.State.CANCELLED,
            "Expected last emission to be CANCELLED"
        )
    }

    @Test
    fun observeDownloadProgress_emitsRunningStateFirst() = runBlocking {
        // Arrange: Work is already RUNNING
        val workInfoRunning = createWorkInfo(WorkInfo.State.RUNNING)
        val workInfoSucceeded = createWorkInfo(WorkInfo.State.SUCCEEDED)

        val callCount = intArrayOf(0)
        every { mockWorkManager.getWorkInfoById(workId) } answers {
            callCount[0]++
            val mockFuture = mockk<ListenableFuture<WorkInfo?>>()
            coEvery { mockFuture.get() } returns when {
                callCount[0] == 1 -> workInfoRunning
                callCount[0] <= 3 -> workInfoRunning
                else -> workInfoSucceeded
            }
            mockFuture
        }

        // Act
        val emissions = mutableListOf<WorkInfo>()
        repository.observeDownloadProgress(workId).collect { workInfo ->
            emissions.add(workInfo)
            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                return@collect
            }
        }

        // Assert: First emission should be the RUNNING state
        assertTrue(
            emissions.first().state == WorkInfo.State.RUNNING,
            "Expected first emission to be RUNNING but was: ${emissions.first().state}"
        )
    }

    /**
     * Helper to create a mock WorkInfo with the given state.
     */
    private fun createWorkInfo(state: WorkInfo.State): WorkInfo {
        val workInfo = mockk<WorkInfo>()
        every { workInfo.id } returns workId
        every { workInfo.state } returns state
        every { workInfo.progress } returns mockk(relaxed = true)
        every { workInfo.outputData } returns mockk(relaxed = true)
        return workInfo
    }
}

