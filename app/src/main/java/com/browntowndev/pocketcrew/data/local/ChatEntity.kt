package com.browntowndev.pocketcrew.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "created")
    val created: Date,
    @ColumnInfo(name = "last_modified")
    val lastModified: Date,
    @ColumnInfo(name = "pinned")
    val pinned: Boolean
)
