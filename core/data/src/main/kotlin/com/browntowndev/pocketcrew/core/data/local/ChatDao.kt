package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(chatEntity: ChatEntity): Long

    @Update
    abstract suspend fun update(chatEntity: ChatEntity): Int

    @Delete
    abstract suspend fun delete(chatEntity: ChatEntity): Int

    @Query("SELECT * FROM chat ORDER BY pinned DESC, last_modified DESC")
    abstract fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat WHERE id = :id")
    abstract suspend fun getChatById(id: ChatId): ChatEntity?

    @Query("SELECT * FROM chat WHERE id IN (:ids)")
    abstract suspend fun getChatsByIds(ids: List<ChatId>): List<ChatEntity>

    @Query("UPDATE chat SET pinned = NOT pinned WHERE id = :chatId")
    abstract suspend fun updatePinStatus(chatId: ChatId): Int

    @Query("DELETE FROM chat WHERE id = :chatId")
    abstract suspend fun deleteById(chatId: ChatId): Int

    @Query("UPDATE chat SET name = :newName WHERE id = :chatId")
    abstract suspend fun updateName(chatId: ChatId, newName: String): Int

    @Query("""
        SELECT * FROM (
            SELECT chat.* FROM chat
            JOIN message ON chat.id = message.chat_id
            JOIN message_search ON message.rowid = message_search.rowid
            WHERE message_search MATCH :ftsQuery
            
            UNION
            
            SELECT * FROM chat
            WHERE name LIKE '%' || :query || '%'
        )
        ORDER BY pinned DESC, last_modified DESC
    """)
    abstract fun searchChats(query: String, ftsQuery: String): Flow<List<ChatEntity>>
}
