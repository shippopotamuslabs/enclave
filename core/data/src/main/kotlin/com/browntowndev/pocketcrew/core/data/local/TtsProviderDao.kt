package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsProviderDao {
    @Query("SELECT * FROM tts_providers WHERE deleted = 0")
    fun getTtsProviders(): Flow<List<TtsProviderEntity>>

    @Query("SELECT * FROM tts_providers WHERE deleted = 0")
    suspend fun getTtsProvidersSync(): List<TtsProviderEntity>

    @Query("SELECT * FROM tts_providers WHERE id = :id AND deleted = 0")
    suspend fun getTtsProvider(id: String): TtsProviderEntity?

    @Upsert
    suspend fun upsertTtsProvider(entity: TtsProviderEntity)

    @Query("UPDATE tts_providers SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM tts_providers WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("UPDATE tts_providers SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM tts_providers WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<TtsProviderEntity>

    @Query("UPDATE tts_providers SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
