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

    @Query("SELECT * FROM local_models")
    suspend fun getAll(): List<LocalModelEntity>

    /**
     * Returns all active local models that have at least one configuration.
     * Excludes soft-deleted models, which have no configurations.
     */
    @Query("""
        SELECT m.* FROM local_models m
        WHERE EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
    """)
    suspend fun getAllActive(): List<LocalModelEntity>

    /**
     * Flow version of getAllActive(). Returns active models with configs.
     * Excludes soft-deleted models via EXISTS subquery.
     */
    @Query("""
        SELECT m.* FROM local_models m
        WHERE EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
    """)
    fun observeAllActive(): Flow<List<LocalModelEntity>>

    /**
     * Returns soft-deleted models: LocalModelEntity rows that have ZERO configurations.
     * These are models that were downloaded but soft-deleted, available for re-download.
     */
    @Query("""
        SELECT m.* FROM local_models m
        WHERE NOT EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
    """)
    suspend fun getSoftDeletedModels(): List<LocalModelEntity>

    @Upsert
    suspend fun upsert(entity: LocalModelEntity): Long

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteById(id: Long)
}
