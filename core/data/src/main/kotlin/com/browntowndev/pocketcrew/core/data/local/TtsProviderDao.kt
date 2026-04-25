package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsProviderDao {
    @Query("SELECT * FROM tts_providers")
    fun getTtsProviders(): Flow<List<TtsProviderEntity>>

    @Query("SELECT * FROM tts_providers")
    suspend fun getTtsProvidersSync(): List<TtsProviderEntity>

    @Query("SELECT * FROM tts_providers WHERE id = :id")
    suspend fun getTtsProvider(id: String): TtsProviderEntity?

    @Upsert
    suspend fun upsertTtsProvider(entity: TtsProviderEntity)

    @Query("DELETE FROM tts_providers WHERE id = :id")
    suspend fun deleteTtsProvider(id: String)
}
