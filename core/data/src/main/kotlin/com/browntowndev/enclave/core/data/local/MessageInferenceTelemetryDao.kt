package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MessageInferenceTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(rows: List<MessageInferenceTelemetryEntity>)

    @Upsert
    abstract suspend fun upsert(entity: MessageInferenceTelemetryEntity)

    @Query("SELECT * FROM message_inference_telemetry WHERE id = :id")
    abstract suspend fun getById(id: String): MessageInferenceTelemetryEntity?

    @Query("SELECT * FROM message_inference_telemetry WHERE message_id = :messageId AND deleted = 0 ORDER BY sequence_index ASC")
    abstract fun observeByMessageId(messageId: String): Flow<List<MessageInferenceTelemetryEntity>>

    @Query("SELECT * FROM message_inference_telemetry WHERE chat_id = :chatId AND deleted = 0")
    abstract fun observeByChatId(chatId: String): Flow<List<MessageInferenceTelemetryEntity>>

    @Query("SELECT * FROM message_inference_telemetry WHERE deleted = 0")
    abstract fun observeAll(): Flow<List<MessageInferenceTelemetryEntity>>

    @Query("UPDATE message_inference_telemetry SET pending_sync = 2 WHERE pending_sync = 1")
    abstract suspend fun claimPending()

    @Query("SELECT * FROM message_inference_telemetry WHERE pending_sync = 2")
    abstract suspend fun getClaimedForSync(): List<MessageInferenceTelemetryEntity>

    @Query("UPDATE message_inference_telemetry SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    abstract suspend fun markSynced(ids: List<String>)

    @Query(
        """
        UPDATE message_inference_telemetry
        SET deleted = 1, pending_sync = 1, updated_at = :timestamp
        WHERE id = :id
        """,
    )
    abstract suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        """
        UPDATE message_inference_telemetry
        SET deleted = 1, pending_sync = 1, updated_at = :timestamp
        WHERE message_id = :messageId AND deleted = 0
        """,
    )
    abstract suspend fun softDeleteAllActiveForMessage(
        messageId: String,
        timestamp: Long,
    )

    @Transaction
    open suspend fun replaceTelemetryForMessage(
        messageId: String,
        rows: List<MessageInferenceTelemetryEntity>,
    ) {
        val ts = System.currentTimeMillis()
        softDeleteAllActiveForMessage(messageId, ts)
        if (rows.isNotEmpty()) {
            insertAll(
                rows.map { row ->
                    row.copy(
                        pendingSync = 1,
                        updatedAt = ts,
                        deleted = false,
                    )
                },
            )
        }
    }
}
