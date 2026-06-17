package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listener lifecycle contract: a stale [FirestoreListenerCoordinator.stopListening] coroutine
 * must not detach a listener attached by a newer [FirestoreListenerCoordinator.startListening].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreListenerCoordinatorLifecycleTest {
    @Test
    fun staleStopDoesNotDetachNewerStart() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val capturedRegistration = mockk<FirestoreSyncEventListenerRegistration>(relaxed = true)

            val feedClient = mockk<FirestoreSyncEventFeedClient>(relaxed = true)
            coEvery { feedClient.attachLiveListener(any(), any(), any()) } returns capturedRegistration

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    feedClient = feedClient,
                )

            coordinator.stopListening()
            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            coVerify(exactly = 1) { feedClient.attachLiveListener(any(), any(), any()) }
            coVerify(exactly = 0) { capturedRegistration.remove() }
        }

    @Test
    fun staleStartDoesNotAttachAfterNewerStop() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val capturedRegistration = mockk<FirestoreSyncEventListenerRegistration>(relaxed = true)

            val feedClient = mockk<FirestoreSyncEventFeedClient>(relaxed = true)
            coEvery { feedClient.attachLiveListener(any(), any(), any()) } returns capturedRegistration

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    feedClient = feedClient,
                )

            coordinator.startListening()
            coordinator.stopListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            coVerify(exactly = 0) { feedClient.attachLiveListener(any(), any(), any()) }
            coVerify(exactly = 0) { capturedRegistration.remove() }
        }

    @Test
    fun detachAllListenersForceDetachesRegardlessOfPendingOperations() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val capturedRegistration = mockk<FirestoreSyncEventListenerRegistration>(relaxed = true)

            val feedClient = mockk<FirestoreSyncEventFeedClient>(relaxed = true)
            coEvery { feedClient.attachLiveListener(any(), any(), any()) } returns capturedRegistration

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    feedClient = feedClient,
                )

            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            coordinator.detachAllListeners()
            advanceUntilIdle()

            coVerify(exactly = 1) { capturedRegistration.remove() }
        }

    private fun buildCoordinator(
        externalScope: kotlinx.coroutines.CoroutineScope,
        feedClient: FirestoreSyncEventFeedClient,
    ): FirestoreListenerCoordinator {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val cryptoManager = mockk<CryptoManagerPort>()
        every { cryptoManager.hasCachedKey() } returns true
        val ctx =
            FirestoreSyncContext(
                firestore = mockk<FirebaseFirestore>(relaxed = true),
                daos = testFirestoreSyncDaos(),
                localEncryption = localEncryption,
                sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
                firestoreSensitiveFieldRepairer = mockk(relaxed = true),
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                apiKeyManager = mockk(relaxed = true),
                embeddingCoordinator = mockk(relaxed = true),
                userIdProvider = { "test-user-id" },
                purgeInProgress = AtomicBoolean(false),
            )
        return FirestoreListenerCoordinator(
            ctx = ctx,
            syncEventFeedClient = feedClient,
            syncEventApplier = mockk(relaxed = true),
            syncStateStore = FirestoreSyncStateStore(),
            externalScope = externalScope,
        )
    }
}
