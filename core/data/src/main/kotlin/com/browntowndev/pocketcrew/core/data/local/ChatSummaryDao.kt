package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSummaryDao {
    @Query("SELECT * FROM chat_summary WHERE chat_id = :chatId LIMIT 1")
    fun getSummaryForChat(chatId: ChatId): Flow<ChatSummaryEntity?>

    @Query("SELECT * FROM chat_summary WHERE chat_id = :chatId LIMIT 1")
    suspend fun getSummaryForChatSync(chatId: ChatId): ChatSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSummary(summary: ChatSummaryEntity)

    @Query("DELETE FROM chat_summary WHERE chat_id = :chatId")
    suspend fun deleteSummaryForChat(chatId: ChatId)
}
