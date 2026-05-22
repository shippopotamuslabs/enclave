package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.enclave.domain.model.chat.ChatId
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatSummaryDao {
    @Query("SELECT * FROM chat_summary WHERE chat_id = :chatId AND deleted = 0 LIMIT 1")
    abstract fun getSummaryForChat(chatId: ChatId): Flow<ChatSummaryEntity?>

    @Query("SELECT * FROM chat_summary WHERE chat_id = :chatId AND deleted = 0 LIMIT 1")
    abstract suspend fun getSummaryForChatSync(chatId: ChatId): ChatSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrUpdateSummary(summary: ChatSummaryEntity)

    @Query("SELECT * FROM chat_summary WHERE id = :id LIMIT 1")
    abstract suspend fun getByIdSync(id: Long): ChatSummaryEntity?

    @Query("UPDATE chat_summary SET pending_sync = 2 WHERE pending_sync = 1")
    abstract suspend fun claimPending()

    @Query("SELECT * FROM chat_summary WHERE pending_sync = 2")
    abstract suspend fun getClaimedForSync(): List<ChatSummaryEntity>

    @Query("UPDATE chat_summary SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    abstract suspend fun markSynced(ids: List<Long>)

    @Query("UPDATE chat_summary SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun softDeleteById(
        id: Long,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    @Query("UPDATE chat_summary SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE chat_id = :chatId")
    abstract suspend fun deleteSummaryForChat(
        chatId: ChatId,
        timestamp: Long = System.currentTimeMillis(),
    ): Int

    @Query("SELECT * FROM chat_summary WHERE chat_id = :chatId LIMIT 1")
    abstract suspend fun getSummaryByChatIdIncludingDeletedSync(chatId: ChatId): ChatSummaryEntity?
}
