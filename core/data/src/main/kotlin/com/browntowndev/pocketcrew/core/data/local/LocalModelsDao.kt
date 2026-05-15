package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalModelsDao {
    @Query("SELECT * FROM local_models WHERE id = :id AND deleted = 0")
    suspend fun getById(id: LocalModelId): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE sha256 = :sha256 AND deleted = 0")
    suspend fun getBySha256(sha256: String): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE deleted = 0")
    suspend fun getAll(): List<LocalModelEntity>

    /**
     * Returns all active local models that have at least one configuration.
     * Excludes soft-deleted models, which have no configurations.
     */
    @Query(
        """
        SELECT m.* FROM local_models m
        WHERE m.deleted = 0 AND EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id AND c.deleted = 0)
    """,
    )
    suspend fun getAllActive(): List<LocalModelEntity>

    /**
     * Flow version of getAllActive(). Returns active models with configs.
     * Excludes soft-deleted models via EXISTS subquery.
     */
    @Query(
        """
        SELECT m.* FROM local_models m
        WHERE m.deleted = 0 AND EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id AND c.deleted = 0)
    """,
    )
    fun observeAllActive(): Flow<List<LocalModelEntity>>

    /**
     * Returns soft-deleted models: LocalModelEntity rows that have ZERO configurations.
     * These are models that were downloaded but soft-deleted, available for re-download.
     */
    @Query(
        """
        SELECT m.* FROM local_models m
        WHERE m.deleted = 0 AND NOT EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id AND c.deleted = 0)
    """,
    )
    suspend fun getSoftDeletedModels(): List<LocalModelEntity>

    @Upsert
    suspend fun upsert(entity: LocalModelEntity): Long

    @Query("UPDATE local_models SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: LocalModelId,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM local_models WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<LocalModelId>)

    @Query("UPDATE local_models SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM local_models WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<LocalModelEntity>

    @Query("UPDATE local_models SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<LocalModelId>)
}
