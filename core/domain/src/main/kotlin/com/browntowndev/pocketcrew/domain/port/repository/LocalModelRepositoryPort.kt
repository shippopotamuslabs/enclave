package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import kotlinx.coroutines.flow.Flow

interface LocalModelRepositoryPort {
    suspend fun getAllLocalAssets(): List<LocalModelAsset>
    fun observeAllLocalAssets(): Flow<List<LocalModelAsset>>
    
    suspend fun clearAll()

    suspend fun upsertLocalAsset(asset: LocalModelAsset): LocalModelId
    suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId

    suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): LocalModelId
    suspend fun deleteLocalModelMetadata(id: LocalModelId)
    
    suspend fun saveConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId
    suspend fun deleteConfiguration(id: LocalModelConfigurationId)
    suspend fun getConfigurationById(id: LocalModelConfigurationId): LocalModelConfiguration?
    suspend fun getAllConfigurationsForAsset(localModelId: LocalModelId): List<LocalModelConfiguration>
    suspend fun deleteAllConfigurationsForAsset(localModelId: LocalModelId)

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
    suspend fun getAssetById(id: LocalModelId): LocalModelAsset?

    /**
     * Gets a LocalModelAsset that contains the specified configuration ID.
     *
     * @param configId The LocalModelConfiguration database ID
     * @return The LocalModelAsset if found, null otherwise
     */
    suspend fun getAssetByConfigId(configId: LocalModelConfigurationId): LocalModelAsset?
    /**
     * Restores a soft-deleted model by re-creating its system preset configurations.
     * The model file still needs to be re-downloaded after this.
     *
     * @param id The LocalModelEntity database ID
     * @param configurations The system configurations to restore (from remote config match)
     * @return The restored LocalModelAsset
     */
    suspend fun restoreSoftDeletedModel(
        id: LocalModelId,
        configurations: List<LocalModelConfiguration>
    ): LocalModelAsset
}

