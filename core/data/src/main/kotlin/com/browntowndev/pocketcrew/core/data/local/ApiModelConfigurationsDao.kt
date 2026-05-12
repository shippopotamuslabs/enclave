@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import kotlinx.coroutines.flow.Flow


@Dao
interface ApiModelConfigurationsDao {
    @Query("SELECT * FROM api_model_configurations WHERE deleted = 0")
    fun observeAll(): Flow<List<ApiModelConfigurationEntity>>

    @Query("SELECT * FROM api_model_configurations WHERE api_credentials_id = :credentialsId AND deleted = 0")
    suspend fun getAllForCredentials(credentialsId: ApiCredentialsId): List<ApiModelConfigurationEntity>

    @Query("SELECT * FROM api_model_configurations WHERE id = :id AND deleted = 0")
    suspend fun getById(id: ApiModelConfigurationId): ApiModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: ApiModelConfigurationEntity)

    @Query("UPDATE api_model_configurations SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: ApiModelConfigurationId, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM api_model_configurations WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<ApiModelConfigurationId>)

    @Query("UPDATE api_model_configurations SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE api_credentials_id = :credentialsId")
    suspend fun deleteAllForCredentials(credentialsId: ApiCredentialsId, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE api_model_configurations SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM api_model_configurations WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<ApiModelConfigurationEntity>

    @Query("UPDATE api_model_configurations SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<ApiModelConfigurationId>)
}
