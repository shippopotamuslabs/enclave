package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(chatEntity: ChatEntity): Long

    @Update
    abstract suspend fun update(chatEntity: ChatEntity): Int

    @Delete
    abstract suspend fun delete(chatEntity: ChatEntity): Int

    @Query("SELECT * FROM chat ORDER BY last_modified DESC")
    abstract fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat WHERE id = :id")
    abstract suspend fun getChatById(id: Long): ChatEntity?
}
