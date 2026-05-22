package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiCredentialsDao {
    @Query("SELECT * FROM api_credentials WHERE deleted = 0 ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ApiCredentialsEntity>>

    @Query("SELECT * FROM api_credentials WHERE deleted = 0 ORDER BY updated_at DESC")
    suspend fun getAll(): List<ApiCredentialsEntity>

    @Query("SELECT * FROM api_credentials WHERE id = :id AND deleted = 0")
    suspend fun getById(id: ApiCredentialsId): ApiCredentialsEntity?

    @Query("SELECT * FROM api_credentials WHERE credential_alias = :credentialAlias AND deleted = 0 LIMIT 1")
    suspend fun getByCredentialAlias(credentialAlias: String): ApiCredentialsEntity?

    @Query("SELECT * FROM api_credentials WHERE api_key_signature = :signature AND deleted = 0 LIMIT 1")
    suspend fun getByApiKeySignature(signature: String): ApiCredentialsEntity?

    @Insert
    suspend fun insert(entity: ApiCredentialsEntity): Long

    @Update
    suspend fun update(entity: ApiCredentialsEntity)

    @Upsert
    suspend fun upsert(entity: ApiCredentialsEntity): Long

    @Query("UPDATE api_credentials SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: ApiCredentialsId,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM api_credentials WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<ApiCredentialsId>)

    @Query("UPDATE api_credentials SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM api_credentials WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<ApiCredentialsEntity>

    @Query("UPDATE api_credentials SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    suspend fun markSynced(ids: List<ApiCredentialsId>)
}
