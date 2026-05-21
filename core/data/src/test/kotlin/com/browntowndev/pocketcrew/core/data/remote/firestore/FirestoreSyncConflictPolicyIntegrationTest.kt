package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.ChatSyncHandler
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date

class FirestoreSyncConflictPolicyIntegrationTest {
    @Test
    fun `SCN conflict policy applies newer remote chat even when local row is dirty`() =
        runTest {
            val chatDao = mockk<ChatDao>(relaxed = true)
            val database = mockk<PocketCrewDatabase>(relaxed = true)
            every { database.chatDao() } returns chatDao
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
            val captured = mutableListOf<ChatEntity>()
            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns dirtyLocal
            coEvery { chatDao.upsert(capture(captured)) } returns 1L
            val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
            every { cryptoManager.hasCachedKey() } returns true
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
            val loggingPort = mockk<com.browntowndev.pocketcrew.domain.port.inference.LoggingPort>(relaxed = true)
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
                            pendingSyncLookup = FirestoreSyncPendingSyncLookup(database),
                            stateStore = stateStore,
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
            val chatDao = mockk<ChatDao>(relaxed = true)
            val database = mockk<PocketCrewDatabase>(relaxed = true)
            every { database.chatDao() } returns chatDao
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
            val captured = mutableListOf<ChatEntity>()
            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns dirtyLocal
            coEvery { chatDao.upsert(capture(captured)) } returns 1L
            val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
            every { cryptoManager.hasCachedKey() } returns true
            val stateStore = DeterministicConflictTestStateStore(localDeviceId = "device-a")
            val localEncryption = FakeLocalDataEncryptionService()
            val sourceDocuments = SharedFakeSyncSourceDocumentStore()
            sourceDocuments.put(
                "users/alice/chats/chat-1",
                encryptedChatSourcePayload("Remote Roadmap", localEncryption),
            )
            val loggingPort = mockk<com.browntowndev.pocketcrew.domain.port.inference.LoggingPort>(relaxed = true)
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
                            pendingSyncLookup = FirestoreSyncPendingSyncLookup(database),
                            stateStore = stateStore,
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
            coVerify(exactly = 0) { chatDao.upsert(any()) }
            assertEquals(1, stateStore.sequence)
        }

    @Test
    fun `SCN conflict policy preserves dirty local chat when remote event has null deviceId`() =
        runTest {
            val chatDao = mockk<ChatDao>(relaxed = true)
            val database = mockk<PocketCrewDatabase>(relaxed = true)
            every { database.chatDao() } returns chatDao
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
            val captured = mutableListOf<ChatEntity>()
            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns dirtyLocal
            coEvery { chatDao.upsert(capture(captured)) } returns 1L
            val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
            every { cryptoManager.hasCachedKey() } returns true
            val stateStore = DeterministicConflictTestStateStore(localDeviceId = "device-a")
            val localEncryption = FakeLocalDataEncryptionService()
            val sourceDocuments = SharedFakeSyncSourceDocumentStore()
            sourceDocuments.put(
                "users/alice/chats/chat-1",
                encryptedChatSourcePayload("Remote Roadmap", localEncryption),
            )
            val loggingPort = mockk<com.browntowndev.pocketcrew.domain.port.inference.LoggingPort>(relaxed = true)
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
                            pendingSyncLookup = FirestoreSyncPendingSyncLookup(database),
                            stateStore = stateStore,
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
            coVerify(exactly = 0) { chatDao.upsert(any()) }
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
    @Suppress("UnusedPrivateProperty")
    private val pendingSyncLookup: FirestoreSyncPendingSyncLookup,
    @Suppress("UnusedPrivateProperty")
    private val stateStore: FirestoreSyncStateStore,
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
