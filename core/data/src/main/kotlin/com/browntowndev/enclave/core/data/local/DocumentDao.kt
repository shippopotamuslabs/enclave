package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE deleted = 0 ORDER BY created_at DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id AND deleted = 0")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query(
        """
        SELECT documents.* FROM documents
        INNER JOIN message ON documents.message_id = message.id
        WHERE message.chat_id = :chatId AND documents.deleted = 0
        """,
    )
    fun getDocumentsByChatIdFlow(chatId: ChatId): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE message_id = :messageId AND deleted = 0")
    fun getDocumentsByMessageIdFlow(messageId: MessageId): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET deleted = 1, updated_at = :timestamp, pending_sync = 1 WHERE id = :id")
    suspend fun deleteDocument(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE documents SET target_format = :targetFormat, updated_at = :timestamp, pending_sync = 1 WHERE id = :artifactId",
    )
    suspend fun updateTargetFormat(
        artifactId: String,
        targetFormat: MimeType,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE documents SET deleted = 1, updated_at = :timestamp, pending_sync = 1 WHERE message_id = :messageId",
    )
    suspend fun softDeleteByMessageId(
        messageId: MessageId,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        """
        UPDATE documents
        SET deleted = 1,
            updated_at = :timestamp,
            pending_sync = 1
        WHERE deleted = 0
          AND message_id IN (
              SELECT id
              FROM message
              WHERE chat_id = :chatId
                AND deleted = 0
          )
        """,
    )
    suspend fun softDeleteByChatId(
        chatId: ChatId,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE documents SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM documents WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<DocumentEntity>

    @Query("UPDATE documents SET pending_sync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT id FROM documents")
    suspend fun getAllIds(): List<String>

    @RawQuery
    suspend fun deleteBlockEmbedding(query: SupportSQLiteQuery): Int

    @RawQuery
    suspend fun insertBlockEmbedding(query: SupportSQLiteQuery): Long

    @RawQuery
    suspend fun searchSimilarBlocks(query: SupportSQLiteQuery): List<String>
}
