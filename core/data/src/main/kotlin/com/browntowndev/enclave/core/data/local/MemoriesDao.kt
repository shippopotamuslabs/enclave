package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoriesDao {
    @Upsert
    suspend fun insertMemory(memory: MemoriesEntity)

    @Update
    suspend fun updateMemory(memory: MemoriesEntity)

    @Query("UPDATE memories SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM memories WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<String>)

    @Query("SELECT * FROM memories WHERE id = :id AND deleted = 0")
    suspend fun getMemoryById(id: String): MemoriesEntity?

    @Query("SELECT * FROM memories WHERE category IN (:categories) AND deleted = 0")
    suspend fun getMemoriesByCategories(categories: List<MemoryCategory>): List<MemoriesEntity>

    @Query("SELECT * FROM memories WHERE deleted = 0 ORDER BY updated_at DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoriesEntity>>

    @RawQuery
    suspend fun searchMemoriesRaw(query: SupportSQLiteQuery): List<MemoriesEntity>

    @Query("UPDATE memories SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM memories WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<MemoriesEntity>

    @Query("UPDATE memories SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT id FROM memories WHERE deleted = 0")
    suspend fun getAllIds(): List<String>
}
