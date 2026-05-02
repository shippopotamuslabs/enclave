package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaProviderDao {
    @Query("SELECT * FROM media_providers WHERE id = :id")
    suspend fun getMediaProvider(id: String): MediaProviderEntity?

    @Query("SELECT * FROM media_providers ORDER BY updatedAt DESC")
    fun observeAllMediaProviders(): Flow<List<MediaProviderEntity>>

    @Upsert
    suspend fun upsert(entity: MediaProviderEntity)

    @Query("DELETE FROM media_providers WHERE id = :id")
    suspend fun delete(id: String)
}
