package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiCredentialsDao {
    @Query("SELECT * FROM api_credentials ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ApiCredentialsEntity>>

    @Query("SELECT * FROM api_credentials ORDER BY updated_at DESC")
    suspend fun getAll(): List<ApiCredentialsEntity>

    @Query("SELECT * FROM api_credentials WHERE id = :id")
    suspend fun getById(id: ApiCredentialsId): ApiCredentialsEntity?

    @Query("SELECT * FROM api_credentials WHERE credential_alias = :credentialAlias LIMIT 1")
    suspend fun getByCredentialAlias(credentialAlias: String): ApiCredentialsEntity?

    @Query("SELECT * FROM api_credentials WHERE api_key_signature = :signature LIMIT 1")
    suspend fun getByApiKeySignature(signature: String): ApiCredentialsEntity?

    @Insert
    suspend fun insert(entity: ApiCredentialsEntity): Long

    @Update
    suspend fun update(entity: ApiCredentialsEntity)

    @Upsert
    suspend fun upsert(entity: ApiCredentialsEntity): Long

    @Query("DELETE FROM api_credentials WHERE id = :id")
    suspend fun deleteById(id: ApiCredentialsId)
}
