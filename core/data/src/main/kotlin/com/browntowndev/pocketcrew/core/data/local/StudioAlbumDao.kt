package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioAlbumDao {
    @Query("SELECT * FROM studio_albums ORDER BY name ASC")
    fun observeAllAlbums(): Flow<List<StudioAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: StudioAlbumEntity): Long

    @Query("UPDATE studio_albums SET name = :name WHERE id = :id")
    suspend fun updateAlbumName(id: Long, name: String)

    @Query("DELETE FROM studio_albums WHERE id = :id")
    suspend fun deleteAlbum(id: Long)
}
