package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsProviderDao {
    @Query("SELECT * FROM tts_providers")
    fun getTtsProviders(): Flow<List<TtsProviderEntity>>

    @Query("SELECT * FROM tts_providers")
    suspend fun getTtsProvidersSync(): List<TtsProviderEntity>

    @Query("SELECT * FROM tts_providers WHERE id = :id")
    suspend fun getTtsProvider(id: String): TtsProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTtsProvider(entity: TtsProviderEntity)

    @Query("DELETE FROM tts_providers WHERE id = :id")
    suspend fun deleteTtsProvider(id: String)
}
