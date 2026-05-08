package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

@Dao
interface MessageFileDao {
    @Upsert
    suspend fun insert(entity: MessageFileEntity)

    @Query("SELECT * FROM message_file WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): MessageFileEntity?

    @Query("SELECT * FROM message_file WHERE message_id = :messageId AND deleted = 0")
    suspend fun getByMessageId(messageId: MessageId): List<MessageFileEntity>

    @Query("UPDATE message_file SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM message_file WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("UPDATE message_file SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM message_file WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<MessageFileEntity>

    @Query("UPDATE message_file SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)
}
