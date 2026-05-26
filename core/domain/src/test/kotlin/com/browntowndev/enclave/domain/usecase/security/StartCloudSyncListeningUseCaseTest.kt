package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartCloudSyncListeningUseCaseTest {
    private val cloudSyncPort = mockk<CloudSyncPort>()
    private lateinit var useCase: StartCloudSyncListeningUseCase

    @BeforeEach
    fun setUp() {
        useCase = StartCloudSyncListeningUseCase(cloudSyncPort)
        every { cloudSyncPort.startListening() } returns Unit
        coEvery { cloudSyncPort.getRemainingEventsCount() } returns Result.success(0L)
    }

    @Test
    fun `SCN-USECASE-001 upload replay and listener start occur in order`() =
        runTest {
            coEvery { cloudSyncPort.syncAllLocalDataToCloud() } returns Result.success(Unit)
            coEvery { cloudSyncPort.syncRemoteChangesFromCloud() } returnsMany
                listOf(
                    Result.success(
                        CloudSyncEventReplayResult(
                            eventsApplied = 200,
                            lastAppliedSequence = 200,
                            hasMore = true,
                        ),
                    ),
                    Result.success(
                        CloudSyncEventReplayResult(
                            eventsApplied = 1,
                            lastAppliedSequence = 201,
                            hasMore = false,
                        ),
                    ),
                )

            val result = useCase(uploadLocalFirst = true)

            assertTrue(result.isSuccess)
            coVerifySequence {
                cloudSyncPort.syncAllLocalDataToCloud()
                cloudSyncPort.getRemainingEventsCount()
                cloudSyncPort.syncRemoteChangesFromCloud()
                cloudSyncPort.syncRemoteChangesFromCloud()
                cloudSyncPort.startListening()
            }
        }

    @Test
    fun `SCN-USECASE-002 upload failure prevents replay and listener start`() =
        runTest {
            coEvery { cloudSyncPort.syncAllLocalDataToCloud() } returns
                Result.failure(IllegalStateException("Vault locked"))

            val result = useCase(uploadLocalFirst = true)

            coVerify(exactly = 0) { cloudSyncPort.syncRemoteChangesFromCloud() }
            verify(exactly = 0) { cloudSyncPort.startListening() }
            val exception = result.exceptionOrNull()
            assertTrue(exception is IllegalStateException)
            assertEquals("Vault locked", exception?.message)
        }

    @Test
    fun `SCN-USECASE-003 replay failure prevents listener start`() =
        runTest {
            coEvery { cloudSyncPort.syncRemoteChangesFromCloud() } returns
                Result.failure(IllegalStateException("missing required parent chats/chat-1 for messages/message-1"))

            val result = useCase(uploadLocalFirst = false)

            verify(exactly = 0) { cloudSyncPort.startListening() }
            val exception = result.exceptionOrNull()
            assertTrue(exception is IllegalStateException)
            assertEquals("missing required parent chats/chat-1 for messages/message-1", exception?.message)
        }

    @Test
    fun `SCN-CHECKPOINT-001 large replay is page resumable without skipping events`() =
        runTest {
            coEvery { cloudSyncPort.syncRemoteChangesFromCloud() } returnsMany
                listOf(
                    Result.success(
                        CloudSyncEventReplayResult(
                            eventsApplied = 200,
                            lastAppliedSequence = 200,
                            hasMore = true,
                        ),
                    ),
                    Result.success(
                        CloudSyncEventReplayResult(
                            eventsApplied = 200,
                            lastAppliedSequence = 400,
                            hasMore = true,
                        ),
                    ),
                    Result.success(
                        CloudSyncEventReplayResult(
                            eventsApplied = 1,
                            lastAppliedSequence = 401,
                            hasMore = false,
                        ),
                    ),
                )

            val result = useCase(uploadLocalFirst = false)

            assertTrue(result.isSuccess)
            coVerify(exactly = 3) { cloudSyncPort.syncRemoteChangesFromCloud() }
            verify(exactly = 1) { cloudSyncPort.startListening() }
        }
}
