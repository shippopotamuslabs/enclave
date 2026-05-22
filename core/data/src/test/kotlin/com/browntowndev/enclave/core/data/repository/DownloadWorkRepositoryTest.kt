package com.browntowndev.enclave.core.data.repository

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.enclave.domain.model.download.ModelConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

class DownloadWorkRepositoryTest {
    private lateinit var mockWorkManager: WorkManager
    private lateinit var repository: DownloadWorkRepository

    private val workId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        mockWorkManager = mockk(relaxed = true)
        // Inject UnconfinedTestDispatcher to bypass threading issues in tests
        repository = DownloadWorkRepository(mockWorkManager, kotlinx.coroutines.test.UnconfinedTestDispatcher())
        // Mock the Log class to prevent RuntimeException in unit tests
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    private fun createMockWorkInfo(state: WorkInfo.State): WorkInfo =
        mockk {
            every { id } returns workId
            every { this@mockk.state } returns state
            every { tags } returns emptySet()
            every { outputData } returns androidx.work.Data.EMPTY
            every { progress } returns androidx.work.Data.EMPTY
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_emitsWorkInfo_whenWorkRunning() =
        runTest {
            val workInfoRunning = createMockWorkInfo(WorkInfo.State.RUNNING)

            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns
                flowOf(listOf(workInfoRunning))

            val result = repository.observeDownloadProgress().first()

            assertEquals(WorkInfo.State.RUNNING, result?.state)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkSucceeds() =
        runTest {
            val workInfoSucceeded = createMockWorkInfo(WorkInfo.State.SUCCEEDED)

            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns
                flowOf(listOf(workInfoSucceeded))

            val result = repository.observeDownloadProgress().first()

            assertEquals(WorkInfo.State.SUCCEEDED, result?.state)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_emitsTerminalState_whenWorkFails() =
        runTest {
            val workInfoFailed = createMockWorkInfo(WorkInfo.State.FAILED)

            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns
                flowOf(listOf(workInfoFailed))

            val result = repository.observeDownloadProgress().first()

            assertEquals(WorkInfo.State.FAILED, result?.state)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeDownloadProgress_returnsNull_whenWorkListEmpty() =
        runTest {
            every { mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG) } returns
                flowOf(emptyList())

            val result = repository.observeDownloadProgress().first()

            assertEquals(null, result)
        }

    @Test
    fun getWorkId_returnsWorkId_whenWorkRunning() =
        runTest {
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
    fun getWorkId_returnsNull_whenNoWorkRunning() =
        runTest {
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

    @ParameterizedTest
    @CsvSource(
        "ENQUEUED, true",
        "RUNNING, true",
        "BLOCKED, true",
        "SUCCEEDED, false",
        "FAILED, false",
        "CANCELLED, false",
    )
    fun isWorkRunning_returnsExpectedResult_basedOnState(
        state: WorkInfo.State,
        expected: Boolean,
    ) = runTest {
        // Arrange
        val workInfo = createMockWorkInfo(state)
        every {
            mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        } returns flowOf(listOf(workInfo))

        // Act
        val result = repository.isWorkRunning()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun isWorkRunning_returnsFalse_whenNoWorkInfo() =
        runTest {
            // Arrange
            every {
                mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
            } returns flowOf(emptyList())

            // Act
            val result = repository.isWorkRunning()

            // Assert
            assertEquals(false, result)
        }

    @Test
    fun isWorkRunning_returnsFalse_whenExceptionThrown() =
        runTest {
            // Arrange
            every {
                mockWorkManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
            } throws RuntimeException("WorkManager error")

            // Act
            val result = repository.isWorkRunning()

            // Assert
            assertEquals(false, result)
        }
}
