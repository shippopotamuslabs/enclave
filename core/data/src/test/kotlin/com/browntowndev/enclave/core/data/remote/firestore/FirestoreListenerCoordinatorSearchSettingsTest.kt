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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Coverage for the vault-unlock gate on `FirestoreListenerCoordinator.startListening()`
 * with respect to the new `search_tool_settings` collection. The new handler rides
 * the existing `sync_events` live listener; we do NOT introduce a new gate. The
 * assertions look at whether the coordinator attached its sync_events listener
 * (which would subsequently route events through `FirestoreSyncEventDispatcher`
 * to entity handlers, including `SearchToolSettingsSyncHandler`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreListenerCoordinatorSearchSettingsTest {
    @Test
    fun `S-LISTENER-001 listener does not apply search_tool_settings events while vault is locked`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val cryptoManager = mockk<CryptoManagerPort>()
            every { cryptoManager.hasCachedKey() } returns false

            val feedClient = mockk<FirestoreSyncEventFeedClient>(relaxed = true)
            val dispatcher = mockk<FirestoreSyncEventDispatcher>(relaxed = true)
            val stateStore = FirestoreSyncStateStore()

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    cryptoManager = cryptoManager,
                    feedClient = feedClient,
                    dispatcher = dispatcher,
                    stateStore = stateStore,
                )

            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()

            // Gate contract: when the vault is locked, attachLiveListener is never
            // called. This guarantees the search_tool_settings handler (and every
            // other handler downstream of the sync_events feed) is not invoked.
            coVerify(exactly = 0) { feedClient.attachLiveListener(any(), any(), any()) }
        }

    @Test
    fun `S-LISTENER-002 listener applies search_tool_settings events once the vault is unlocked`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val externalScope = TestScope(testDispatcher)
            val gateState = AtomicReference(false)
            val cryptoManager = mockk<CryptoManagerPort>()
            every { cryptoManager.hasCachedKey() } answers { gateState.get() }

            // The first startListening() call must observe a locked vault. The
            // listener callback is captured into a local var so we can drive a
            // synthetic event after unlocking the vault.
            var listenerCallback: (suspend (List<FirestoreSyncEvent>) -> Unit)? = null
            val capturedRegistration = mockk<FirestoreSyncEventListenerRegistration>(relaxed = true)

            val feedClient = mockk<FirestoreSyncEventFeedClient>(relaxed = true)
            coEvery {
                feedClient.attachLiveListener(any(), any(), any())
            } answers {
                listenerCallback = thirdArg<suspend (List<FirestoreSyncEvent>) -> Unit>()
                capturedRegistration
            }

            val dispatcher = mockk<FirestoreSyncEventDispatcher>(relaxed = true)
            val stateStore = FirestoreSyncStateStore()

            val coordinator =
                buildCoordinator(
                    externalScope = externalScope,
                    cryptoManager = cryptoManager,
                    feedClient = feedClient,
                    dispatcher = dispatcher,
                    stateStore = stateStore,
                )

            // First start: vault is locked → no listener attached.
            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            coVerify(exactly = 0) { feedClient.attachLiveListener(any(), any(), any()) }

            // Unlock the vault and try again. The second startListening() call
            // observes hasCachedKey() == true and must attach the listener.
            gateState.set(true)
            coordinator.startListening()
            externalScope.advanceUntilIdle()
            advanceUntilIdle()
            runCurrent()

            val callback = listenerCallback
            assertNotNull(callback, "after unlock, startListening must attach a listener")

            // Drive a synthetic live event for the search_tool_settings singleton.
            val syntheticEvent =
                FirestoreSyncEvent(
                    sequence = 1L,
                    schemaVersion = 1,
                    operation = "upsert",
                    collectionPath = "search_tool_settings",
                    entityType = "search_tool_settings",
                    entityId = "main",
                    entityPath = "users/test-user-id/search_tool_settings/main",
                    sourceExists = true,
                    sourceUpdatedAt = 1700000000000L,
                    sourceDeleted = false,
                    deviceId = "device-1",
                    clientMutationId = "mut-1",
                    triggerEventId = "evt-1",
                    emittedAtMillis = 1700000000000L,
                )
            callback?.invoke(listOf(syntheticEvent))
            externalScope.advanceUntilIdle()
            advanceUntilIdle()

            // The event must have been dispatched to the applier exactly once.
            coVerify(exactly = 1) { dispatcher.apply("test-user-id", syntheticEvent) }
        }

    private fun buildCoordinator(
        externalScope: kotlinx.coroutines.CoroutineScope,
        cryptoManager: CryptoManagerPort,
        feedClient: FirestoreSyncEventFeedClient,
        dispatcher: FirestoreSyncEventDispatcher,
        stateStore: FirestoreSyncStateStore,
    ): FirestoreListenerCoordinator {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
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
            syncEventApplier = dispatcher,
            syncStateStore = stateStore,
            externalScope = externalScope,
        )
    }
}
