package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatDao {
    @Upsert
    abstract suspend fun upsert(chatEntity: ChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(chatEntity: ChatEntity): Long

    @Update
    abstract suspend fun update(chatEntity: ChatEntity): Int

    @Delete
    abstract suspend fun delete(chatEntity: ChatEntity): Int

    @Query("SELECT * FROM chat WHERE deleted = 0 ORDER BY pinned DESC, last_modified DESC")
    abstract fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat WHERE id = :id AND deleted = 0")
    abstract suspend fun getChatById(id: ChatId): ChatEntity?

    @Query("SELECT * FROM chat WHERE id IN (:ids) AND deleted = 0")
    abstract suspend fun getChatsByIds(ids: List<ChatId>): List<ChatEntity>

    @Query("UPDATE chat SET pending_sync = 2 WHERE pending_sync = 1")
    abstract suspend fun claimPending()

    @Query("SELECT * FROM chat WHERE pending_sync = 2")
    abstract suspend fun getClaimedForSync(): List<ChatEntity>

    @Query("UPDATE chat SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    abstract suspend fun markSynced(ids: List<ChatId>)

    @Query("UPDATE chat SET pinned = NOT pinned, pending_sync = 1, updated_at = :timestamp WHERE id = :chatId")
    abstract suspend fun updatePinStatus(
        chatId: ChatId,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    @Query("UPDATE chat SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :chatId")
    abstract suspend fun softDeleteById(
        chatId: ChatId,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    @Query("DELETE FROM chat WHERE id IN (:ids) AND deleted = 1")
    abstract suspend fun purgeDeleted(ids: List<ChatId>): Int

    /** Removes sync placeholder chats left behind when child Firestore docs arrive after a mass-delete. */
    @Query("DELETE FROM chat WHERE created = 0")
    abstract suspend fun deleteOrphanedPlaceholders(): Int

    @Query("UPDATE chat SET name = :newName, pending_sync = 1, updated_at = :timestamp WHERE id = :chatId")
    abstract suspend fun updateName(
        chatId: ChatId,
        newName: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    @Query(
        """
        SELECT * FROM chat
        WHERE deleted = 0 AND (
            id IN (
                SELECT DISTINCT chat_id FROM message
                WHERE deleted = 0 AND id IN (:messageIds)
            ) OR name LIKE '%' || :query || '%'
        )
        ORDER BY pinned DESC, last_modified DESC
    """,
    )
    abstract fun searchChats(
        query: String,
        messageIds: List<MessageId>,
    ): Flow<List<ChatEntity>>
}
