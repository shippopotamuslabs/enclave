package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import kotlinx.coroutines.flow.Flow

@Dao
interface UtilityModelsDao {
    @Query("SELECT * FROM utility_models WHERE id = :id AND deleted = 0")
    suspend fun getById(id: UtilityModelId): UtilityModelEntity?

    @Query("SELECT * FROM utility_models WHERE utility_type = :utilityType AND deleted = 0")
    suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelEntity?

    @Query("SELECT * FROM utility_models WHERE sha256 = :sha256 AND deleted = 0")
    suspend fun getBySha256(sha256: String): UtilityModelEntity?

    @Query("SELECT * FROM utility_models WHERE deleted = 0")
    suspend fun getAll(): List<UtilityModelEntity>

    @Query("SELECT * FROM utility_models WHERE deleted = 0")
    fun observeAllActive(): Flow<List<UtilityModelEntity>>

    @Upsert
    suspend fun upsert(entity: UtilityModelEntity): Long

    @Query("UPDATE utility_models SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: UtilityModelId,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE utility_models SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM utility_models WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<UtilityModelEntity>

    @Query("UPDATE utility_models SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<UtilityModelId>)
}
