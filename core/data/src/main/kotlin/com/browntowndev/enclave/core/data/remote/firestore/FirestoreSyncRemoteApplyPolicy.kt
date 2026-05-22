package com.browntowndev.enclave.core.data.remote.firestore

/**
 * Conflict policy for event-feed remote applies (FEAT-cloud-sync-event-feed spec §Conflict Policy).
 */
internal object FirestoreSyncRemoteApplyPolicy {
    /**
     * When local row is dirty and the event originated on another device, preserve local state.
     */
    fun shouldPreserveLocalDirtyRow(
        pendingSync: Int,
        eventDeviceId: String?,
        localDeviceId: String,
    ): Boolean = pendingSync != 0 && (eventDeviceId == null || eventDeviceId != localDeviceId)

    /**
     * Echo from the same device while local row is still dirty — allow handler merge to clear pending.
     */
    fun overwriteForSameDeviceEcho(
        pendingSync: Int,
        eventDeviceId: String?,
        localDeviceId: String,
    ): Boolean = pendingSync != 0 && eventDeviceId != null && eventDeviceId == localDeviceId
}
