package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

@Dao
interface TavilySourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<TavilySourceEntity>)

    @Query("SELECT * FROM tavily_source WHERE message_id = :messageId")
    suspend fun getByMessageId(messageId: MessageId): List<TavilySourceEntity>
}
