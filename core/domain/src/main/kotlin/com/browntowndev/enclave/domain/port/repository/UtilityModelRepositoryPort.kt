package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import kotlinx.coroutines.flow.Flow

interface UtilityModelRepositoryPort {
    suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelMetadata?

    suspend fun getAllActive(): List<UtilityModelMetadata>

    fun observeAllActive(): Flow<List<UtilityModelMetadata>>

    suspend fun getById(id: UtilityModelId): UtilityModelMetadata?

    suspend fun getBySha256(sha256: String): UtilityModelMetadata?

    suspend fun upsert(metadata: UtilityModelMetadata): UtilityModelId

    suspend fun softDelete(id: UtilityModelId)
}
