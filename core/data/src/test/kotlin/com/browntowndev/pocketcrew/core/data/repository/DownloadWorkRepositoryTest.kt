package com.browntowndev.pocketcrew.core.data.repository

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    private fun createMockWorkInfo(state: WorkInfo.State): WorkInfo {
        return mockk {
            every { id } returns workId
            every { this@mockk.state } returns state
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_emitsWorkInfo_whenWorkRunning() = runTest {
        // Arrange: Return a Flow that emits RUNNING state
        val workInfoRunning = createMockWorkInfo(WorkInfo.State.RUNNING)

        val mockFlow = kotlinx.coroutines.flow.flowOf(listOf(workInfoRunning))
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns mockFlow

        // Act
        val result = repository.observeDownloadProgress(workId).first()

        // Assert
        assertEquals(WorkInfo.State.RUNNING, result?.state)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkSucceeds() = runTest {
        // Arrange: Return a Flow that emits SUCCEEDED state
        val workInfoSucceeded = createMockWorkInfo(WorkInfo.State.SUCCEEDED)

        val mockFlow = kotlinx.coroutines.flow.flowOf(listOf(workInfoSucceeded))
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns mockFlow

        // Act
        val result = repository.observeDownloadProgress(workId).first()

        // Assert
        assertEquals(WorkInfo.State.SUCCEEDED, result?.state)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkFails() = runTest {
        // Arrange: Return a Flow that emits FAILED state
        val workInfoFailed = createMockWorkInfo(WorkInfo.State.FAILED)

        val mockFlow = kotlinx.coroutines.flow.flowOf(listOf(workInfoFailed))
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns mockFlow

        // Act
        val result = repository.observeDownloadProgress(workId).first()

        // Assert
        assertEquals(WorkInfo.State.FAILED, result?.state)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_filtersByWorkId() = runTest {
        // Arrange: Return multiple work infos, only one matching our workId
        val otherWorkId = UUID.randomUUID()
        val workInfoRunning = createMockWorkInfo(WorkInfo.State.RUNNING)
        val otherWorkInfo = mockk<WorkInfo> {
            every { id } returns otherWorkId
            every { this@mockk.state } returns WorkInfo.State.RUNNING
        }

        val mockFlow = kotlinx.coroutines.flow.flowOf(listOf(otherWorkInfo, workInfoRunning))
        every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns mockFlow

        // Act
        val result = repository.observeDownloadProgress(workId).first()

        // Assert - should find our workId
        assertEquals(workId, result?.id)
    }

    @Test
    fun getWorkId_returnsWorkId_whenWorkRunning() = runTest {
        // Arrange
        val workInfoRunning = createMockWorkInfo(WorkInfo.State.RUNNING)
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns listOf(workInfoRunning)

        // Act
        val result = repository.getWorkId()

        // Assert
        assertEquals(workId, result)
    }

    @Test
    fun getWorkId_returnsNull_whenNoWorkRunning() = runTest {
        // Arrange
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns emptyList()

        // Act
        val result = repository.getWorkId()

        // Assert
        assertEquals(null, result)
    }

    @Test
    fun cancelDownload_callsWorkManager() {
        // Act
        repository.cancelDownload()

        // Assert
        verify { mockWorkManager.cancelUniqueWork(ModelConfig.WORK_TAG) }
    }

    @Test
    fun isWorkRunning_returnsTrue_whenStateIsEnqueued() {
        // Arrange
        val workInfoEnqueued = createMockWorkInfo(WorkInfo.State.ENQUEUED)
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns listOf(workInfoEnqueued)

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(true, result)
    }

    @Test
    fun isWorkRunning_returnsTrue_whenStateIsRunning() {
        // Arrange
        val workInfoRunning = createMockWorkInfo(WorkInfo.State.RUNNING)
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns listOf(workInfoRunning)

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(true, result)
    }

    @Test
    fun isWorkRunning_returnsTrue_whenStateIsBlocked() {
        // Arrange
        val workInfoBlocked = createMockWorkInfo(WorkInfo.State.BLOCKED)
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns listOf(workInfoBlocked)

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(true, result)
    }

    @Test
    fun isWorkRunning_returnsFalse_whenStateIsSucceeded() {
        // Arrange
        val workInfoSucceeded = createMockWorkInfo(WorkInfo.State.SUCCEEDED)
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns listOf(workInfoSucceeded)

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun isWorkRunning_returnsFalse_whenStateIsFailed() {
        // Arrange
        val workInfoFailed = createMockWorkInfo(WorkInfo.State.FAILED)
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns listOf(workInfoFailed)

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun isWorkRunning_returnsFalse_whenStateIsCancelled() {
        // Arrange
        val workInfoCancelled = createMockWorkInfo(WorkInfo.State.CANCELLED)
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns listOf(workInfoCancelled)

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun isWorkRunning_returnsFalse_whenNoWorkInfo() {
        // Arrange
        every {
            mockWorkManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
        } returns emptyList()

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(false, result)
    }
}
