@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (FirebaseFirestore / DocumentSnapshot), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreSyncProductionWiringTest {
    private lateinit var database: EnclaveDatabase

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `decode failure does not advance lastAppliedSequence`() =
        runTest {
            val stateStore = TrackingSyncStateStore(initialSequence = 4)
            val adapter = FakeSyncAdapter<ChatEntity>(collectionPath = "chats", fromFirestoreResult = null)
            val snapshot = mockk<DocumentSnapshot>()
            every { snapshot.exists() } returns true
            every { snapshot.data } returns mapOf("name" to "cipher")
            every { snapshot.getString("_sync_device_id") } returns null

            val docRef = mockk<DocumentReference>()
            val firestore = mockk<FirebaseFirestore>()
            every { firestore.document("users/alice/chats/chat-bad") } returns docRef
            every { docRef.get() } returns Tasks.forResult(snapshot)

            // Real in-memory Room database: `database.withTransaction` runs for real
            // (the previous mockkStatic stub of RoomDatabaseKt is gone).
            val localStore =
                RoomFirestoreSyncLocalStore(
                    database = database,
                    firestore = firestore,
                    userIdProvider = { "alice" },
                    adapterLookup = mapOf("chats" to adapter),
                    repairer = FakeFirestoreSensitiveFieldRepairer(),
                    embeddingCoordinator = testEmbeddingRegenerationCoordinator(),
                    loggingPort = FakeLoggingPort(),
                    syncStateStore = stateStore,
                    pendingSyncLookup = FirestoreSyncPendingSyncLookup(database),
                )
            val applier =
                FirestoreSyncEventApplier(
                    sourceDocuments =
                        object : FirestoreSyncSourceDocumentStore {
                            override suspend fun getDocument(path: String): Map<String, Any?>? = mapOf("name" to "cipher")
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

    @Test
    fun `applying a studio_gallery upsert fires the media download trigger`() =
        runTest {
            val entity = StudioMediaEntity(id = "m1", prompt = "p", mediaUri = "u", mediaType = "IMAGE")
            val adapter =
                FakeSyncAdapter<StudioMediaEntity>(
                    collectionPath = "studio_gallery",
                    fromFirestoreResult = entity,
                )

            val snapshot = mockk<DocumentSnapshot>()
            every { snapshot.exists() } returns true
            every { snapshot.data } returns mapOf("media_type" to "IMAGE")
            every { snapshot.getString("_sync_device_id") } returns null

            val docRef = mockk<DocumentReference>()
            val firestore = mockk<FirebaseFirestore>()
            every { firestore.document("users/alice/studio_gallery/m1") } returns docRef
            every { docRef.get() } returns Tasks.forResult(snapshot)

            var triggered = false
            val localStore =
                RoomFirestoreSyncLocalStore(
                    database = database,
                    firestore = firestore,
                    userIdProvider = { "alice" },
                    adapterLookup = mapOf("studio_gallery" to adapter),
                    repairer = FakeFirestoreSensitiveFieldRepairer(),
                    embeddingCoordinator = testEmbeddingRegenerationCoordinator(),
                    loggingPort = FakeLoggingPort(),
                    syncStateStore = TrackingSyncStateStore(initialSequence = 4),
                    // Real lookup over the empty database: pendingSync is 0, as the stub returned.
                    pendingSyncLookup = FirestoreSyncPendingSyncLookup(database),
                    mediaDownloadTrigger = { triggered = true },
                )

            // upsert() runs applyAdapterUpsert directly (no transaction wrapper), exercising the
            // post-apply collectionPath dispatch.
            localStore.upsert(
                collectionPath = "studio_gallery",
                entityId = "m1",
                data = mapOf("media_type" to "IMAGE"),
                eventDeviceId = null,
            )

            assertEquals(true, triggered)
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
