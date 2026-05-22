package com.browntowndev.enclave.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_albums")
data class StudioAlbumEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @androidx.room.ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
)
