package com.browntowndev.enclave.core.data.remote.firestore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.browntowndev.enclave.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.enclave.core.data.remote.firestore.handlers.SearchToolSettingsSyncHandler
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pipeline coverage for the new `SearchToolSettingsSyncHandler` riding the
 * `FirestorePendingSyncEngine`. The test captures the writes the engine dispatches
 * and asserts the path, payload shape, and post-write queue state.
 */
class SearchToolSettingsPipelineTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `S-HANDLER-003 syncPendingChanges writes once to search_tool_settings main with the four user-facing fields`() =
        runTest {
            val captured = CapturedBatchWrites()
            val settingsDataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings-${System.nanoTime()}.preferences_pb").toFile() },
                )
            val queueDataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("queue-${System.nanoTime()}.preferences_pb").toFile() },
                )
            val queue = SearchToolSettingsSyncQueue(dataStore = queueDataStore)
            val repository =
                SettingsRepositoryImpl(
                    dataStore = settingsDataStore,
                    apiKeyManager = mockk<ApiKeyManager>(relaxed = true),
                    searchToolSettingsSyncQueue = queue,
                )
            repository.updateSearchProvider(SearchProvider.EXA)
            repository.updateSearchEnabled(true)
            repository.updateXSearchEnabled(true)
            assertTrue(queue.isPending(), "precondition: mutators must mark the queue pending")

            val ctx = createContext(queue = queue, settingsRepository = repository, captured = captured)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val engine =
                FirestorePendingSyncEngine(
                    ctx = ctx,
                    adapters = listOf(handler),
                    syncStateStore = DeterministicPipelineSyncStateStore(),
                )

            val result = engine.syncPendingChanges(userId = "test-user-id")

            assertTrue(result.isSuccess, "syncPendingChanges must succeed")
            assertEquals(
                1,
                captured.batchInvocations,
                "exactly one runBatch must be dispatched for the singleton",
            )
            assertEquals(
                listOf("users/test-user-id/search_tool_settings/main"),
                captured.documentPaths,
                "the engine must write to users/{uid}/search_tool_settings/main",
            )
            val write = captured.writes.single()
            assertEquals(true, write["search_enabled"], "search_enabled must round-trip")
            assertEquals(true, write["x_search_enabled"], "x_search_enabled must round-trip")
            assertEquals("EXA", write["search_provider"], "search_provider must round-trip")
            val updatedAt = write["updated_at"]
            assertTrue(updatedAt is Long && updatedAt > 0L, "updated_at must be a positive Long")
            assertTrue(write.containsKey("_sync_device_id"), "the engine must add _sync_device_id")
            assertTrue(write.containsKey("_sync_client_mutation_id"), "the engine must add _sync_client_mutation_id")
            assertTrue(write.containsKey("_sync_written_at"), "the engine must add _sync_written_at")
            assertFalse(
                queue.isPending(),
                "the queue must be cleared after a successful round-trip (markSynced ran)",
            )
        }

    @Test
    fun `syncAllLocalDataToCloud is idempotent across the two-phase claim`() =
        runTest {
            // Regression test for the double-claim bug: `syncAllLocalDataToCloud` calls
            // `claimPending` on every adapter, then `syncPendingChangesInternal` calls
            // `claimPending` again per adapter. The queue's two-key design is what
            // keeps the captured `updatedAt` alive across both claims.
            val captured = CapturedBatchWrites()
            val settingsDataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings-${System.nanoTime()}.preferences_pb").toFile() },
                )
            val queueDataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("queue-${System.nanoTime()}.preferences_pb").toFile() },
                )
            val queue = SearchToolSettingsSyncQueue(dataStore = queueDataStore)
            val repository =
                SettingsRepositoryImpl(
                    dataStore = settingsDataStore,
                    apiKeyManager = mockk<ApiKeyManager>(relaxed = true),
                    searchToolSettingsSyncQueue = queue,
                )
            repository.updateSearchEnabled(true)
            assertTrue(queue.isPending(), "precondition: the mutator must mark the queue pending")

            val ctx = createContext(queue = queue, settingsRepository = repository, captured = captured)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val engine =
                FirestorePendingSyncEngine(
                    ctx = ctx,
                    adapters = listOf(handler),
                    syncStateStore = DeterministicPipelineSyncStateStore(),
                )

            val result = engine.syncAllLocalDataToCloud(userId = "test-user-id")

            assertTrue(result.isSuccess, "syncAllLocalDataToCloud must succeed")
            assertEquals(
                1,
                captured.batchInvocations,
                "exactly one runBatch must be dispatched even with the two-phase claim",
            )
            assertEquals(
                listOf("users/test-user-id/search_tool_settings/main"),
                captured.documentPaths,
            )
            assertFalse(
                queue.isPending(),
                "the queue must be cleared after a successful round-trip",
            )
        }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Real-path coverage for S-LISTENER-002: a synthetic sync event is driven
     * through the real `FirestoreSyncEventApplier` (not a mocked dispatcher) into
     * the real `SearchToolSettingsSyncHandler.upsertLocal`, which writes through
     * the real `SettingsRepositoryImpl.applyRemoteSearchToolSettings` into the
     * real DataStore. The test asserts that `searchEnabled` becomes `true` in the
     * local DataStore and the queue stays clear (no pending mark is created by a
     * pure remote apply).
     */
    @Test
    fun `S-LISTENER-002 real applier applies search_tool_settings event into the local DataStore`() =
        runTest {
            val sourceDocuments = RecordingSourceDocumentStore()
            val settingsDataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings-${System.nanoTime()}.preferences_pb").toFile() },
                )
            val queueDataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("queue-${System.nanoTime()}.preferences_pb").toFile() },
                )
            val queue = SearchToolSettingsSyncQueue(dataStore = queueDataStore)
            val repository =
                SettingsRepositoryImpl(
                    dataStore = settingsDataStore,
                    apiKeyManager = mockk<ApiKeyManager>(relaxed = true),
                    searchToolSettingsSyncQueue = queue,
                )
            // Sanity: local default for searchEnabled is false, queue is not pending.
            assertFalse(repository.settingsFlow.first().searchEnabled)
            assertFalse(queue.isPending())

            val ctx = createContext(queue = queue, settingsRepository = repository, captured = CapturedBatchWrites())
            val handler = SearchToolSettingsSyncHandler(ctx)
            val localStore = SearchToolSettingsLocalStore(handler)
            val stateStore = FirestoreSyncStateStore()
            val applier = FirestoreSyncEventApplier(
                sourceDocuments = sourceDocuments,
                localStore = localStore,
                stateStore = stateStore,
            )

            // Synthetic remote snapshot: search_enabled=true, x_search_enabled=true, EXA.
            val updatedAt = 1700000000000L
            val remotePayload =
                mapOf<String, Any?>(
                    "search_enabled" to true,
                    "x_search_enabled" to true,
                    "search_provider" to "EXA",
                    "updated_at" to updatedAt,
                )
            sourceDocuments.put("users/test-user-id/search_tool_settings/main", remotePayload)
            val snapshot = buildSearchToolSettingsSnapshot("search_tool_settings", "main", remotePayload)

            val event =
                FirestoreSyncEvent(
                    sequence = 1L,
                    schemaVersion = 1,
                    operation = "upsert",
                    collectionPath = "search_tool_settings",
                    entityType = "search_tool_settings",
                    entityId = "main",
                    entityPath = "users/test-user-id/search_tool_settings/main",
                    sourceExists = true,
                    sourceUpdatedAt = updatedAt,
                    sourceDeleted = false,
                    deviceId = "device-1",
                    clientMutationId = "mut-1",
                    triggerEventId = "evt-1",
                    emittedAtMillis = updatedAt,
                )

            // Drive the real applier — not a mocked dispatcher.
            val result = applier.apply("test-user-id", event)
            assertEquals(1, result.appliedCount, "the applier must report the event applied")
            assertEquals(1L, result.lastAppliedSequence)

            // The local DataStore now reflects the remote snapshot.
            val local = repository.settingsFlow.first()
            assertTrue(local.searchEnabled, "searchEnabled must be true after the real applier ran")
            assertTrue(local.xSearchEnabled, "xSearchEnabled must be true after the real applier ran")
            assertEquals(SearchProvider.EXA, local.searchProvider)
            assertFalse(
                queue.isPending(),
                "a pure remote apply must not create a pending mark on the local queue",
            )
        }

    /**
     * Test double: a `FirestoreSyncLocalStore` that routes the
     * `search_tool_settings` collection through the real
     * `SearchToolSettingsSyncHandler.upsertLocal` path. This is the seam the
     * listener's `FirestoreSyncEventApplier` uses to translate an event into a
     * local write.
     */
    private class SearchToolSettingsLocalStore(
        private val handler: SearchToolSettingsSyncHandler,
    ) : FirestoreSyncLocalStore {
        override suspend fun upsert(
            collectionPath: String,
            entityId: String,
            data: Map<String, Any?>,
            eventDeviceId: String?,
        ) {
            if (collectionPath != "search_tool_settings") return
            val snapshot = buildSearchToolSettingsSnapshot(collectionPath, entityId, data)
            val record = handler.fromFirestore(snapshot) ?: return
            handler.upsertLocal(record, snapshot, overwrite = false)
        }

        override suspend fun delete(
            collectionPath: String,
            entityId: String,
        ) = Unit

        override suspend fun withTransaction(block: suspend () -> Unit) {
            block()
        }
    }

    private class RecordingSourceDocumentStore : FirestoreSyncSourceDocumentStore {
        private val documents = mutableMapOf<String, Map<String, Any?>>()

        fun put(
            path: String,
            data: Map<String, Any?>,
        ) {
            documents[path] = data
        }

        override suspend fun getDocument(path: String): Map<String, Any?>? = documents[path]
    }

    private fun createContext(
        queue: SearchToolSettingsSyncQueue,
        settingsRepository: SettingsRepositoryImpl,
        captured: CapturedBatchWrites,
    ): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val batch = mockk<WriteBatch>(relaxed = true)
        val documentPathSlot = slot<String>()
        val dataSlot = slot<Map<String, Any?>>()
        every { batch.set(any<DocumentReference>(), capture(dataSlot), any()) } answers {
            captured.writes += dataSlot.captured
            batch
        }
        // The Kotlin lambda passed by the engine is SAM-converted to `WriteBatch.Function`
        // at runtime, so the matcher / slot must use the Java SAM type, not `Function1`.
        val blockSlot = slot<WriteBatch.Function>()
        every { firestore.runBatch(capture(blockSlot)) } answers {
            captured.batchInvocations++
            blockSlot.captured.apply(batch)
            Tasks.forResult(null)
        }
        every { firestore.document(capture(documentPathSlot)) } answers {
            captured.documentPaths += documentPathSlot.captured
            mockk<DocumentReference>(relaxed = true)
        }
        val repairer = mockk<FirestoreSensitiveFieldRepairer>()
        every { repairer.repairOutbound(any(), any(), any()) } answers { thirdArg() }
        return FirestoreSyncContext(
            firestore = firestore,
            daos = testFirestoreSyncDaos(),
            localEncryption = localEncryption,
            sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
            firestoreSensitiveFieldRepairer = repairer,
            cryptoManager = mockk<CryptoManagerPort>(relaxed = true),
            loggingPort = loggingPort,
            apiKeyManager = mockk(relaxed = true),
            embeddingCoordinator = mockk(relaxed = true),
            userIdProvider = { "test-user-id" },
            purgeInProgress = AtomicBoolean(false),
            searchToolSettingsSyncQueue = queue,
            settingsRepository = settingsRepository,
        )
    }

    private class CapturedBatchWrites {
        var batchInvocations: Int = 0
        val documentPaths: MutableList<String> = mutableListOf()
        val writes: MutableList<Map<String, Any?>> = mutableListOf()
    }

    private class DeterministicPipelineSyncStateStore : FirestoreSyncStateStore() {
        override suspend fun deviceId(): String = "device-a"

        override fun newClientMutationId(): String = "mutation-a"

        override fun currentTimeMillis(): Long = 1700000001000L
    }
}

/**
 * File-private helper used by [SearchToolSettingsPipelineTest] to assemble a
 * `DocumentSnapshot` that responds to `getString` / `getLong` / `getBoolean` in
 * the way `SearchToolSettingsSyncHandler.fromFirestore` expects.
 */
private fun buildSearchToolSettingsSnapshot(
    collectionPath: String,
    entityId: String,
    data: Map<String, Any?>,
): DocumentSnapshot {
    val parent = mockk<CollectionReference>(relaxed = true)
    every { parent.id } returns collectionPath
    val reference = mockk<DocumentReference>(relaxed = true)
    every { reference.parent } returns parent
    val snapshot = mockk<DocumentSnapshot>(relaxed = true)
    every { snapshot.id } returns entityId
    every { snapshot.reference } returns reference
    every { snapshot.exists() } returns true
    every { snapshot.data } returns data
    data.forEach { (field, value) ->
        when (value) {
            is String -> every { snapshot.getString(field) } returns value
            is Long -> every { snapshot.getLong(field) } returns value
            is Boolean -> every { snapshot.getBoolean(field) } returns value
            else -> Unit
        }
    }
    return snapshot
}
