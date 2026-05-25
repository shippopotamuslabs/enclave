package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ChatSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.StudioMediaSyncHandler
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.testSensitiveSelfHealer
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test doubles that apply sync events through real entity handlers so data-source tests can
 * assert decrypted Room writes instead of only dispatcher forwarding.
 */
internal data class ChatHydrationApplierBundle(
    val applier: FirestoreSyncEventApplier,
    val sourceDocuments: SharedFakeSyncSourceDocumentStore,
    val stateStore: FirestoreSyncStateStore,
)

internal fun buildChatHydrationApplier(
    chatDao: ChatDao,
    cryptoManager: CryptoManagerPort,
    loggingPort: LoggingPort = mockk(relaxed = true),
    localEncryption: LocalDataEncryptionService = FakeLocalDataEncryptionService(),
    stateStore: FirestoreSyncStateStore = FirestoreSyncStateStore(),
): ChatHydrationApplierBundle {
    val sourceDocuments = SharedFakeSyncSourceDocumentStore()
    val syncContext =
        buildTestSyncContext(
            chatDao = chatDao,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            localEncryption = localEncryption,
        )
    val chatHandler = ChatSyncHandler(syncContext)
    val applier =
        FirestoreSyncEventApplier(
            sourceDocuments = sourceDocuments,
            localStore = HandlerBackedSyncLocalStore(chatHandler = chatHandler),
            stateStore = stateStore,
        )
    return ChatHydrationApplierBundle(applier, sourceDocuments, stateStore)
}

internal fun buildStudioMediaHydrationApplier(
    studioMediaDao: com.browntowndev.enclave.core.data.local.StudioMediaDao,
    cryptoManager: CryptoManagerPort,
    loggingPort: LoggingPort = mockk(relaxed = true),
    localEncryption: LocalDataEncryptionService = FakeLocalDataEncryptionService(),
): Pair<FirestoreSyncEventApplier, SharedFakeSyncSourceDocumentStore> {
    val sourceDocuments = SharedFakeSyncSourceDocumentStore()
    val syncContext =
        buildTestSyncContext(
            studioMediaDao = studioMediaDao,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            localEncryption = localEncryption,
        )
    val handler = StudioMediaSyncHandler(syncContext)
    val applier =
        FirestoreSyncEventApplier(
            sourceDocuments = sourceDocuments,
            localStore = HandlerBackedSyncLocalStore(studioMediaHandler = handler),
            stateStore = FirestoreSyncStateStore(),
        )
    return applier to sourceDocuments
}

internal class IntegrationApplyingDispatcher(
    private val applier: FirestoreSyncEventApplier,
    val sourceDocuments: SharedFakeSyncSourceDocumentStore,
) : FirestoreSyncEventDispatcher {
    override suspend fun apply(
        userId: String,
        event: FirestoreSyncEvent,
    ): FirestoreSyncEventApplier.ApplyResult = applier.apply(userId, event)
}

internal class SharedFakeSyncSourceDocumentStore : FirestoreSyncSourceDocumentStore {
    private val documents = mutableMapOf<String, Map<String, Any?>>()

    fun put(
        path: String,
        data: Map<String, Any?>,
    ) {
        documents[path] = data
    }

    override suspend fun getDocument(path: String): Map<String, Any?>? = documents[path]
}

internal fun buildTestSyncContext(
    chatDao: ChatDao = mockk(relaxed = true),
    studioMediaDao: com.browntowndev.enclave.core.data.local.StudioMediaDao = mockk(relaxed = true),
    cryptoManager: CryptoManagerPort,
    loggingPort: LoggingPort,
    localEncryption: LocalDataEncryptionService,
): FirestoreSyncContext {
    val healer = testSensitiveSelfHealer(localEncryption, loggingPort)
    val firestore = mockk<FirebaseFirestore>(relaxed = true)
    val database = mockk<EnclaveDatabase>(relaxed = true)
    return FirestoreSyncDependencies(
        firestore = firestore,
        daos =
            FirestoreSyncDaos.from(
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
                        studioMediaDao = studioMediaDao,
                        studioAlbumDao = mockk(relaxed = true),
                    ),
            ),
        localEncryption = localEncryption,
        sensitiveSelfHealer = healer,
        firestoreSensitiveFieldRepairer =
            FirestoreSensitiveFieldRepairer(
                StaticSensitiveFieldCatalog(),
                healer,
            ),
        cryptoManager = cryptoManager,
        loggingPort = loggingPort,
        apiKeyManager = mockk<ApiKeyManager>(relaxed = true),
        embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true),
        userIdProvider = { "alice" },
        purgeInProgress = AtomicBoolean(false),
    ).toContext()
}

internal class HandlerBackedSyncLocalStore(
    private val chatHandler: ChatSyncHandler? = null,
    private val studioMediaHandler: StudioMediaSyncHandler? = null,
) : FirestoreSyncLocalStore {
    override suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String?,
    ) {
        val snapshot = mockDocumentSnapshot(collectionPath, entityId, data)
        when (collectionPath) {
            "chats" ->
                chatHandler?.fromFirestore(snapshot)?.let { entity ->
                    chatHandler.upsertLocal(entity, snapshot, overwrite = false)
                }
            "studio_gallery" ->
                studioMediaHandler?.let { handler ->
                    handler.upsertLocal(handler.fromFirestore(snapshot), snapshot, overwrite = true)
                }
            else -> Unit
        }
    }

    override suspend fun delete(
        collectionPath: String,
        entityId: String,
    ) = Unit

    override suspend fun withTransaction(block: suspend () -> Unit) {
        block()
    }
}

private fun mockDocumentSnapshot(
    collectionPath: String,
    entityId: String,
    data: Map<String, Any?>,
): DocumentSnapshot {
    val parent = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
    every { parent.id } returns collectionPath
    val reference = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
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
            is com.google.firebase.firestore.Blob ->
                every { snapshot.getBlob(field) } returns value
            else -> Unit
        }
    }
    return snapshot
}

internal fun encryptedChatSourcePayload(
    plaintextName: String,
    localEncryption: LocalDataEncryptionService = FakeLocalDataEncryptionService(),
    updatedAt: Long = 1700000001000L,
): Map<String, Any?> {
    val encryptedName =
        localEncryption.encryptString(
            plaintextName,
            byteArrayOf(),
        )
    return mapOf(
        "name" to encryptedName,
        "created_at" to 1700000000000L,
        "last_modified" to 1700000000000L,
        "pinned" to false,
        "updated_at" to updatedAt,
        "deleted" to false,
    )
}

internal fun captureChatUpserts(chatDao: ChatDao): MutableList<ChatEntity> {
    val captured = mutableListOf<ChatEntity>()
    io.mockk.coEvery { chatDao.getChatById(any()) } returns null
    io.mockk.coEvery { chatDao.upsert(capture(captured)) } returns 1L
    return captured
}

internal fun captureStudioMediaUpserts(
    studioMediaDao: com.browntowndev.enclave.core.data.local.StudioMediaDao,
): MutableList<com.browntowndev.enclave.core.data.local.StudioMediaEntity> {
    val captured = mutableListOf<com.browntowndev.enclave.core.data.local.StudioMediaEntity>()
    io.mockk.coEvery { studioMediaDao.insertMedia(capture(captured)) } returns Unit
    return captured
}
