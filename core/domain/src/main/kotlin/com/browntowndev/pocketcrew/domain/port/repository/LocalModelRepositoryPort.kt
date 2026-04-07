package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import kotlinx.coroutines.flow.Flow

interface LocalModelRepositoryPort {
    suspend fun getAllLocalAssets(): List<LocalModelAsset>
    fun observeAllLocalAssets(): Flow<List<LocalModelAsset>>
    
    suspend fun clearAll()

    suspend fun upsertLocalAsset(asset: LocalModelAsset): Long
    suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): Long

    suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): Long
    suspend fun deleteLocalModelMetadata(id: Long)
    
    suspend fun saveConfiguration(config: LocalModelConfiguration): Long
    suspend fun deleteConfiguration(id: Long)
    suspend fun getConfigurationById(id: Long): LocalModelConfiguration?
    suspend fun getAllConfigurationsForAsset(localModelId: Long): List<LocalModelConfiguration>
    suspend fun deleteAllConfigurationsForAsset(localModelId: Long)

    /**
     * Returns models that were previously downloaded but have been soft-deleted.
     * A soft-deleted model has a LocalModelEntity row but has zero configurations.
     * These models are available for re-download.
     */
    suspend fun getSoftDeletedModels(): List<LocalModelAsset>

    /**
     * Gets a LocalModelAsset by its database ID.
     * Used by ModelFileScanner to locate the file to delete during soft-delete.
     *
     * @param id The LocalModelEntity database ID
     * @return The LocalModelAsset if found, null otherwise
     */
    suspend fun getAssetById(id: Long): LocalModelAsset?

    /**
     * Gets a LocalModelAsset that contains the specified configuration ID.
     *
     * @param configId The LocalModelConfiguration database ID
     * @return The LocalModelAsset if found, null otherwise
     */
    suspend fun getAssetByConfigId(configId: Long): LocalModelAsset?
}
