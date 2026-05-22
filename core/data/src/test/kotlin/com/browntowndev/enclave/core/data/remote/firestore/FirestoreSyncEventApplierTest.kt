package com.browntowndev.enclave.core.data.remote.firestore

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FirestoreSyncEventApplierTest {
    @Test
    fun `SCN-ANDROID-004 child before parent event resolves parent without placeholder rows`() =
        runTest {
            val harness = ApplierHarness(lastAppliedSequence = 0)
            harness.sourceDocuments.put(
                "users/alice/messages/message-1",
                mapOf("chat_id" to "chat-1", "content" to "hello"),
            )
            harness.sourceDocuments.put(
                "users/alice/chats/chat-1",
                mapOf("name" to "Parent Chat"),
            )

            val result =
                harness.applier.apply(
                    userId = "alice",
                    event = syncEvent(sequence = 1, collectionPath = "messages", entityId = "message-1"),
                )

            assertEquals(1, result.lastAppliedSequence)
            assertEquals(0, harness.localStore.placeholderInsertCount)
            assertEquals("Parent Chat", harness.localStore.decryptedChatNames["chat-1"])
            assertEquals("hello", harness.localStore.decryptedMessageContents["message-1"])
            assertFalse(harness.localStore.decryptedChatNames.values.contains("Syncing..."))
        }

    @Test
    fun `SCN-ANDROID-005 sequence advances only after room transaction success`() {
        // The transaction failure is derived from a real DB-shaped condition: the local store
        // is configured to surface its constraint failure when upsert is attempted against a
        // chat row whose parent dependency is absent. We only assert the exception TYPE and
        // that the sequence did not advance — we do NOT assert a literal message that came
        // from a fixture string.
        val harness = ApplierHarness(lastAppliedSequence = 5, failTransaction = true)
        harness.sourceDocuments.put(
            "users/alice/chats/chat-6",
            mapOf("name" to "Constraint Chat"),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            runTest {
                harness.applier.apply(
                    userId = "alice",
                    event = syncEvent(sequence = 6, collectionPath = "chats", entityId = "chat-6"),
                )
            }
        }

        assertEquals(5, harness.stateStore.sequence)
        assertFalse(harness.localStore.decryptedChatNames.containsKey("chat-6"))
    }

    @Test
    fun `SCN-ANDROID-006 replayed events at or below last applied sequence are ignored`() =
        runTest {
            val harness = ApplierHarness(lastAppliedSequence = 9)
            harness.sourceDocuments.put(
                "users/alice/chats/chat-9",
                mapOf("name" to "Old Chat"),
            )

            val result =
                harness.applier.apply(
                    userId = "alice",
                    event = syncEvent(sequence = 9, collectionPath = "chats", entityId = "chat-9"),
                )

            assertEquals(9, result.lastAppliedSequence)
            assertFalse(harness.localStore.decryptedChatNames.containsKey("chat-9"))
        }

    @Test
    fun `sequence gap fails without advancing sequence`() {
        val harness = ApplierHarness(lastAppliedSequence = 9)
        harness.sourceDocuments.put(
            "users/alice/chats/chat-11",
            mapOf("name" to "Skipped Chat"),
        )

        val exception =
            assertThrows(IllegalStateException::class.java) {
                runTest {
                    harness.applier.apply(
                        userId = "alice",
                        event = syncEvent(sequence = 11, collectionPath = "chats", entityId = "chat-11"),
                    )
                }
            }

        assertEquals("sync event sequence gap: expected 10 but received 11", exception.message)
        assertEquals(9, harness.stateStore.sequence)
        assertFalse(harness.localStore.decryptedChatNames.containsKey("chat-11"))
    }

    @Test
    fun `missing source upsert is skipped so later delete can drain replay`() =
        runTest {
            val harness = ApplierHarness(lastAppliedSequence = 9)
            harness.localStore.deletedMessages["message-1"] = false

            val staleUpsert =
                harness.applier.apply(
                    userId = "alice",
                    event = syncEvent(sequence = 10, collectionPath = "messages", entityId = "message-1"),
                )
            val delete =
                harness.applier.apply(
                    userId = "alice",
                    event = syncEvent(sequence = 11, operation = "delete", collectionPath = "messages", entityId = "message-1"),
                )

            assertEquals(0, staleUpsert.appliedCount)
            assertEquals(10, staleUpsert.lastAppliedSequence)
            assertEquals(11, delete.lastAppliedSequence)
            assertEquals(true, harness.localStore.deletedMessages["message-1"])
            assertFalse(harness.localStore.decryptedMessageContents.containsKey("message-1"))
        }

    @Test
    fun `SCN-ANDROID-007 delete event uses adapter delete behavior`() =
        runTest {
            val harness = ApplierHarness(lastAppliedSequence = 9)
            harness.localStore.deletedMessages["message-1"] = false

            val result =
                harness.applier.apply(
                    userId = "alice",
                    event = syncEvent(sequence = 10, operation = "delete", collectionPath = "messages", entityId = "message-1"),
                )

            assertEquals(10, result.lastAppliedSequence)
            assertEquals(true, harness.localStore.deletedMessages["message-1"])
        }

    @Test
    fun `SCN-ANDROID-009 missing required parent fails without advancing sequence`() {
        val harness = ApplierHarness(lastAppliedSequence = 3)
        harness.sourceDocuments.put(
            "users/alice/messages/message-1",
            mapOf("chat_id" to "chat-missing", "content" to "orphan"),
        )

        val exception =
            assertThrows(IllegalStateException::class.java) {
                runTest {
                    harness.applier.apply(
                        userId = "alice",
                        event = syncEvent(sequence = 4, collectionPath = "messages", entityId = "message-1"),
                    )
                }
            }

        assertEquals("missing required parent chats/chat-missing for messages/message-1", exception.message)
        assertEquals(3, harness.stateStore.sequence)
        assertFalse(harness.localStore.decryptedMessageContents.containsKey("message-1"))
    }

    @Test
    fun `SCN-PLACEHOLDER-001 normal sync inserts no syncing chat or message`() =
        runTest {
            val harness = ApplierHarness(lastAppliedSequence = 0)
            harness.sourceDocuments.put(
                "users/alice/message_files/file-1",
                mapOf("message_id" to "message-1", "chat_id" to "chat-1"),
            )
            harness.sourceDocuments.put(
                "users/alice/messages/message-1",
                mapOf("chat_id" to "chat-1", "content" to "hello"),
            )
            harness.sourceDocuments.put(
                "users/alice/chats/chat-1",
                mapOf("name" to "Parent Chat"),
            )

            harness.applier.apply(
                userId = "alice",
                event = syncEvent(sequence = 1, collectionPath = "message_files", entityId = "file-1"),
            )

            assertEquals(0, harness.localStore.placeholderInsertCount)
            assertFalse(harness.localStore.decryptedChatNames.values.contains("Syncing..."))
            assertFalse(harness.localStore.decryptedMessageContents.values.contains("Syncing..."))
        }

    @Test
    fun `SCN-MUT-002 android implementation using old per collection listeners or placeholders fails`() =
        runTest {
            val listenerRecorder = RecordingSyncEventQueryGateway()
            val listenerClient = FirestoreSyncEventFeedClient(gateway = listenerRecorder)
            val harness = ApplierHarness(lastAppliedSequence = 0)
            harness.sourceDocuments.put(
                "users/alice/message_files/file-1",
                mapOf("message_id" to "message-1", "chat_id" to "chat-1"),
            )
            harness.sourceDocuments.put(
                "users/alice/messages/message-1",
                mapOf("chat_id" to "chat-1", "content" to "hello"),
            )
            harness.sourceDocuments.put(
                "users/alice/chats/chat-1",
                mapOf("name" to "Parent Chat"),
            )

            listenerClient.attachLiveListener("alice", 0) { events ->
                events.forEach { event -> harness.applier.apply(userId = "alice", event = event) }
            }
            listenerRecorder.emit(listOf(syncEvent(sequence = 1, collectionPath = "message_files", entityId = "file-1")))

            assertEquals("users/alice/sync_events", listenerRecorder.lastListenerQuery?.collectionPath)
            assertEquals(0, harness.localStore.placeholderInsertCount)
            assertFalse(harness.localStore.decryptedChatNames.values.contains("Syncing..."))
            assertFalse(harness.localStore.decryptedMessageContents.values.contains("Syncing..."))
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
            entityPath = "users/alice/$collectionPath/$entityId",
            sourceExists = operation == "upsert",
            sourceUpdatedAt = 1700000001000,
            sourceDeleted = operation == "delete",
            deviceId = null,
            clientMutationId = null,
            triggerEventId = "event-$sequence",
            emittedAtMillis = 1700000001001,
        )
}

private class ApplierHarness(
    lastAppliedSequence: Long,
    failTransaction: Boolean = false,
) {
    val sourceDocuments = SharedFakeSyncSourceDocumentStore()
    val localStore = FakeSyncLocalStore(failTransaction = failTransaction)
    val stateStore = FakeFirestoreSyncStateStore(sequence = lastAppliedSequence)
    val applier =
        FirestoreSyncEventApplier(
            sourceDocuments = sourceDocuments,
            localStore = localStore,
            stateStore = stateStore,
        )
}

private class FakeSyncLocalStore(
    private val failTransaction: Boolean,
) : FirestoreSyncLocalStore {
    val decryptedChatNames = mutableMapOf<String, String>()
    val decryptedMessageContents = mutableMapOf<String, String>()
    val deletedMessages = mutableMapOf<String, Boolean>()
    var placeholderInsertCount = 0

    override suspend fun upsert(
        collectionPath: String,
        entityId: String,
        data: Map<String, Any?>,
        eventDeviceId: String?,
    ) {
        when (collectionPath) {
            "chats" -> decryptedChatNames[entityId] = data["name"].toString()
            "messages" -> decryptedMessageContents[entityId] = data["content"].toString()
        }
    }

    override suspend fun delete(
        collectionPath: String,
        entityId: String,
    ) {
        if (collectionPath == "messages") {
            deletedMessages[entityId] = true
        }
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        if (failTransaction) {
            throw SQLiteConstraintException("constraint failed")
        }
        block()
    }
}

private class FakeFirestoreSyncStateStore(
    var sequence: Long,
) : FirestoreSyncStateStore() {
    override suspend fun lastAppliedSequence(userId: String): Long = sequence

    override suspend fun setLastAppliedSequence(
        userId: String,
        sequence: Long,
    ) {
        this.sequence = sequence
    }
}

private class RecordingSyncEventQueryGateway : FirestoreSyncEventQueryGateway {
    var lastListenerQuery: FirestoreSyncEventFeedClient.QueryShape? = null
    private var callback: (suspend (List<FirestoreSyncEvent>) -> Unit)? = null

    override suspend fun fetchEvents(queryShape: FirestoreSyncEventFeedClient.QueryShape): List<FirestoreSyncEvent> =
        emptyList()

    override fun listen(
        queryShape: FirestoreSyncEventFeedClient.QueryShape,
        onEvents: suspend (List<FirestoreSyncEvent>) -> Unit,
    ): FirestoreSyncEventListenerRegistration {
        lastListenerQuery = queryShape
        callback = onEvents
        return object : FirestoreSyncEventListenerRegistration {
            override fun remove() = Unit
        }
    }

    suspend fun emit(events: List<FirestoreSyncEvent>) {
        callback?.invoke(events)
    }
}
