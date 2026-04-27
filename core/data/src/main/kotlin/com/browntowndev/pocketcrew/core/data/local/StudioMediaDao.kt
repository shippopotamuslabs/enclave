package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioMediaDao {
    @Query("SELECT * FROM studio_gallery ORDER BY createdAt DESC")
    fun getAllMedia(): Flow<List<StudioMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: StudioMediaEntity): Long

    @Query("DELETE FROM studio_gallery WHERE id = :id")
    suspend fun deleteMedia(id: Long)

    @Query("SELECT * FROM studio_gallery WHERE id = :id")
    suspend fun getMediaById(id: Long): StudioMediaEntity?
}
