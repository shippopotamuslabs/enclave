@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase SDK surface (FirebaseFirestore / FirebaseFunctions filler), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreSyncFeedIntegrityRecoveryTest {
    private lateinit var metaClient: FakeFirestoreSyncFeedMetaClient
    private lateinit var callableClient: FakeFirestoreSyncFeedCallableClient
    private lateinit var entityReconciler: FakeFirestoreSyncEntityReconciler
    private lateinit var syncStateStore: FakeSyncStateStore
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var recovery: FirestoreSyncFeedIntegrityRecovery

    @BeforeEach
    fun setUp() {
        metaClient = FakeFirestoreSyncFeedMetaClient(mockk<FirebaseFirestore>(relaxed = true))
        callableClient = FakeFirestoreSyncFeedCallableClient(mockk<FirebaseFunctions>(relaxed = true))
        entityReconciler = FakeFirestoreSyncEntityReconciler()
        syncStateStore = FakeSyncStateStore()
        loggingPort = FakeLoggingPort()
        recovery =
            FirestoreSyncFeedIntegrityRecovery(
                metaClient = metaClient,
                callableClient = callableClient,
                entityReconciler = entityReconciler,
                syncStateStore = syncStateStore,
                loggingPort = loggingPort,
            )
    }

    @Test
    fun `recoverFromSequenceGap skips reconciliation when missing event arrives during verify`() =
        runTest {
            metaClient.awaitSyncEventResult = true

            recovery.recoverFromSequenceGap(USER_ID, expectedSequence = 4L)

            assertEquals(listOf(USER_ID to 4L), metaClient.awaitSyncEventCalls)
            assertTrue(entityReconciler.reconcileCalls.isEmpty())
            assertTrue(syncStateStore.setLastAppliedCalls.isEmpty())
        }

    @Test
    fun `recoverFromSequenceGap reconciles and advances cursor on verified gap`() =
        runTest {
            metaClient.awaitSyncEventResult = false
            metaClient.feedStatus =
                FirestoreSyncFeedStatus(
                    nextSequence = 10L,
                    feedInitialized = true,
                    feedEpoch = 2L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            entityReconciler.reconcileResult =
                FirestoreSyncEntityReconciler.ReconcileResult(upsertsApplied = 3, tombstonesApplied = 1)

            recovery.recoverFromSequenceGap(USER_ID, expectedSequence = 4L)

            assertEquals(listOf(USER_ID), entityReconciler.reconcileCalls)
            assertEquals(listOf(USER_ID to 9L), syncStateStore.setLastAppliedCalls)
            assertEquals(listOf(USER_ID to 2L), syncStateStore.setFeedSchemaVersionCalls)
        }

    @Test
    fun `recoverFromSequenceGap throws when expected sequence is beyond server tail`() =
        runTest {
            metaClient.awaitSyncEventResult = false
            metaClient.feedStatus =
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
            metaClient.feedStatus =
                FirestoreSyncFeedStatus(
                    nextSequence = 0L,
                    feedInitialized = false,
                    feedEpoch = 1L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            callableClient.backfillResult =
                BackfillSyncFeedResult(status = "initialized", eventsWritten = 5, nextSequence = 5L)

            recovery.ensureFeedBootstrapped(USER_ID)

            assertEquals(listOf(USER_ID), callableClient.backfillCalls)
        }

    @Test
    fun `ensureFeedBootstrapped marks initialized without full backfill when events already exist`() =
        runTest {
            metaClient.feedStatus =
                FirestoreSyncFeedStatus(
                    nextSequence = 79L,
                    feedInitialized = false,
                    feedEpoch = 1L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            callableClient.markInitializedResult =
                BackfillSyncFeedResult(status = "initialized", eventsWritten = 0, nextSequence = 79L)

            recovery.ensureFeedBootstrapped(USER_ID)

            assertEquals(listOf(USER_ID), callableClient.markInitializedCalls)
            assertTrue(callableClient.backfillCalls.isEmpty())
        }

    @Test
    fun `recoverIfFeedEpochChanged reconciles when local epoch differs`() =
        runTest {
            metaClient.feedStatus =
                FirestoreSyncFeedStatus(
                    nextSequence = 20L,
                    feedInitialized = true,
                    feedEpoch = 3L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            syncStateStore.feedSchemaVersionValue = 1L

            recovery.recoverIfFeedEpochChanged(USER_ID)

            assertEquals(listOf(USER_ID), entityReconciler.reconcileCalls)
            assertEquals(listOf(USER_ID to 19L), syncStateStore.setLastAppliedCalls)
            assertEquals(listOf(USER_ID to 3L), syncStateStore.setFeedSchemaVersionCalls)
        }

    @Test
    fun `recoverIfFeedEpochChanged adopts server epoch when local epoch is unset`() =
        runTest {
            metaClient.feedStatus =
                FirestoreSyncFeedStatus(
                    nextSequence = 79L,
                    feedInitialized = false,
                    feedEpoch = 1L,
                    oldestRetainedSequence = 1L,
                    schemaVersion = 1L,
                )
            syncStateStore.feedSchemaVersionValue = 0L

            recovery.recoverIfFeedEpochChanged(USER_ID)

            assertTrue(entityReconciler.reconcileCalls.isEmpty())
            assertEquals(listOf(USER_ID to 1L), syncStateStore.setFeedSchemaVersionCalls)
            assertTrue(syncStateStore.setLastAppliedCalls.isEmpty())
        }

    private companion object {
        const val USER_ID = "user-1"
    }
}
