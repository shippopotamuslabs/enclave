@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId


@Dao
interface LocalModelConfigurationsDao {
    @Query("SELECT * FROM local_model_configurations WHERE local_model_id = :localModelId AND deleted = 0")
    suspend fun getAllForAsset(localModelId: LocalModelId): List<LocalModelConfigurationEntity>

    @Query("SELECT * FROM local_model_configurations WHERE id = :id AND deleted = 0")
    suspend fun getById(id: LocalModelConfigurationId): LocalModelConfigurationEntity?

    @Query("SELECT * FROM local_model_configurations WHERE local_model_id = :localModelId AND display_name = :displayName AND deleted = 0")
    suspend fun getByModelIdAndName(localModelId: LocalModelId, displayName: String): LocalModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: LocalModelConfigurationEntity)

    @Query("UPDATE local_model_configurations SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: LocalModelConfigurationId, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM local_model_configurations WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<LocalModelConfigurationId>)

    @Query("UPDATE local_model_configurations SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE local_model_id = :localModelId")
    suspend fun deleteAllForAsset(localModelId: LocalModelId, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE local_model_configurations SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM local_model_configurations WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<LocalModelConfigurationEntity>

    @Query("UPDATE local_model_configurations SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<LocalModelConfigurationId>)
}
