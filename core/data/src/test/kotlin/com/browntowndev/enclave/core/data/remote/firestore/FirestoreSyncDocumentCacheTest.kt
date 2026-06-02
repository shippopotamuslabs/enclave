package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.FirestoreDataSource
import com.browntowndev.enclave.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.enclave.core.data.remote.FirestoreSyncDependenciesFacade
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.domain.model.sync.CloudSyncReplayProgress
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

class FirestoreSyncDocumentCacheTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val cache = FirestoreSyncDocumentCache(firestore)

    private fun mockDocument(
        path: String,
        data: Map<String, Any?>?,
    ): DocumentSnapshot {
        val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val snapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)

        every { firestore.document(path) } returns docRef
        every { docRef.get() } returns
            com.google.android.gms.tasks.Tasks
                .forResult(snapshot)
        every { docRef.path } returns path
        every { snapshot.reference } returns docRef
        every { snapshot.exists() } returns (data != null)
        every { snapshot.data } returns data
        return snapshot
    }

    private fun syncEvent(
        sequence: Long,
        operation: String = "upsert",
        collectionPath: String,
        entityId: String,
    ): FirestoreSyncEvent =
        FirestoreSyncEvent(
            sequence = sequence,
            schemaVersion = 1,
            operation = operation,
            collectionPath = collectionPath,
            entityType = collectionPath,
            entityId = entityId,
            entityPath = "users/user_123/$collectionPath/$entityId",
            sourceExists = operation == "upsert",
            sourceUpdatedAt = 1700000001000,
            sourceDeleted = operation == "delete",
            deviceId = null,
            clientMutationId = null,
            triggerEventId = "event-$sequence",
            emittedAtMillis = 1700000001001,
        )

    private fun createTestDataSource(
        firestore: FirebaseFirestore,
        syncStateStore: FirestoreSyncStateStore,
        syncEventFeedClient: FirestoreSyncEventFeedClient,
        syncEventApplier: FirestoreSyncEventDispatcher,
        documentCache: FirestoreSyncDocumentCache,
        externalScope: kotlinx.coroutines.CoroutineScope,
        chatDao: ChatDao = mockk(relaxed = true),
    ): FirestoreDataSource {
        val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
        every { cryptoManager.hasCachedKey() } returns true

        val facade =
            FirestoreSyncDependenciesFacade.forTests(
                firestore = firestore,
                chatSyncDaos =
                    ChatSyncDaos(
                        chatDao = chatDao,
                        messageDao = mockk(relaxed = true),
                        chatSummaryDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        messageTranscriptSegmentDao = mockk(relaxed = true),
                        messageInferenceTelemetryDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        documentDao = mockk(relaxed = true),
                        embeddingDao = mockk(relaxed = true),
                        firestoreSyncPlaceholderDao = mockk(relaxed = true),
                    ),
                configSyncDaos =
                    ConfigSyncDaos(
                        memoriesDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        apiCredentialsDao = mockk(relaxed = true),
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        utilityModelsDao = mockk(relaxed = true),
                    ),
                studioSyncDaos =
                    StudioSyncDaos(
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                    ),
                apiKeyManager = mockk(relaxed = true),
                cryptoManager = cryptoManager,
                loggingPort = mockk(relaxed = true),
                userIdProvider = { "user_123" },
                embeddingCoordinator = mockk(relaxed = true),
                localEncryption = mockk(relaxed = true),
                sensitiveSelfHealer = mockk(relaxed = true),
                purgeInProgress = AtomicBoolean(false),
                syncEventFeedClient = syncEventFeedClient,
                syncEventApplier = syncEventApplier,
                syncStateStore = syncStateStore,
                documentCache = documentCache,
                searchToolSettingsSyncQueue =
                    SearchToolSettingsSyncQueue(
                        dataStore =
                            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                                scope =
                                    kotlinx.coroutines.CoroutineScope(
                                        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob(),
                                    ),
                            ) { java.io.File.createTempFile("search-settings-", ".preferences_pb") },
                    ),
                settingsRepository = mockk<SettingsRepositoryImpl>(relaxed = true),
            )
        return FirestoreDataSource(facade, externalScope)
    }

    @Test
    fun `SCENARIO-01 Prefetching resolves full dependency closure for upsert events`() =
        runTest {
            val msg1Snapshot =
                mockDocument(
                    "users/user_123/messages/msg1",
                    mapOf("chat_id" to "chat1", "user_message_id" to "msg0"),
                )
            val chat1Snapshot =
                mockDocument(
                    "users/user_123/chats/chat1",
                    emptyMap(),
                )
            val msg0Snapshot =
                mockDocument(
                    "users/user_123/messages/msg0",
                    emptyMap(),
                )

            val events =
                listOf(
                    syncEvent(sequence = 100, operation = "upsert", collectionPath = "messages", entityId = "msg1"),
                    syncEvent(sequence = 101, operation = "delete", collectionPath = "memories", entityId = "mem1"),
                )

            cache.prefetch("user_123", events)

            assertEquals(msg1Snapshot.data, cache.getDocument("users/user_123/messages/msg1"))
            assertEquals(chat1Snapshot.data, cache.getDocument("users/user_123/chats/chat1"))
            assertEquals(msg0Snapshot.data, cache.getDocument("users/user_123/messages/msg0"))

            // Assert no fetch occurred for delete event
            verify(exactly = 0) { firestore.document(match { it.contains("mem1") }) }

            // Assert duplicate document paths fetched at most once during prefetch pass
            verify(exactly = 1) { firestore.document("users/user_123/messages/msg1") }
            verify(exactly = 1) { firestore.document("users/user_123/chats/chat1") }
            verify(exactly = 1) { firestore.document("users/user_123/messages/msg0") }
        }

    @Test
    fun `SCENARIO-02 Missing required parent surfaces as IllegalStateException during apply`() =
        runTest {
            mockDocument("users/user_123/messages/msg1", mapOf("chat_id" to "chat1"))
            mockDocument("users/user_123/chats/chat1", null)

            val localStore = mockk<FirestoreSyncLocalStore>(relaxed = true)
            val stateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
            coEvery { stateStore.lastAppliedSequence("user_123") } returns 99L

            val applier =
                FirestoreSyncEventApplier(
                    sourceDocuments = cache,
                    localStore = localStore,
                    stateStore = stateStore,
                )

            val event = syncEvent(sequence = 100, collectionPath = "messages", entityId = "msg1")

            val exception =
                assertThrows<IllegalStateException> {
                    applier.apply("user_123", event)
                }

            assertEquals("missing required parent chats/chat1 for messages/msg1", exception.message)

            coVerify(exactly = 0) { localStore.upsert(any(), any(), any(), any()) }
            coVerify(exactly = 0) { stateStore.setLastAppliedSequence("user_123", 100) }
        }

    @Test
    fun `SCENARIO-03 RoomFirestoreSyncLocalStore decodes from cached snapshots first`() =
        runTest {
            val database = mockk<EnclaveDatabase>(relaxed = true)
            val adapterLookup =
                mapOf<String, EntitySyncAdapter<*>>(
                    "chats" to mockk<EntitySyncAdapter<Any>>(relaxed = true),
                )
            val repairer = mockk<FirestoreSensitiveFieldRepairer>()
            val embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val syncStateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
            val pendingSyncLookup = mockk<FirestoreSyncPendingSyncLookup>(relaxed = true)

            val localStore =
                RoomFirestoreSyncLocalStore(
                    database = database,
                    firestore = firestore,
                    userIdProvider = { "user_123" },
                    adapterLookup = adapterLookup,
                    repairer = repairer,
                    embeddingCoordinator = embeddingCoordinator,
                    loggingPort = loggingPort,
                    syncStateStore = syncStateStore,
                    pendingSyncLookup = pendingSyncLookup,
                    cache = cache,
                )

            val path = "users/user_123/chats/chat1"
            val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
            val mockSnapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)

            every { firestore.document(path) } returns docRef
            every { docRef.get() } returns
                com.google.android.gms.tasks.Tasks
                    .forResult(mockSnapshot)
            every { docRef.path } returns path
            every { mockSnapshot.reference } returns docRef
            every { mockSnapshot.exists() } returns true
            every { mockSnapshot.data } returns mapOf("name" to "Chat 1")

            val event = syncEvent(sequence = 100, collectionPath = "chats", entityId = "chat1")
            cache.prefetch("user_123", listOf(event))

            every { repairer.repairInbound("chats", "chat1", any()) } answers { it.invocation.args[2] as Map<String, Any?> }

            localStore.upsert("chats", "chat1", mapOf("name" to "Chat 1"), null)

            // Verifies the store used the cached snapshot rather than doing another network get
            verify(exactly = 1) { docRef.get() }
        }

    @Test
    fun `SCENARIO-04 Sensitive field repair invalidates cached snapshot before decode`() =
        runTest {
            val database = mockk<EnclaveDatabase>(relaxed = true)
            val adapterLookup =
                mapOf<String, EntitySyncAdapter<*>>(
                    "api_keys" to mockk<EntitySyncAdapter<Any>>(relaxed = true),
                )
            val repairer = mockk<FirestoreSensitiveFieldRepairer>()
            val embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val syncStateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
            val pendingSyncLookup = mockk<FirestoreSyncPendingSyncLookup>(relaxed = true)

            val localStore =
                RoomFirestoreSyncLocalStore(
                    database = database,
                    firestore = firestore,
                    userIdProvider = { "user_123" },
                    adapterLookup = adapterLookup,
                    repairer = repairer,
                    embeddingCoordinator = embeddingCoordinator,
                    loggingPort = loggingPort,
                    syncStateStore = syncStateStore,
                    pendingSyncLookup = pendingSyncLookup,
                    cache = cache,
                )

            val path = "users/user_123/api_keys/key1"
            val originalData = mapOf<String, Any?>("key" to "unencrypted_key_val")
            val repairedData = mapOf<String, Any?>("key" to "ENCRYPTED_key_val")

            val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
            every { firestore.document(path) } returns docRef
            every { docRef.path } returns path

            val originalSnapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
            every { originalSnapshot.reference } returns docRef
            every { originalSnapshot.exists() } returns true
            every { originalSnapshot.data } returns originalData

            val repairedSnapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
            every { repairedSnapshot.reference } returns docRef
            every { repairedSnapshot.exists() } returns true
            every { repairedSnapshot.data } returns repairedData

            every { docRef.get() } returnsMany
                listOf(
                    com.google.android.gms.tasks.Tasks
                        .forResult(originalSnapshot),
                    com.google.android.gms.tasks.Tasks
                        .forResult(repairedSnapshot),
                )
            every { docRef.set(repairedData, any<com.google.firebase.firestore.SetOptions>()) } returns
                com.google.android.gms.tasks.Tasks
                    .forResult(null)

            val event = syncEvent(sequence = 100, collectionPath = "api_keys", entityId = "key1")
            cache.prefetch("user_123", listOf(event))

            every { repairer.repairInbound("api_keys", "key1", originalData) } returns repairedData

            localStore.upsert("api_keys", "key1", originalData, null)

            assertNull(cache.getCachedSnapshot(path))
            verify(exactly = 2) { docRef.get() }
            verify { docRef.set(repairedData, any<com.google.firebase.firestore.SetOptions>()) }
        }

    @Test
    fun `SCENARIO-05 Aggregation count query falls back to indeterminate progress on failure`() =
        runTest {
            val port = mockk<CloudSyncPort>(relaxed = true)
            coEvery { port.getRemainingEventsCount() } returns Result.failure(RuntimeException("Network error"))
            coEvery { port.syncRemoteChangesFromCloud() } returns
                Result.success(
                    com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0L,
                        hasMore = false,
                    ),
                )

            val useCase = StartCloudSyncListeningUseCase(port)
            val progressUpdates = mutableListOf<CloudSyncReplayProgress>()

            val result =
                useCase.invoke(uploadLocalFirst = false) { progress ->
                    progressUpdates.add(progress)
                }

            assertTrue(result.isSuccess)
            assertTrue(progressUpdates.isNotEmpty())
            assertNull(progressUpdates.first().progressFraction)
            assertEquals("Checking for updates...", progressUpdates.first().status)
        }

    @Test
    fun `SCENARIO-06 Replay clears document cache even when apply fails`() =
        runTest {
            val cacheDouble = mockk<FirestoreSyncDocumentCache>(relaxed = true)

            val syncStateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
            coEvery { syncStateStore.lastAppliedSequence("user_123") } returns 99L

            val syncEventFeedClient = mockk<FirestoreSyncEventFeedClient>(relaxed = true)
            val event = syncEvent(sequence = 100, collectionPath = "chats", entityId = "chat1")
            coEvery { syncEventFeedClient.fetchNextPage("user_123", 99L) } returns listOf(event)

            val syncEventApplier = mockk<FirestoreSyncEventDispatcher>()
            coEvery { syncEventApplier.apply("user_123", event) } throws RuntimeException("Apply failed")

            val dataSource =
                createTestDataSource(
                    firestore = firestore,
                    syncStateStore = syncStateStore,
                    syncEventFeedClient = syncEventFeedClient,
                    syncEventApplier = syncEventApplier,
                    documentCache = cacheDouble,
                    externalScope = this,
                )

            val result = dataSource.syncRemoteChangesFromCloud()

            assertTrue(result.isFailure)
            verify(exactly = 1) { cacheDouble.clear() }
            coVerify(exactly = 0) { syncStateStore.setLastAppliedSequence("user_123", 100) }
        }

    @Test
    fun `SCENARIO-07 Remaining event count is computed after stale cursor recovery`() =
        runTest {
            val chatDao = mockk<ChatDao>(relaxed = true)
            coEvery { chatDao.countActiveChats() } returns 0

            val syncStateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
            var seq = 100L
            coEvery { syncStateStore.lastAppliedSequence("user_123") } answers { seq }
            coEvery { syncStateStore.setLastAppliedSequence("user_123", any()) } answers {
                seq = it.invocation.args[1] as Long
            }

            val collectionRef = mockk<com.google.firebase.firestore.CollectionReference>()
            val query = mockk<com.google.firebase.firestore.Query>()
            val countQuery = mockk<com.google.firebase.firestore.AggregateQuery>()
            val snapshot = mockk<com.google.firebase.firestore.AggregateQuerySnapshot>()
            val task =
                com.google.android.gms.tasks.Tasks
                    .forResult(snapshot)

            every { firestore.collection("users/user_123/sync_events") } returns collectionRef
            every { collectionRef.whereGreaterThan("sequence", 0L) } returns query
            every { query.count() } returns countQuery
            every { countQuery.get(com.google.firebase.firestore.AggregateSource.SERVER) } returns task
            every { snapshot.count } returns 42L

            val dataSource =
                createTestDataSource(
                    firestore = firestore,
                    syncStateStore = syncStateStore,
                    syncEventFeedClient = mockk(),
                    syncEventApplier = mockk(),
                    documentCache = cache,
                    chatDao = chatDao,
                    externalScope = this,
                )

            val result = dataSource.getRemainingEventsCount()

            if (result.isFailure) {
                println("SCENARIO-07 failed: ${result.exceptionOrNull()}")
                result.exceptionOrNull()?.printStackTrace()
            }

            assertTrue(result.isSuccess)
            assertEquals(42L, result.getOrThrow())
            assertEquals(0L, seq) // Cursor was recovered to 0

            coVerify(ordering = io.mockk.Ordering.SEQUENCE) {
                syncStateStore.lastAppliedSequence("user_123")
                chatDao.countActiveChats()
                syncStateStore.setLastAppliedSequence("user_123", 0L)
                syncStateStore.lastAppliedSequence("user_123")
                firestore.collection("users/user_123/sync_events")
            }
        }

    @Test
    fun `SCENARIO-08 Live-drain prefetches only contiguous events starting from last applied sequence`() =
        runTest {
            val cacheDouble = mockk<FirestoreSyncDocumentCache>(relaxed = true)

            val syncStateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
            coEvery { syncStateStore.lastAppliedSequence("user_123") } returns 50L

            var listenerCallback: (suspend (List<FirestoreSyncEvent>) -> Unit)? = null
            val syncEventFeedClient = mockk<FirestoreSyncEventFeedClient>()
            coEvery { syncEventFeedClient.attachLiveListener("user_123", 50L, any()) } answers {
                listenerCallback = thirdArg()
                mockk(relaxed = true)
            }

            val syncEventApplier = mockk<FirestoreSyncEventDispatcher>(relaxed = true)
            coEvery { syncEventApplier.apply("user_123", any()) } answers {
                val event = secondArg<FirestoreSyncEvent>()
                FirestoreSyncEventApplier.ApplyResult(appliedCount = 1, lastAppliedSequence = event.sequence)
            }

            val ctx = mockk<FirestoreSyncContext>(relaxed = true)
            every { ctx.userIdProvider() } returns "user_123"
            every { ctx.cryptoManager.hasCachedKey() } returns true

            val coordinator =
                FirestoreListenerCoordinator(
                    ctx = ctx,
                    syncEventFeedClient = syncEventFeedClient,
                    syncEventApplier = syncEventApplier,
                    syncStateStore = syncStateStore,
                    externalScope = this,
                    cache = cacheDouble,
                )

            coordinator.startListening()

            runCurrent()
            assertNotNull(listenerCallback)

            val events =
                listOf(
                    syncEvent(sequence = 51, collectionPath = "chats", entityId = "chat51"),
                    syncEvent(sequence = 52, collectionPath = "chats", entityId = "chat52"),
                    syncEvent(sequence = 54, collectionPath = "chats", entityId = "chat54"),
                )

            listenerCallback!!.invoke(events)

            coVerify(exactly = 1) {
                cacheDouble.prefetch(
                    "user_123",
                    match { list ->
                        list.size == 2 && list[0].sequence == 51L && list[1].sequence == 52L
                    },
                )
            }

            verify(exactly = 1) { cacheDouble.clear() }
        }

    @Test
    fun `SCENARIO-19 Cache bypass detection (Mutation Defense)`() =
        runTest {
            val database = mockk<EnclaveDatabase>(relaxed = true)
            val adapterLookup =
                mapOf<String, EntitySyncAdapter<*>>(
                    "chats" to mockk<EntitySyncAdapter<Any>>(relaxed = true),
                )
            val repairer = mockk<FirestoreSensitiveFieldRepairer>()
            val embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val syncStateStore = mockk<FirestoreSyncStateStore>(relaxed = true)
            val pendingSyncLookup = mockk<FirestoreSyncPendingSyncLookup>(relaxed = true)

            val localStore =
                RoomFirestoreSyncLocalStore(
                    database = database,
                    firestore = firestore,
                    userIdProvider = { "user_123" },
                    adapterLookup = adapterLookup,
                    repairer = repairer,
                    embeddingCoordinator = embeddingCoordinator,
                    loggingPort = loggingPort,
                    syncStateStore = syncStateStore,
                    pendingSyncLookup = pendingSyncLookup,
                    cache = cache,
                )

            val paths = (1..5).map { "users/user_123/chats/chat$it" }
            val docRefs =
                paths.map { path ->
                    val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
                    val snapshot = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
                    every { firestore.document(path) } returns docRef
                    every { docRef.path } returns path
                    every { docRef.get() } returns
                        com.google.android.gms.tasks.Tasks
                            .forResult(snapshot)
                    every { snapshot.reference } returns docRef
                    every { snapshot.exists() } returns true
                    every { snapshot.data } returns mapOf("name" to "Chat Name")
                    docRef
                }

            val events =
                (1..5).map {
                    syncEvent(sequence = 100L + it, collectionPath = "chats", entityId = "chat$it")
                }
            cache.prefetch("user_123", events)

            docRefs.forEach { docRef ->
                verify(exactly = 1) { docRef.get() }
            }

            every { repairer.repairInbound("chats", any(), any()) } answers { it.invocation.args[2] as Map<String, Any?> }

            for (i in 1..5) {
                localStore.upsert("chats", "chat$i", mapOf("name" to "Chat Name"), null)
            }

            docRefs.forEach { docRef ->
                verify(exactly = 1) { docRef.get() }
            }
        }
}
