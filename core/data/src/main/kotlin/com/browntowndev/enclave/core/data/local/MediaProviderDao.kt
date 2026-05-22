package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaProviderDao {
    @Query("SELECT * FROM media_providers WHERE id = :id AND deleted = 0")
    suspend fun getMediaProvider(id: String): MediaProviderEntity?

    @Query("SELECT * FROM media_providers WHERE deleted = 0 ORDER BY updated_at DESC")
    fun observeAllMediaProviders(): Flow<List<MediaProviderEntity>>

    @Upsert
    suspend fun upsert(entity: MediaProviderEntity)

    @Query("UPDATE media_providers SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM media_providers WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("UPDATE media_providers SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM media_providers WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<MediaProviderEntity>

    @Query("UPDATE media_providers SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
