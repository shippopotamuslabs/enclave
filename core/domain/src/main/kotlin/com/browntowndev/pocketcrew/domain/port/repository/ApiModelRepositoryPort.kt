package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import kotlinx.coroutines.flow.Flow

/**
 * Port for managing API model configurations.
 * Implementation in :data handles Room persistence + ApiKeyManager for secrets.
 */
interface ApiModelRepositoryPort {
    fun observeAll(): Flow<List<ApiModelConfig>>
    suspend fun getAll(): List<ApiModelConfig>
    suspend fun getById(id: Long): ApiModelConfig?
    suspend fun save(config: ApiModelConfig, apiKey: String): Long
    suspend fun delete(id: Long)
}
