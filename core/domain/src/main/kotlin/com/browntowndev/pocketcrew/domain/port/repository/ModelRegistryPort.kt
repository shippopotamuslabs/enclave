package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.SlotResolvedLocalModel
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
    suspend fun clearAll()

    suspend fun upsertLocalAsset(asset: LocalModelAsset): Long

    suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): Long

    suspend fun setDefaultLocalConfig(modelType: ModelType, configId: Long)

    suspend fun activateLocalModel(modelType: ModelType, asset: LocalModelAsset): SlotResolvedLocalModel

    suspend fun getRegisteredSelection(modelType: ModelType): SlotResolvedLocalModel?

    suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): Long
    suspend fun deleteLocalModelMetadata(id: Long)
    suspend fun saveConfiguration(config: LocalModelConfiguration): Long
    suspend fun deleteConfiguration(id: Long)

    /**
     * Deletes a model: hard-deletes configs, preserves LocalModelEntity for re-download.
     */
    suspend fun deleteModel(modelId: Long, replacementLocalConfigId: Long?, replacementApiConfigId: Long?): Result<Unit>

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
}
