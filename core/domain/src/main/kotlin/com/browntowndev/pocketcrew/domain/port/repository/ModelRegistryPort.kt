package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

interface ModelRegistryPort {
    suspend fun getRegisteredAsset(modelType: ModelType): LocalModelAsset?
    suspend fun getRegisteredConfiguration(modelType: ModelType): LocalModelConfiguration?
    suspend fun getRegisteredAssets(): List<LocalModelAsset>
    suspend fun getRegisteredConfigurations(): List<LocalModelConfiguration>
    fun observeAsset(modelType: ModelType): Flow<LocalModelAsset?>
    fun observeConfiguration(modelType: ModelType): Flow<LocalModelConfiguration?>
    fun observeAssets(): Flow<List<LocalModelAsset>>
    suspend fun setRegisteredModel(modelType: ModelType, asset: LocalModelAsset, status: ModelStatus = ModelStatus.CURRENT, markExistingAsOld: Boolean = true)
    suspend fun clearAll()
    suspend fun clearOld()

    suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): Long
    suspend fun deleteLocalModelMetadata(id: Long)
    suspend fun saveConfiguration(config: LocalModelConfiguration): Long
    suspend fun deleteConfiguration(id: Long)

    /**
     * Returns models that were previously downloaded but have been soft-deleted.
     * A soft-deleted model has a LocalModelEntity row but has zero configurations.
     * These models are available for re-download.
     */
    suspend fun getSoftDeletedModels(): List<LocalModelAsset>

    /**
     * Reuses an existing soft-deleted LocalModelEntity row for re-download.
     * Updates the metadata with the new asset information and creates a new
     * configuration with isSystemPreset=true.
     *
     * @param modelId The existing LocalModelEntity ID to reuse
     * @param newAsset The new asset data from the remote config
     * @return The LocalModelEntity ID (same as input modelId)
     */
    suspend fun reuseModelForRedownload(modelId: Long, newAsset: LocalModelAsset): Long

    /**
     * Gets a LocalModelAsset by its database ID.
     * Used by ModelFileScanner to locate the file to delete during soft-delete.
     *
     * @param id The LocalModelEntity database ID
     * @return The LocalModelAsset if found, null otherwise
     */
    suspend fun getAssetById(id: Long): LocalModelAsset?
}