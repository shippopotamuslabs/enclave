@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (DocumentSnapshot), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
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
import com.browntowndev.enclave.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ChatSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.StudioMediaSyncHandler
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.FakeStudioMediaDao
import com.browntowndev.enclave.core.data.repository.testSensitiveSelfHealer
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
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
    loggingPort: LoggingPort = FakeLoggingPort(),
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
    loggingPort: LoggingPort = FakeLoggingPort(),
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
    context: Context = ApplicationProvider.getApplicationContext(),
    chatDao: ChatDao = FakeChatDao(),
    studioMediaDao: com.browntowndev.enclave.core.data.local.StudioMediaDao = FakeStudioMediaDao(),
    cryptoManager: CryptoManagerPort,
    loggingPort: LoggingPort,
    localEncryption: LocalDataEncryptionService,
): FirestoreSyncContext {
    val healer = testSensitiveSelfHealer(localEncryption, loggingPort)
    return FirestoreSyncDependencies(
        firestore = fakeFirestore(),
        daos =
            FirestoreSyncDaos.from(
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
                        studioMediaDao = studioMediaDao,
                        studioAlbumDao = recordingDao<StudioAlbumDao>().dao,
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
        apiKeyManager = testApiKeyManager(),
        embeddingCoordinator = testEmbeddingRegenerationCoordinator(context),
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

/**
 * The recorded upserts of a [FakeChatDao] — the fake-era replacement for the
 * MockK capture stub. The fake's `getChatById` returns null for unseeded ids,
 * matching the stubbed behavior the capture helper used to install.
 */
internal fun captureChatUpserts(chatDao: ChatDao): MutableList<ChatEntity> {
    require(chatDao is FakeChatDao) {
        "captureChatUpserts requires a FakeChatDao so upserts are recorded, got ${chatDao::class.simpleName}"
    }
    return chatDao.upsertedChats
}

/** The recorded inserts of the repository suite's [FakeStudioMediaDao]. */
internal fun captureStudioMediaUpserts(
    studioMediaDao: com.browntowndev.enclave.core.data.local.StudioMediaDao,
): MutableList<com.browntowndev.enclave.core.data.local.StudioMediaEntity> {
    require(studioMediaDao is FakeStudioMediaDao) {
        "captureStudioMediaUpserts requires a FakeStudioMediaDao so inserts are recorded, got ${studioMediaDao::class.simpleName}"
    }
    return studioMediaDao.insertCalls
}
