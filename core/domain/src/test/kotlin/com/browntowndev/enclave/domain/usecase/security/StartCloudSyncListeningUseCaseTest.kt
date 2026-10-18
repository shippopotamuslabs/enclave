package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartCloudSyncListeningUseCaseTest {
    private val cloudSyncPort = FakeCloudSyncPort()
    private lateinit var useCase: StartCloudSyncListeningUseCase

    @BeforeEach
    fun setUp() {
        useCase = StartCloudSyncListeningUseCase(cloudSyncPort)
        cloudSyncPort.remainingEventsResult = Result.success(0L)
        cloudSyncPort.bootstrapResult = Result.success(Unit)
    }

    @Test
    fun `SCN-USECASE-001 upload replay and listener start occur in order`() =
        runTest {
            cloudSyncPort.syncAllResult = Result.success(Unit)
            cloudSyncPort.syncRemoteResults.addAll(
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
                ),
            )

            val result = useCase(uploadLocalFirst = true)

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    "syncAll",
                    "getRemainingEventsCount",
                    "syncRemote",
                    "syncRemote",
                    "bootstrapFeatureSettings",
                    "startListening",
                ),
                cloudSyncPort.calls,
            )
        }

    @Test
    fun `SCN-USECASE-002 upload failure prevents replay and listener start`() =
        runTest {
            cloudSyncPort.syncAllResult =
                Result.failure(IllegalStateException("Vault locked"))

            val result = useCase(uploadLocalFirst = true)

            assertEquals(0, cloudSyncPort.count("syncRemote"))
            assertEquals(0, cloudSyncPort.count("startListening"))
            val exception = result.exceptionOrNull()
            assertTrue(exception is IllegalStateException)
            assertEquals("Vault locked", exception?.message)
        }

    @Test
    fun `SCN-USECASE-003 replay failure prevents listener start`() =
        runTest {
            cloudSyncPort.syncRemoteResult =
                Result.failure(IllegalStateException("missing required parent chats/chat-1 for messages/message-1"))

            val result = useCase(uploadLocalFirst = false)

            assertEquals(0, cloudSyncPort.count("startListening"))
            val exception = result.exceptionOrNull()
            assertTrue(exception is IllegalStateException)
            assertEquals("missing required parent chats/chat-1 for messages/message-1", exception?.message)
        }

    @Test
    fun `SCN-CHECKPOINT-001 large replay is page resumable without skipping events`() =
        runTest {
            cloudSyncPort.syncRemoteResults.addAll(
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
                ),
            )

            val result = useCase(uploadLocalFirst = false)

            assertTrue(result.isSuccess)
            assertEquals(3, cloudSyncPort.count("syncRemote"))
            assertEquals(1, cloudSyncPort.count("bootstrapFeatureSettings"))
            assertEquals(1, cloudSyncPort.count("startListening"))
        }

    @Test
    fun `search tool settings bootstrap failure prevents listener start`() =
        runTest {
            cloudSyncPort.syncRemoteResult =
                Result.success(
                    CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0,
                        hasMore = false,
                    ),
                )
            cloudSyncPort.bootstrapResult =
                Result.failure(IllegalStateException("bootstrap failed"))

            val result = useCase(uploadLocalFirst = false)

            assertEquals(0, cloudSyncPort.count("startListening"))
            assertEquals("bootstrap failed", result.exceptionOrNull()?.message)
        }
}
