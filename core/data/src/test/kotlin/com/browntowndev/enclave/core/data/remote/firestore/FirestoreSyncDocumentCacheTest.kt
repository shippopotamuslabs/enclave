@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (FirebaseFirestore / DocumentSnapshot / aggregate queries), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.testWorkManager
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.FirestoreDataSource
import com.browntowndev.enclave.core.data.remote.FirestoreSyncDependenciesFacade
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.FakeStudioMediaDao
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.model.sync.CloudSyncReplayProgress
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncResult
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.mockk.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
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
        chatDao: ChatDao = FakeChatDao(),
    ): FirestoreDataSource {
        val cryptoManager = FakeCryptoManagerPort()
        cryptoManager.hasCachedKeyValue = true
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = FakeLoggingPort()

        val facade =
            FirestoreSyncDependenciesFacade.forTests(
                firestore = firestore,
                chatSyncDaos =
                    ChatSyncDaos(
                        chatDao = chatDao,
                        messageDao = FakeMessageDaoBundle(FakeEnclaveDatabase()).messageDao,
                        chatSummaryDao = FakeChatSummaryDao(),
                        messageFileDao = recordingDao<MessageFileDao>().dao,
                        messageVisionAnalysisDao = recordingDao<MessageVisionAnalysisDao>().dao,
                        messageTranscriptSegmentDao = FakeMessageTranscriptSegmentDao(),
                        messageInferenceTelemetryDao = FakeMessageInferenceTelemetryDao(),
                        webSearchSourceDao = recordingDao<WebSearchSourceDao>().dao,
                        documentDao = recordingDao<DocumentDao>().dao,
                        embeddingDao = recordingDao<EmbeddingDao>().dao,
                        firestoreSyncPlaceholderDao = FakeFirestoreSyncPlaceholderDao(),
                    ),
                configSyncDaos =
                    ConfigSyncDaos(
                        memoriesDao = recordingDao<MemoriesDao>().dao,
                        ttsProviderDao = recordingDao<TtsProviderDao>().dao,
                        liveVoiceProviderDao = recordingDao<LiveVoiceProviderDao>().dao,
                        mediaProviderDao = recordingDao<MediaProviderDao>().dao,
                        apiModelConfigurationsDao = recordingDao<ApiModelConfigurationsDao>().dao,
                        localModelsDao = recordingDao<LocalModelsDao>().dao,
                        apiCredentialsDao = recordingDao<ApiCredentialsDao>().dao,
                        localModelConfigurationsDao = recordingDao<LocalModelConfigurationsDao>().dao,
                        defaultModelsDao = recordingDao<DefaultModelsDao>().dao,
                        utilityModelsDao = recordingDao<UtilityModelsDao>().dao,
                    ),
                studioSyncDaos =
                    StudioSyncDaos(
                        studioMediaDao = FakeStudioMediaDao(),
                        studioAlbumDao = recordingDao<StudioAlbumDao>().dao,
                    ),
                apiKeyManager = testApiKeyManager(),
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                userIdProvider = { "user_123" },
                embeddingCoordinator = testEmbeddingRegenerationCoordinator(),
                localEncryption = localEncryption,
                sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
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
                settingsRepository =
                    SettingsRepositoryImpl(
                        dataStore = FakePreferencesDataStore(),
                        apiKeyManager = testApiKeyManager(),
                    ),
                feedIntegrityRecovery =
                    FirestoreSyncFeedIntegrityRecovery(
                        metaClient = FakeFirestoreSyncFeedMetaClient(firestore),
                        callableClient =
                            FakeFirestoreSyncFeedCallableClient(mockk<FirebaseFunctions>(relaxed = true)),
                        entityReconciler = FakeFirestoreSyncEntityReconciler(firestore = firestore),
                        syncStateStore = syncStateStore,
                        loggingPort = loggingPort,
                    ),
            )
        return FirestoreDataSource(
            facade,
            externalScope,
            CloudSyncTrigger(
                testWorkManager(ApplicationProvider.getApplicationContext()),
                FakeLoggingPort(),
                FakeEntitlementPort(),
            ),
        )
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

            val localStore = RecordingSyncLocalStore()
            val stateStore = FakeSyncStateStore()
            stateStore.sequence = 99L

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

            assertTrue(localStore.upsertCalls.isEmpty())
            assertTrue(stateStore.setLastAppliedCalls.none { it == ("user_123" to 100L) })
        }

    @Test
    fun `SCENARIO-03 RoomFirestoreSyncLocalStore decodes from cached snapshots first`() =
        runTest {
            val database = FakeEnclaveDatabase()
            val adapterLookup =
                mapOf<String, EntitySyncAdapter<*>>(
                    "chats" to FakeSyncAdapter<Any>(collectionPath = "chats", fromFirestoreResult = Any()),
                )
            val repairer = FakeFirestoreSensitiveFieldRepairer()
            val embeddingCoordinator = testEmbeddingRegenerationCoordinator()
            val loggingPort = FakeLoggingPort()
            val syncStateStore = FakeSyncStateStore()
            val pendingSyncLookup = FirestoreSyncPendingSyncLookup(database)

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

            // The fake repairer's default is identity repair, as the stub provided.
            localStore.upsert("chats", "chat1", mapOf("name" to "Chat 1"), null)

            // Verifies the store used the cached snapshot rather than doing another network get
            verify(exactly = 1) { docRef.get() }
        }

    @Test
    fun `SCENARIO-04 Sensitive field repair invalidates cached snapshot before decode`() =
        runTest {
            val database = FakeEnclaveDatabase()
            val adapterLookup =
                mapOf<String, EntitySyncAdapter<*>>(
                    "api_keys" to FakeSyncAdapter<Any>(collectionPath = "api_keys", fromFirestoreResult = Any()),
                )
            val repairer = FakeFirestoreSensitiveFieldRepairer()
            val embeddingCoordinator = testEmbeddingRegenerationCoordinator()
            val loggingPort = FakeLoggingPort()
            val syncStateStore = FakeSyncStateStore()
            val pendingSyncLookup = FirestoreSyncPendingSyncLookup(database)

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

            // Scripted repair result for exactly the stubbed call shape.
            repairer.repairInboundHandler = { collectionPath, entityId, data ->
                if (collectionPath == "api_keys" && entityId == "key1" && data == originalData) {
                    repairedData
                } else {
                    data
                }
            }

            localStore.upsert("api_keys", "key1", originalData, null)

            assertNull(cache.getCachedSnapshot(path))
            verify(exactly = 2) { docRef.get() }
            verify { docRef.set(repairedData, any<com.google.firebase.firestore.SetOptions>()) }
        }

    @Test
    fun `SCENARIO-05 Aggregation count query falls back to indeterminate progress on failure`() =
        runTest {
            val port = FakeCloudSyncPort()
            port.remainingEventsResult = Result.failure(RuntimeException("Network error"))
            port.syncRemoteResult =
                Result.success(
                    com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0L,
                        hasMore = false,
                    ),
                )

            val useCase =
                StartCloudSyncListeningUseCase(port, FakeEntitlementPort(EntitlementState.Entitled()))
            val progressUpdates = mutableListOf<CloudSyncReplayProgress>()

            val result =
                useCase.invoke(uploadLocalFirst = false) { progress ->
                    progressUpdates.add(progress)
                }

            assertTrue(result is StartCloudSyncResult.Started)
            assertTrue(progressUpdates.isNotEmpty())
            assertNull(progressUpdates.first().progressFraction)
            assertEquals("Checking for updates...", progressUpdates.first().status)
        }

    @Test
    fun `SCENARIO-06 Replay clears document cache even when apply fails`() =
        runTest {
            val cacheDouble = RecordingDocumentCache(firestore)

            val syncStateStore = FakeSyncStateStore()
            syncStateStore.sequence = 99L

            val event = syncEvent(sequence = 100, collectionPath = "chats", entityId = "chat1")
            val gateway = FakeFeedQueryGateway()
            gateway.fetchResult = listOf(event)
            val syncEventFeedClient = FirestoreSyncEventFeedClient(gateway = gateway)

            val syncEventApplier = RecordingSyncEventDispatcher()
            syncEventApplier.applyThrowable = RuntimeException("Apply failed")

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
            assertEquals(1, cacheDouble.clearCalls)
            assertTrue(syncStateStore.setLastAppliedCalls.none { it == ("user_123" to 100L) })
        }

    @Test
    fun `SCENARIO-07 Remaining event count is computed after stale cursor recovery`() =
        runTest {
            // Shared ordering log across the state store and chat DAO (the Firestore
            // collection call is verified separately on the SDK mock below).
            val orderLog = mutableListOf<String>()
            val chatDao = FakeChatDao()
            chatDao.events = orderLog

            val syncStateStore = FakeSyncStateStore()
            syncStateStore.events = orderLog
            syncStateStore.sequence = 100L

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
                    syncEventFeedClient = FirestoreSyncEventFeedClient(gateway = FakeFeedQueryGateway()),
                    syncEventApplier = RecordingSyncEventDispatcher(),
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
            assertEquals(0L, syncStateStore.sequence) // Cursor was recovered to 0

            // Relative order of the recovery interactions, as the previous
            // Ordering.SEQUENCE coVerify asserted (the firestore.collection call
            // terminates the sequence and is verified on the SDK mock).
            val firstSequenceAt = orderLog.indexOf("lastAppliedSequence")
            val countAt = orderLog.indexOf("countActiveChats")
            val resetAt = orderLog.indexOf("setLastAppliedSequence")
            val secondSequenceAt = orderLog.lastIndexOf("lastAppliedSequence")
            assertTrue(firstSequenceAt >= 0, "lastAppliedSequence must be read first: $orderLog")
            assertTrue(countAt > firstSequenceAt, "countActiveChats must follow the first read: $orderLog")
            assertTrue(resetAt > countAt, "the cursor reset must follow the empty check: $orderLog")
            assertTrue(secondSequenceAt > resetAt, "lastAppliedSequence must be re-read after the reset: $orderLog")
            verify(exactly = 1) { firestore.collection("users/user_123/sync_events") }
        }

    @Test
    fun `SCENARIO-08 Live-drain prefetches only contiguous events starting from last applied sequence`() =
        runTest {
            val cacheDouble = RecordingDocumentCache(firestore)

            val syncStateStore = FakeSyncStateStore()
            syncStateStore.sequence = 50L

            val gateway = FakeFeedQueryGateway()
            val syncEventFeedClient = FirestoreSyncEventFeedClient(gateway = gateway)

            // The recording dispatcher's default result mirrors the previous stub:
            // ApplyResult(appliedCount = 1, lastAppliedSequence = event.sequence).
            val syncEventApplier = RecordingSyncEventDispatcher()

            val cryptoManager = FakeCryptoManagerPort()
            cryptoManager.hasCachedKeyValue = true
            val ctx =
                testSyncContext(
                    cryptoManager = cryptoManager,
                    userId = "user_123",
                )

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
            val listenerCallback = gateway.listenerCallbacks.singleOrNull()
            assertNotNull(listenerCallback)

            val events =
                listOf(
                    syncEvent(sequence = 51, collectionPath = "chats", entityId = "chat51"),
                    syncEvent(sequence = 52, collectionPath = "chats", entityId = "chat52"),
                    syncEvent(sequence = 54, collectionPath = "chats", entityId = "chat54"),
                )

            listenerCallback!!.invoke(events)

            assertEquals(1, cacheDouble.prefetchCalls.size)
            val (prefetchUserId, prefetchedEvents) = cacheDouble.prefetchCalls.single()
            assertEquals("user_123", prefetchUserId)
            assertEquals(listOf(51L, 52L), prefetchedEvents.map { it.sequence })

            assertEquals(1, cacheDouble.clearCalls)
        }

    @Test
    fun `SCENARIO-19 Cache bypass detection (Mutation Defense)`() =
        runTest {
            val database = FakeEnclaveDatabase()
            val adapterLookup =
                mapOf<String, EntitySyncAdapter<*>>(
                    "chats" to FakeSyncAdapter<Any>(collectionPath = "chats", fromFirestoreResult = Any()),
                )
            val repairer = FakeFirestoreSensitiveFieldRepairer()
            val embeddingCoordinator = testEmbeddingRegenerationCoordinator()
            val loggingPort = FakeLoggingPort()
            val syncStateStore = FakeSyncStateStore()
            val pendingSyncLookup = FirestoreSyncPendingSyncLookup(database)

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

            // The fake repairer's default is identity repair, as the stub provided.

            for (i in 1..5) {
                localStore.upsert("chats", "chat$i", mapOf("name" to "Chat Name"), null)
            }

            docRefs.forEach { docRef ->
                verify(exactly = 1) { docRef.get() }
            }
        }
}
