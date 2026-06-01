package com.browntowndev.enclave.core.data.remote

import com.google.firebase.firestore.DocumentSnapshot

internal interface EntitySyncAdapter<RoomT> {
    val collectionPath: String

    /**
     * Atomically reserve any locally-pending writes for upload.
     *
     * Adapters whose pending state lives in a separate DataStore queue
     * (currently only the search-tool-settings queue) may capture the claim
     * result in a private field and surface it via [getClaimedForSync] —
     * the return value of this method is `Unit` to keep the existing
     * 20-adapter contract stable.
     */
    suspend fun claimPending()

    suspend fun getClaimedForSync(): List<RoomT>

    suspend fun markSynced(entities: List<RoomT>)

    suspend fun toFirestore(local: RoomT): Map<String, Any?>

    fun fromFirestore(doc: DocumentSnapshot): RoomT?

    suspend fun upsertLocal(
        entity: RoomT,
        doc: DocumentSnapshot,
        overwrite: Boolean = false,
    )

    suspend fun deleteLocal(id: String)

    fun getEntityId(entity: RoomT): String

    fun shouldDelete(entity: RoomT): Boolean = false

    suspend fun repairForSync(entity: RoomT): RoomT = entity
}
