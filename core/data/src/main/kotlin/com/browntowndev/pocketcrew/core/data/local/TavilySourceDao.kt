package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow

@Dao
interface TavilySourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<TavilySourceEntity>)

    @Query("SELECT * FROM tavily_source WHERE message_id = :messageId")
    suspend fun getByMessageId(messageId: MessageId): List<TavilySourceEntity>

    /**
     * Returns a reactive Flow of all Tavily sources for messages in a given chat.
     * Room will re-emit whenever the tavily_source table is modified,
     * ensuring that changes (e.g. extracted flag updates) propagate to UI.
     */
    @Query("""
        SELECT tavily_source.* FROM tavily_source
        INNER JOIN message ON tavily_source.message_id = message.id
        WHERE message.chat_id = :chatId
    """)
    fun getByChatIdFlow(chatId: ChatId): Flow<List<TavilySourceEntity>>

    @Query("UPDATE tavily_source SET extracted = 1 WHERE url = :url")
    suspend fun markExtracted(url: String)
}