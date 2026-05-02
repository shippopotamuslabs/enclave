package com.browntowndev.pocketcrew.core.data.local

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
        )
    ]
)
data class StudioMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prompt: String,
    val mediaUri: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val createdAt: Long = System.currentTimeMillis(),
    val albumId: Long? = null,
)
