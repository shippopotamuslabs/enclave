package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioAlbumDao {
    @Query("SELECT * FROM studio_albums WHERE deleted = 0 ORDER BY name ASC")
    fun observeAllAlbums(): Flow<List<StudioAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: StudioAlbumEntity)

    @Query("UPDATE studio_albums SET name = :name, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun updateAlbumName(id: String, name: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM studio_albums WHERE id = :id AND deleted = 0")
    suspend fun getAlbumById(id: String): StudioAlbumEntity?

    @Query("UPDATE studio_albums SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM studio_albums WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("UPDATE studio_albums SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM studio_albums WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<StudioAlbumEntity>

    @Query("UPDATE studio_albums SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
