package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val json: String,
    val title: String,
    val author: String? = null,
    @ColumnInfo(name = "target_format") val targetFormat: String = "PDF",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    @ColumnInfo(name = "pending_sync") val pendingSync: Int = 0,
)
