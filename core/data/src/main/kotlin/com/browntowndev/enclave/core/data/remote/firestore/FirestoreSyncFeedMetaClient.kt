package com.browntowndev.enclave.core.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

internal class FirestoreSyncFeedMetaClient(
    private val firestore: FirebaseFirestore,
) {
    suspend fun readFeedStatus(userId: String): FirestoreSyncFeedStatus? {
        val snapshot =
            firestore
                .document("users/$userId/sync_meta/state")
                .get()
                .await()
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        return FirestoreSyncFeedStatus(
            nextSequence = (data["nextSequence"] as? Number)?.toLong() ?: 0L,
            feedInitialized = data["feedInitialized"] as? Boolean ?: false,
            feedEpoch = (data["feedEpoch"] as? Number)?.toLong() ?: 1L,
            oldestRetainedSequence = (data["oldestRetainedSequence"] as? Number)?.toLong() ?: 1L,
            schemaVersion = (data["schemaVersion"] as? Number)?.toLong() ?: 1L,
        )
    }

    suspend fun syncEventExists(
        userId: String,
        sequence: Long,
    ): Boolean {
        val snapshot =
            firestore
                .document("users/$userId/sync_events/${paddedSequence(sequence)}")
                .get()
                .await()
        return snapshot.exists()
    }

    suspend fun awaitSyncEvent(
        userId: String,
        sequence: Long,
        attempts: Int = GAP_VERIFY_ATTEMPTS,
        delayMillis: Long = GAP_VERIFY_DELAY_MS,
    ): Boolean {
        repeat(attempts) { attempt ->
            if (syncEventExists(userId, sequence)) return true
            if (attempt < attempts - 1) delay(delayMillis)
        }
        return false
    }

    private fun paddedSequence(sequence: Long): String =
        sequence.toString().padStart(SEQUENCE_DOC_ID_WIDTH, '0')

    companion object {
        const val GAP_VERIFY_ATTEMPTS = 3
        const val GAP_VERIFY_DELAY_MS = 500L
        private const val SEQUENCE_DOC_ID_WIDTH = 20
    }
}
