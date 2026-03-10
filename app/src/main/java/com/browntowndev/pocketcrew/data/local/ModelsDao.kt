package com.browntowndev.pocketcrew.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelsDao {
    @Query("SELECT display_name FROM models WHERE model_type = :modelType AND model_status = 'CURRENT'")
    suspend fun getDisplayName(modelType: ModelType): String?

    @Query("SELECT * FROM models WHERE model_type = :modelType AND model_status = 'CURRENT'")
    suspend fun getModelEntity(modelType: ModelType): ModelEntity?

    @Query("SELECT * FROM models WHERE model_status = 'CURRENT'")
    suspend fun getAll(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE model_status = 'CURRENT'")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE model_type = :modelType AND model_status = 'CURRENT'")
    fun observeModelEntity(modelType: ModelType): Flow<ModelEntity?>

    @Query("SELECT * FROM models WHERE model_type = :modelType AND model_status = :modelStatus")
    suspend fun getModelEntityByStatus(modelType: ModelType, modelStatus: ModelStatus): ModelEntity?

    @Query("SELECT * FROM models WHERE model_status = 'OLD'")
    suspend fun getAllOld(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE model_type = :modelType")
    suspend fun getAllByModelType(modelType: ModelType): List<ModelEntity>

    @Upsert
    suspend fun upsert(entity: ModelEntity)

    @Query("DELETE FROM models WHERE model_status = 'OLD'")
    suspend fun deleteAllOld()

    @Query("DELETE FROM models")
    suspend fun deleteAll()
}
