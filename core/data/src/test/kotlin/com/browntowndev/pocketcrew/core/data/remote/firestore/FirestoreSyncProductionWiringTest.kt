package com.browntowndev.pocketcrew.core.data.remote.firestore

import androidx.room.withTransaction
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FirestoreSyncProductionWiringTest {
    @Test
    fun `decode failure does not advance lastAppliedSequence`() =
        runTest {
            val stateStore = TrackingSyncStateStore(initialSequence = 4)
            val adapter = mockk<EntitySyncAdapter<ChatEntity>>()
            val snapshot = mockk<DocumentSnapshot>()
            every { snapshot.exists() } returns true
            every { snapshot.data } returns mapOf("name" to "cipher")
            every { snapshot.getString("_sync_device_id") } returns null
            every { adapter.fromFirestore(snapshot) } returns null

            val docRef = mockk<DocumentReference>()
            val firestore = mockk<FirebaseFirestore>()
            every { firestore.document("users/alice/chats/chat-bad") } returns docRef
            every { docRef.get() } returns Tasks.forResult(snapshot)

            val database = mockk<PocketCrewDatabase>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { database.withTransaction<Unit>(any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val block = invocation.args[0] as suspend () -> Unit
                block()
            }

            val localStore =
                RoomFirestoreSyncLocalStore(
                    database = database,
                    firestore = firestore,
                    userIdProvider = { "alice" },
                    adapterLookup = mapOf("chats" to adapter),
                    repairer = mockk<FirestoreSensitiveFieldRepairer>(relaxed = true) {
                        every { repairInbound(any(), any(), any()) } answers { thirdArg() }
                    },
                    embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true),
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                    syncStateStore = stateStore,
                    pendingSyncLookup = mockk(relaxed = true) {
                        coEvery { pendingSync(any(), any()) } returns 0
                    },
                )
            val applier =
                FirestoreSyncEventApplier(
                    sourceDocuments =
                        object : FirestoreSyncSourceDocumentStore {
                            override suspend fun getDocument(path: String): Map<String, Any?>? =
                                mapOf("name" to "cipher")
                        },
                    localStore = localStore,
                    stateStore = stateStore,
                )

            assertThrows(IllegalStateException::class.java) {
                runTest {
                    applier.apply(
                        userId = "alice",
                        event =
                            FirestoreSyncEvent(
                                sequence = 5,
                                schemaVersion = 1,
                                operation = "upsert",
                                collectionPath = "chats",
                                entityType = "chats",
                                entityId = "chat-bad",
                                entityPath = "users/alice/chats/chat-bad",
                                sourceExists = true,
                                sourceUpdatedAt = 1700000001000L,
                                sourceDeleted = false,
                                deviceId = null,
                                clientMutationId = null,
                                triggerEventId = "event-5",
                                emittedAtMillis = 1700000001001L,
                            ),
                    )
                }
            }

            assertEquals(4, stateStore.sequence)
        }
}

private class TrackingSyncStateStore(
    initialSequence: Long,
) : FirestoreSyncStateStore() {
    var sequence: Long = initialSequence

    override suspend fun lastAppliedSequence(userId: String): Long = sequence

    override suspend fun setLastAppliedSequence(
        userId: String,
        sequence: Long,
    ) {
        this.sequence = sequence
    }

    override suspend fun deviceId(): String = "device-a"
}
