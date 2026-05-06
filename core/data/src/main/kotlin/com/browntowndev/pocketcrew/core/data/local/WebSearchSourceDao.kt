package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow

@Dao
interface WebSearchSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<WebSearchSourceEntity>)

    @Query("SELECT * FROM web_search_source WHERE message_id = :messageId")
    suspend fun getByMessageId(messageId: MessageId): List<WebSearchSourceEntity>

    /**
     * Returns a reactive Flow of all web search sources for messages in a given chat.
     * Room will re-emit whenever the web_search_source table is modified,
     * fulfilling the requirement to react to markExtracted updates.
     */
    @Query("""
        SELECT web_search_source.* FROM web_search_source
        INNER JOIN message ON web_search_source.message_id = message.id
        WHERE message.chat_id = :chatId
    """)
    fun getByChatIdFlow(chatId: ChatId): Flow<List<WebSearchSourceEntity>>

    @Query("UPDATE web_search_source SET extracted = 1 WHERE url = :url")
    suspend fun markExtracted(url: String)
}
