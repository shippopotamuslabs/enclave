package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalModelsDao {
    @Query("SELECT * FROM local_models WHERE id = :id")
    suspend fun getById(id: Long): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE sha256 = :sha256")
    suspend fun getBySha256(sha256: String): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE model_status = 'CURRENT'")
    suspend fun getAllCurrent(): List<LocalModelEntity>

    @Query("SELECT * FROM local_models WHERE model_status = 'CURRENT'")
    fun observeAllCurrent(): Flow<List<LocalModelEntity>>

    @Upsert
    suspend fun upsert(entity: LocalModelEntity): Long

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteById(id: Long)
}