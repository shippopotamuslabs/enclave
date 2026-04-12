package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

@Dao
interface MessageVisionAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageVisionAnalysisEntity)

    @Query(
        "SELECT * FROM message_vision_analysis WHERE user_message_id IN (:userMessageIds) ORDER BY created_at ASC"
    )
    suspend fun getByUserMessageIds(
        userMessageIds: List<MessageId>
    ): List<MessageVisionAnalysisEntity>
}
