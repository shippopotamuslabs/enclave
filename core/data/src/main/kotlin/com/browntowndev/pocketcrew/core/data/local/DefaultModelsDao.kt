package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface DefaultModelsDao {
    @Query("SELECT * FROM default_models WHERE model_type = :modelType")
    suspend fun getDefault(modelType: ModelType): DefaultModelEntity?

    @Query("SELECT * FROM default_models")
    fun observeAll(): Flow<List<DefaultModelEntity>>

    @Query("SELECT * FROM default_models")
    suspend fun getAll(): List<DefaultModelEntity>

    @Upsert
    suspend fun upsert(entity: DefaultModelEntity)

    @Query("DELETE FROM default_models WHERE model_type = :modelType")
    suspend fun delete(modelType: ModelType)
}