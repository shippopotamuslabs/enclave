package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "studio_gallery",
    foreignKeys = [
        ForeignKey(
            entity = StudioAlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        androidx.room.Index("albumId"),
    ],
)
data class StudioMediaEntity(
    @PrimaryKey
    val id: String,
    val prompt: String,
    val mediaUri: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val createdAt: Long = System.currentTimeMillis(),
    val albumId: String? = null,
    val remoteUrl: String? = null,
    val remoteThumbnailUrl: String? = null,
    val syncStatus: String = "PENDING_UPLOAD",
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
    @ColumnInfo(name = "staging_remote_url")
    val stagingRemoteUrl: String? = null,
)
