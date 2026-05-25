package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.domain.port.inference.LoggingPort

/**
 * Detects a stale sync-event cursor: Room was wiped (destructive migration, adopt-cloud, or
 * clear data) while DataStore still holds a high [FirestoreSyncStateStore.lastAppliedSequence].
 * In that state replay and the live listener query `sequence > N` and return nothing, so the
 * app looks fresh-installed even though Firestore still has entity documents.
 */
internal class FirestoreSyncHydrationRecovery(
    private val chatDao: ChatDao,
    private val syncStateStore: FirestoreSyncStateStore,
    private val loggingPort: LoggingPort,
) {
    suspend fun resetStaleCursorIfLocalStoreEmpty(userId: String): Boolean {
        val lastApplied = syncStateStore.lastAppliedSequence(userId)
        if (lastApplied == 0L) return false
        if (chatDao.countActiveChats() > 0) return false
        loggingPort.warning(
            FirestoreSyncContext.TAG,
            "Local Room has no chats but lastAppliedSequence=$lastApplied; resetting sync cursor to replay cloud events",
        )
        syncStateStore.setLastAppliedSequence(userId, 0L)
        return true
    }
}
