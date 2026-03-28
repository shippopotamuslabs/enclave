package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiModelsDao {
    @Query("SELECT * FROM api_models ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ApiModelEntity>>

    @Query("SELECT * FROM api_models ORDER BY updated_at DESC")
    suspend fun getAll(): List<ApiModelEntity>

    @Query("SELECT * FROM api_models WHERE id = :id")
    suspend fun getById(id: Long): ApiModelEntity?

    @Upsert
    suspend fun upsert(entity: ApiModelEntity): Long

    @Query("DELETE FROM api_models WHERE id = :id")
    suspend fun deleteById(id: Long)
}
