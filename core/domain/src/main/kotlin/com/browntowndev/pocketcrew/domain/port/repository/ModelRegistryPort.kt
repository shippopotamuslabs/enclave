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
    fun getRegisteredAssetSync(modelType: ModelType): LocalModelAsset?
    fun getRegisteredConfigurationSync(modelType: ModelType): LocalModelConfiguration?
    suspend fun getRegisteredAssets(): List<LocalModelAsset>
    fun getRegisteredAssetsSync(): List<LocalModelAsset>
    suspend fun getRegisteredConfigurations(): List<LocalModelConfiguration>
    fun getRegisteredConfigurationsSync(): List<LocalModelConfiguration>
    fun observeAsset(modelType: ModelType): Flow<LocalModelAsset?>
    fun observeConfiguration(modelType: ModelType): Flow<LocalModelConfiguration?>
    fun observeAssets(): Flow<List<LocalModelAsset>>
    suspend fun setRegisteredModel(modelType: ModelType, asset: LocalModelAsset, status: ModelStatus = ModelStatus.CURRENT, markExistingAsOld: Boolean = true)
    suspend fun clearAll()
    suspend fun clearOld()
    suspend fun getAssetsPreferringOld(): Map<ModelType, LocalModelAsset>
    
    suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): Long
    suspend fun deleteLocalModelMetadata(id: Long)
    suspend fun saveConfiguration(config: LocalModelConfiguration): Long
    suspend fun deleteConfiguration(id: Long)
}