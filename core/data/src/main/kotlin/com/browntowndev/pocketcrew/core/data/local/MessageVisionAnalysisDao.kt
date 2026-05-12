package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

@Dao
interface MessageVisionAnalysisDao {
    @Upsert
    suspend fun upsert(entity: MessageVisionAnalysisEntity)

    @Query("SELECT * FROM message_vision_analysis WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): MessageVisionAnalysisEntity?

    @Query("UPDATE message_vision_analysis SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM message_vision_analysis WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query(
        "SELECT * FROM message_vision_analysis WHERE user_message_id IN (:userMessageIds) AND deleted = 0 ORDER BY created_at ASC",
    )
    suspend fun getByUserMessageIds(userMessageIds: List<MessageId>): List<MessageVisionAnalysisEntity>

    @Query("UPDATE message_vision_analysis SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM message_vision_analysis WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<MessageVisionAnalysisEntity>

    @Query("UPDATE message_vision_analysis SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
