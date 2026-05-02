package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoriesEntity)

    @Update
    suspend fun updateMemory(memory: MemoriesEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoriesEntity)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: String): MemoriesEntity?

    @Query("SELECT * FROM memories WHERE category IN (:categories)")
    suspend fun getMemoriesByCategories(categories: List<MemoryCategory>): List<MemoriesEntity>

    @Query("SELECT * FROM memories ORDER BY updated_at DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoriesEntity>>

    @RawQuery
    suspend fun searchMemoriesRaw(query: SupportSQLiteQuery): List<MemoriesEntity>
}
