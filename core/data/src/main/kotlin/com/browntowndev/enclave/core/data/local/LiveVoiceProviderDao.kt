package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveVoiceProviderDao {
    @Query("SELECT * FROM live_voice_providers WHERE deleted = 0")
    fun getLiveVoiceProviders(): Flow<List<LiveVoiceProviderEntity>>

    @Query("SELECT * FROM live_voice_providers WHERE deleted = 0")
    suspend fun getLiveVoiceProvidersSync(): List<LiveVoiceProviderEntity>

    @Query("SELECT * FROM live_voice_providers WHERE id = :id AND deleted = 0")
    suspend fun getLiveVoiceProvider(id: String): LiveVoiceProviderEntity?

    @Upsert
    suspend fun upsertLiveVoiceProvider(entity: LiveVoiceProviderEntity)

    @Query("UPDATE live_voice_providers SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM live_voice_providers WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("UPDATE live_voice_providers SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM live_voice_providers WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<LiveVoiceProviderEntity>

    @Query("UPDATE live_voice_providers SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
