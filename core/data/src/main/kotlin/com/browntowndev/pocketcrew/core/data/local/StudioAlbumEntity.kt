package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_albums")
data class StudioAlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
)
