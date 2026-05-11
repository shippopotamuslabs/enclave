package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow

@Dao
interface WebSearchSourceDao {
    @Upsert
    suspend fun insertAll(sources: List<WebSearchSourceEntity>)

    @Upsert
    suspend fun upsert(entity: WebSearchSourceEntity)

    @Query("SELECT * FROM web_search_source WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): WebSearchSourceEntity?

    @Query("UPDATE web_search_source SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM web_search_source WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("SELECT * FROM web_search_source WHERE message_id = :messageId AND deleted = 0")
    suspend fun getByMessageId(messageId: MessageId): List<WebSearchSourceEntity>

    /**
     * Returns a reactive Flow of all web search sources for messages in a given chat.
     * Room will re-emit whenever the web_search_source table is modified,
     * fulfilling the requirement to react to markExtracted updates.
     */
    @Query("""
        SELECT web_search_source.* FROM web_search_source
        INNER JOIN message ON web_search_source.message_id = message.id
        WHERE message.chat_id = :chatId AND web_search_source.deleted = 0
    """)
    fun getByChatIdFlow(chatId: ChatId): Flow<List<WebSearchSourceEntity>>

    @Query("UPDATE web_search_source SET extracted = 1, pending_sync = 1, updated_at = :timestamp WHERE url = :url")
    suspend fun markExtracted(url: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE web_search_source SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM web_search_source WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<WebSearchSourceEntity>

    @Query("UPDATE web_search_source SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
