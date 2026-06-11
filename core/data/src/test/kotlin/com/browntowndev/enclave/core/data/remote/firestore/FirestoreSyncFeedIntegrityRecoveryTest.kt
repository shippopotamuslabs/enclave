package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.domain.port.inference.LoggingPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FirestoreSyncFeedIntegrityRecoveryTest {
    private val metaClient = mockk<FirestoreSyncFeedMetaClient>()
    private val callableClient = mockk<FirestoreSyncFeedCallableClient>(relaxed = true)
    private val entityReconciler = mockk<FirestoreSyncEntityReconciler>(relaxed = true)
    private val syncStateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    private val recovery =
        FirestoreSyncFeedIntegrityRecovery(
            metaClient = metaClient,
            callableClient = callableClient,
            entityReconciler = entityReconciler,
            syncStateStore = syncStateStore,
            loggingPort = loggingPort,
        )

    @Test
    fun `recoverFromSequenceGap skips reconciliation when missing event arrives during verify`() =
        runTest {
            coEvery { metaClient.awaitSyncEvent(USER_ID, 4L) } returns true

            recovery.recoverFromSequenceGap(USER_ID, expectedSequence = 4L)

            coVerify(exactly = 0) { entityReconciler.reconcileFromCloudEntitySources(any()) }
            coVerify(exactly = 0) { syncStateStore.setLastAppliedSequence(any(), any()) }
        }

    @Test
    fun `recoverFromSequenceGap reconciles and advances cursor on verified gap`() =
        runTest {
            coEvery { metaClient.awaitSyncEvent(USER_ID, 4L) } returns false
            coEvery { metaClient.readFeedStatus(USER_ID) } returns
                FirestoreSyncFeedStatus(
                    nextSequence = 10L,
                    feedInitialized = true,
                    feedEpoch = 2L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            coEvery { entityReconciler.reconcileFromCloudEntitySources(USER_ID) } returns
                FirestoreSyncEntityReconciler.ReconcileResult(upsertsApplied = 3, tombstonesApplied = 1)

            recovery.recoverFromSequenceGap(USER_ID, expectedSequence = 4L)

            coVerify(exactly = 1) { entityReconciler.reconcileFromCloudEntitySources(USER_ID) }
            coVerify { syncStateStore.setLastAppliedSequence(USER_ID, 9L) }
            coVerify { syncStateStore.setFeedSchemaVersion(USER_ID, 2L) }
        }

    @Test
    fun `recoverFromSequenceGap throws when expected sequence is beyond server tail`() =
        runTest {
            coEvery { metaClient.awaitSyncEvent(USER_ID, 12L) } returns false
            coEvery { metaClient.readFeedStatus(USER_ID) } returns
                FirestoreSyncFeedStatus(
                    nextSequence = 10L,
                    feedInitialized = true,
                    feedEpoch = 1L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )

            val failure =
                runCatching {
                    recovery.recoverFromSequenceGap(USER_ID, expectedSequence = 12L)
                }.exceptionOrNull()
            assertEquals(SyncFeedSequenceGapException::class.java, failure?.javaClass)
            assertEquals(12L, (failure as SyncFeedSequenceGapException).expectedSequence)
        }

    @Test
    fun `ensureFeedBootstrapped triggers callable when feed is uninitialized`() =
        runTest {
            coEvery { metaClient.readFeedStatus(USER_ID) } returns
                FirestoreSyncFeedStatus(
                    nextSequence = 0L,
                    feedInitialized = false,
                    feedEpoch = 1L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            coEvery { callableClient.backfillIfNeeded(USER_ID) } returns
                BackfillSyncFeedResult(status = "initialized", eventsWritten = 5, nextSequence = 5L)

            recovery.ensureFeedBootstrapped(USER_ID)

            coVerify(exactly = 1) { callableClient.backfillIfNeeded(USER_ID) }
        }

    @Test
    fun `ensureFeedBootstrapped marks initialized without full backfill when events already exist`() =
        runTest {
            coEvery { metaClient.readFeedStatus(USER_ID) } returns
                FirestoreSyncFeedStatus(
                    nextSequence = 79L,
                    feedInitialized = false,
                    feedEpoch = 1L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            coEvery { callableClient.markFeedInitializedIfNeeded(USER_ID) } returns
                BackfillSyncFeedResult(status = "initialized", eventsWritten = 0, nextSequence = 79L)

            recovery.ensureFeedBootstrapped(USER_ID)

            coVerify(exactly = 1) { callableClient.markFeedInitializedIfNeeded(USER_ID) }
            coVerify(exactly = 0) { callableClient.backfillIfNeeded(USER_ID) }
        }

    @Test
    fun `recoverIfFeedEpochChanged reconciles when local epoch differs`() =
        runTest {
            coEvery { metaClient.readFeedStatus(USER_ID) } returns
                FirestoreSyncFeedStatus(
                    nextSequence = 20L,
                    feedInitialized = true,
                    feedEpoch = 3L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            coEvery { syncStateStore.feedSchemaVersion(USER_ID) } returns 1L

            recovery.recoverIfFeedEpochChanged(USER_ID)

            coVerify(exactly = 1) { entityReconciler.reconcileFromCloudEntitySources(USER_ID) }
            coVerify { syncStateStore.setLastAppliedSequence(USER_ID, 19L) }
            coVerify { syncStateStore.setFeedSchemaVersion(USER_ID, 3L) }
        }

    @Test
    fun `recoverIfFeedEpochChanged adopts server epoch when local epoch is unset`() =
        runTest {
            coEvery { metaClient.readFeedStatus(USER_ID) } returns
                FirestoreSyncFeedStatus(
                    nextSequence = 79L,
                    feedInitialized = false,
                    feedEpoch = 1L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            coEvery { syncStateStore.feedSchemaVersion(USER_ID) } returns 0L

            recovery.recoverIfFeedEpochChanged(USER_ID)

            coVerify(exactly = 0) { entityReconciler.reconcileFromCloudEntitySources(any()) }
            coVerify { syncStateStore.setFeedSchemaVersion(USER_ID, 1L) }
            coVerify(exactly = 0) { syncStateStore.setLastAppliedSequence(any(), any()) }
        }

    private companion object {
        const val USER_ID = "user-1"
    }
}
