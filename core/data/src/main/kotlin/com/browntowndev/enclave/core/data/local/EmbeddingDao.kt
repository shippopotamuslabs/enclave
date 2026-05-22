package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface EmbeddingDao {
    @RawQuery
    suspend fun searchSimilarMessages(query: SupportSQLiteQuery): List<String>

    @RawQuery
    suspend fun searchMemoryEmbeddingMatches(query: SupportSQLiteQuery): List<MemoryEmbeddingMatch>

    @RawQuery
    suspend fun insertEmbedding(query: SupportSQLiteQuery): Long

    @Transaction
    suspend fun replaceEmbedding(
        deleteQuery: SupportSQLiteQuery,
        insertQuery: SupportSQLiteQuery,
    ) {
        insertEmbedding(deleteQuery)
        insertEmbedding(insertQuery)
    }

    @androidx.room.Query("SELECT * FROM embeddings WHERE message_id = :messageId")
    suspend fun getEmbeddingMetadata(messageId: String): EmbeddingEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddingMetadata(entity: EmbeddingEntity)

    @androidx.room.Query("DELETE FROM embeddings WHERE message_id = :messageId")
    suspend fun deleteEmbeddingMetadata(messageId: String)
}
