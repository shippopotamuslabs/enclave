package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE deleted = 0 ORDER BY created_at DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id AND deleted = 0")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET deleted = 1, updated_at = :timestamp, pending_sync = 1 WHERE id = :id")
    suspend fun deleteDocument(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM documents WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<DocumentEntity>

    @Query("UPDATE documents SET pending_sync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT id FROM documents")
    suspend fun getAllIds(): List<String>

    @RawQuery
    suspend fun insertBlockEmbedding(query: SupportSQLiteQuery): Long

    @RawQuery
    suspend fun searchSimilarBlocks(query: SupportSQLiteQuery): List<String>
}
