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
     * Returns all active local models (CURRENT status) that have at least one configuration.
     * Excludes soft-deleted models (models with 0 configs).
     *
     * Soft-deleted models retain model_status='CURRENT' but have no configurations,
     * so this query uses EXISTS to filter them out.
     */
    @Query("""
        SELECT m.* FROM local_models m
        WHERE m.model_status = 'CURRENT'
        AND EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
    """)
    suspend fun getAllCurrent(): List<LocalModelEntity>

    /**
     * Flow version of getAllCurrent(). Returns active models with configs.
     * Excludes soft-deleted models via EXISTS subquery.
     */
    @Query("""
        SELECT m.* FROM local_models m
        WHERE m.model_status = 'CURRENT'
        AND EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
    """)
    fun observeAllCurrent(): Flow<List<LocalModelEntity>>

    /**
     * Returns soft-deleted models: LocalModelEntity rows that have ZERO configurations.
     * These are models that were downloaded but soft-deleted, available for re-download.
     */
    @Query("""
        SELECT m.* FROM local_models m
        WHERE m.model_status = 'CURRENT'
        AND NOT EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
    """)
    suspend fun getSoftDeletedModels(): List<LocalModelEntity>

    @Upsert
    suspend fun upsert(entity: LocalModelEntity): Long

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_models WHERE model_status = 'OLD'")
    suspend fun deleteOld()
}