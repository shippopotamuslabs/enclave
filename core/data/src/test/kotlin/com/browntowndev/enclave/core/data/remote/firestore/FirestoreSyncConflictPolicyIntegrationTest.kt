package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ChatSyncHandler
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreSyncConflictPolicyIntegrationTest {
    @Test
    fun `SCN conflict policy applies newer remote chat even when local row is dirty`() =
        runTest {
            val chatDao = FakeChatDao()
            val dirtyLocal =
                ChatEntity(
                    id = ChatId("chat-1"),
                    name = "Local stale edit",
                    created = Date(0),
                    lastModified = Date(0),
                    pinned = false,
                    pendingSync = 1,
                    updatedAt = 1700000001000L,
                )
            chatDao.seed(dirtyLocal)
            val captured = chatDao.upsertedChats
            val cryptoManager = FakeCryptoManagerPort()
            cryptoManager.hasCachedKeyValue = true
            val stateStore = DeterministicConflictTestStateStore(localDeviceId = "device-a")
            val localEncryption = FakeLocalDataEncryptionService()
            val sourceDocuments = SharedFakeSyncSourceDocumentStore()
            sourceDocuments.put(
                "users/alice/chats/chat-1",
                encryptedChatSourcePayload(
                    plaintextName = "Remote newer edit",
                    localEncryption = localEncryption,
                    updatedAt = 1700000005000L,
                ),
            )
            val loggingPort = FakeLoggingPort()
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
                    localStore =
                        PolicyEnforcingSyncLocalStore(
                            delegate = HandlerBackedSyncLocalStore(chatHandler = chatHandler),
                        ),
                    stateStore = stateStore,
                )

            applier.apply(
                userId = "alice",
                event =
                    FirestoreSyncEvent(
                        sequence = 1,
                        schemaVersion = 1,
                        operation = "upsert",
                        collectionPath = "chats",
                        entityType = "chats",
                        entityId = "chat-1",
                        entityPath = "users/alice/chats/chat-1",
                        sourceExists = true,
                        sourceUpdatedAt = 1700000005000L,
                        sourceDeleted = false,
                        deviceId = "device-b",
                        clientMutationId = null,
                        triggerEventId = "event-1",
                        emittedAtMillis = 1700000005001,
                    ),
            )

            assertEquals(1, captured.size)
            assertEquals(0, captured.single().pendingSync)
            assertEquals(1, stateStore.sequence)
        }

    @Test
    fun `SCN conflict policy preserves dirty local chat when remote event is from another device`() =
        runTest {
            val chatDao = FakeChatDao()
            val dirtyLocal =
                ChatEntity(
                    id = ChatId("chat-1"),
                    name = "Local edit",
                    created = Date(0),
                    lastModified = Date(0),
                    pinned = false,
                    pendingSync = 1,
                    updatedAt = 1700000005000L,
                )
            chatDao.seed(dirtyLocal)
            val captured = chatDao.upsertedChats
            val cryptoManager = FakeCryptoManagerPort()
            cryptoManager.hasCachedKeyValue = true
            val stateStore = DeterministicConflictTestStateStore(localDeviceId = "device-a")
            val localEncryption = FakeLocalDataEncryptionService()
            val sourceDocuments = SharedFakeSyncSourceDocumentStore()
            sourceDocuments.put(
                "users/alice/chats/chat-1",
                encryptedChatSourcePayload("Remote Roadmap", localEncryption),
            )
            val loggingPort = FakeLoggingPort()
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
                    localStore =
                        PolicyEnforcingSyncLocalStore(
                            delegate = HandlerBackedSyncLocalStore(chatHandler = chatHandler),
                        ),
                    stateStore = stateStore,
                )

            applier.apply(
                userId = "alice",
                event =
                    FirestoreSyncEvent(
                        sequence = 1,
                        schemaVersion = 1,
                        operation = "upsert",
                        collectionPath = "chats",
                        entityType = "chats",
                        entityId = "chat-1",
                        entityPath = "users/alice/chats/chat-1",
                        sourceExists = true,
                        sourceUpdatedAt = 1700000001000L,
                        sourceDeleted = false,
                        deviceId = "device-b",
                        clientMutationId = null,
                        triggerEventId = "event-1",
                        emittedAtMillis = 1700000001001,
                    ),
            )

            assertEquals(0, captured.size)
            assertEquals(1, stateStore.sequence)
        }

    @Test
    fun `SCN conflict policy preserves dirty local chat when remote event has null deviceId`() =
        runTest {
            val chatDao = FakeChatDao()
            val dirtyLocal =
                ChatEntity(
                    id = ChatId("chat-1"),
                    name = "Local edit",
                    created = Date(0),
                    lastModified = Date(0),
                    pinned = false,
                    pendingSync = 1,
                    updatedAt = 1700000005000L,
                )
            chatDao.seed(dirtyLocal)
            val captured = chatDao.upsertedChats
            val cryptoManager = FakeCryptoManagerPort()
            cryptoManager.hasCachedKeyValue = true
            val stateStore = DeterministicConflictTestStateStore(localDeviceId = "device-a")
            val localEncryption = FakeLocalDataEncryptionService()
            val sourceDocuments = SharedFakeSyncSourceDocumentStore()
            sourceDocuments.put(
                "users/alice/chats/chat-1",
                encryptedChatSourcePayload("Remote Roadmap", localEncryption),
            )
            val loggingPort = FakeLoggingPort()
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
                    localStore =
                        PolicyEnforcingSyncLocalStore(
                            delegate = HandlerBackedSyncLocalStore(chatHandler = chatHandler),
                        ),
                    stateStore = stateStore,
                )

            applier.apply(
                userId = "alice",
                event =
                    FirestoreSyncEvent(
                        sequence = 1,
                        schemaVersion = 1,
                        operation = "upsert",
                        collectionPath = "chats",
                        entityType = "chats",
                        entityId = "chat-1",
                        entityPath = "users/alice/chats/chat-1",
                        sourceExists = true,
                        sourceUpdatedAt = 1700000001000L,
                        sourceDeleted = false,
                        deviceId = null,
                        clientMutationId = null,
                        triggerEventId = "event-legacy",
                        emittedAtMillis = 1700000001001,
                    ),
            )

            assertEquals(0, captured.size)
            assertEquals(1, stateStore.sequence)
        }
}

private class DeterministicConflictTestStateStore(
    private val localDeviceId: String,
) : FirestoreSyncStateStore() {
    var sequence: Long = 0

    override suspend fun lastAppliedSequence(userId: String): Long = sequence

    override suspend fun setLastAppliedSequence(
        userId: String,
        sequence: Long,
    ) {
        this.sequence = sequence
    }

    override suspend fun deviceId(): String = localDeviceId
}

private class PolicyEnforcingSyncLocalStore(
    private val delegate: FirestoreSyncLocalStore,
) : FirestoreSyncLocalStore {
    override suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String?,
    ) {
        delegate.upsert(collectionPath, entityId, data, eventDeviceId)
    }

    override suspend fun delete(
        collectionPath: String,
        entityId: String,
    ) {
        delegate.delete(collectionPath, entityId)
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        delegate.withTransaction(block)
    }
}
