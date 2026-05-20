package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirestorePendingSyncEngineTest {
    @Test
    fun `empty adapter payload stays pending because no cloud write occurred`() =
        runTest {
            val adapter = EmptyPayloadAdapter()
            val firestore = mockk<FirebaseFirestore>(relaxed = true)
            every { firestore.runBatch(any()) } returns Tasks.forResult(null)
            val repairer = mockk<FirestoreSensitiveFieldRepairer>()
            every { repairer.repairOutbound(any(), any(), any()) } answers { thirdArg() }
            val ctx = mockk<FirestoreSyncContext>(relaxed = true)
            every { ctx.firestore } returns firestore
            every { ctx.firestoreSensitiveFieldRepairer } returns repairer
            every { ctx.loggingPort } returns mockk<LoggingPort>(relaxed = true)
            val engine =
                FirestorePendingSyncEngine(
                    ctx = ctx,
                    adapters = listOf(adapter),
                    syncStateStore = DeterministicPendingSyncStateStore(),
                )

            val result = engine.syncPendingChanges(userId = "alice")

            assertTrue(result.isSuccess)
            assertEquals(listOf("child-1"), adapter.claimed)
            assertEquals(emptyList<String>(), adapter.markedSynced)
        }
}

private class EmptyPayloadAdapter : EntitySyncAdapter<String> {
    val claimed = mutableListOf<String>()
    val markedSynced = mutableListOf<String>()
    override val collectionPath: String = "message_files"

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<String> = listOf("child-1").also { claimed += it }

    override suspend fun markSynced(entities: List<String>) {
        markedSynced += entities
    }

    override suspend fun toFirestore(local: String): Map<String, Any?> = emptyMap()

    override fun fromFirestore(doc: DocumentSnapshot): String? = doc.id

    override suspend fun upsertLocal(
        entity: String,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) = Unit

    override suspend fun deleteLocal(id: String) = Unit

    override fun getEntityId(entity: String): String = entity
}

private class DeterministicPendingSyncStateStore : FirestoreSyncStateStore() {
    override suspend fun deviceId(): String = "device-a"

    override fun newClientMutationId(): String = "mutation-a"

    override fun currentTimeMillis(): Long = 1700000001000L
}
