package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioMediaDao {
    @Query("SELECT * FROM studio_gallery WHERE deleted = 0 ORDER BY createdAt DESC")
    fun getAllMedia(): Flow<List<StudioMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: StudioMediaEntity)

    @Query("UPDATE studio_gallery SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM studio_gallery WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("SELECT * FROM studio_gallery WHERE id = :id AND deleted = 0")
    suspend fun getMediaById(id: String): StudioMediaEntity?

    @Query("UPDATE studio_gallery SET albumId = :albumId, pending_sync = 1, updated_at = :timestamp WHERE id IN (:mediaIds)")
    suspend fun updateMediaAlbum(mediaIds: List<String>, albumId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE studio_gallery SET albumId = NULL, pending_sync = 1, updated_at = :timestamp WHERE albumId = :albumId")
    suspend fun reassignMediaToDefault(albumId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE studio_gallery SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM studio_gallery WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<StudioMediaEntity>

    @Query("UPDATE studio_gallery SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT * FROM studio_gallery WHERE syncStatus = 'PENDING_UPLOAD' AND deleted = 0")
    suspend fun getPendingUploads(): List<StudioMediaEntity>
}
