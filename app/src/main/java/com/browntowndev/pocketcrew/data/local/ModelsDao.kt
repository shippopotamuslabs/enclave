package com.browntowndev.pocketcrew.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.pocketcrew.domain.model.ModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelsDao {
    @Query("SELECT display_name FROM models WHERE model_type = :modelType")
    suspend fun getDisplayName(modelType: ModelType): String?

    @Query("SELECT * FROM models WHERE model_type = :modelType")
    suspend fun getModelEntity(modelType: ModelType): ModelEntity?

    @Query("SELECT * FROM models")
    suspend fun getAll(): List<ModelEntity>


    @Query("SELECT * FROM models")
    fun observeAll(): Flow<List<ModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ModelEntity)

    @Query("DELETE FROM models")
    suspend fun deleteAll()
}
