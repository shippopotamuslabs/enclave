package com.browntowndev.pocketcrew.core.testing

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.SlotResolvedLocalModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake implementation of LocalModelRepositoryPort for testing.
 * Provides in-memory storage to test real behavioral scenarios.
 */
class FakeLocalModelRepository : LocalModelRepositoryPort {

    private val modelsMap = mutableMapOf<ModelType, Pair<LocalModelAsset, LocalModelConfiguration>>()
    private val _registeredModelsFlow = MutableStateFlow<Map<ModelType, String>>(emptyMap())

    // Soft-deleted models: LocalModelEntity rows with no configs
    // Key = model ID, Value = LocalModelAsset (metadata only, no configs)
    private val softDeletedModels = mutableMapOf<Long, LocalModelAsset>()

    private val assignments = mutableMapOf<ModelType, LocalModelAsset>()
    private val assets = mutableListOf<LocalModelAsset>()
    private val softDeleted = mutableListOf<LocalModelAsset>()
    private val configsById = mutableMapOf<Long, Pair<LocalModelAsset, LocalModelConfiguration>>()
    private var nextAssetId = 1L
    private var nextConfigId = 1L

    override suspend fun getAllLocalAssets(): List<LocalModelAsset> {
        return assets
    }

    override fun observeAllLocalAssets(): Flow<List<LocalModelAsset>> {
        return _registeredModelsFlow.map { assets }
    }

    override suspend fun getAssetByConfigId(configId: Long): LocalModelAsset? {
        return configsById[configId]?.first
    }

    override suspend fun upsertLocalAsset(asset: LocalModelAsset): Long {
        val existingIndex = assets.indexOfFirst { it.metadata.id == asset.metadata.id || it.metadata.sha256 == asset.metadata.sha256 }
        val assignedId = when {
            asset.metadata.id > 0 -> asset.metadata.id
            existingIndex >= 0 -> assets[existingIndex].metadata.id
            else -> nextAssetId++
        }
        val normalizedAsset = asset.copy(metadata = asset.metadata.copy(id = assignedId))
        if (existingIndex >= 0) {
            assets[existingIndex] = normalizedAsset
        } else {
            assets.add(normalizedAsset)
        }
        return assignedId
    }

    override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): Long {
        val configId = if (config.id > 0) config.id else nextConfigId++
        val asset = assets.find { it.metadata.id == config.localModelId }
            ?: throw IllegalStateException("Asset ${config.localModelId} not found")
        val normalizedConfig = config.copy(id = configId, localModelId = asset.metadata.id)
        configsById[configId] = asset to normalizedConfig
        return configId
    }



    override suspend fun clearAll() {
        assignments.clear()
        assets.clear()
        softDeleted.clear()
        configsById.clear()
    }

    override suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): Long {
        return metadata.id
    }

    override suspend fun deleteLocalModelMetadata(id: Long) {
    }

    override suspend fun saveConfiguration(config: LocalModelConfiguration): Long {
        return config.id
    }

    override suspend fun deleteConfiguration(id: Long) {
    }

    override suspend fun getConfigurationById(id: Long): LocalModelConfiguration? {
        return configsById[id]?.second
    }

    override suspend fun getAllConfigurationsForAsset(localModelId: Long): List<LocalModelConfiguration> {
        return configsById.values.filter { it.first.metadata.id == localModelId }.map { it.second }
    }

    override suspend fun deleteAllConfigurationsForAsset(localModelId: Long) {
        configsById.entries.removeIf { it.value.first.metadata.id == localModelId }
    }

    fun setAssets(newAssets: List<LocalModelAsset>) {
        assets.clear()
    }

    private fun updateFlow() {
        _registeredModelsFlow.value = modelsMap.mapValues { it.value.second.displayName }
    }

    fun reset() {
        modelsMap.clear()
        softDeletedModels.clear()
        assignments.clear()
        assets.clear()
        configsById.clear()
        _registeredModelsFlow.value = emptyMap()
    }

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> {
        return softDeletedModels.values.toList()
    }

    override suspend fun getAssetById(id: Long): LocalModelAsset? {
        // First check active models
        modelsMap.values.find { it.first.metadata.id == id }?.let { return it.first }
        // Then check soft-deleted models
        return softDeletedModels[id]
    }

    /**
     * Test helper: registers a model as soft-deleted (metadata preserved, no configs).
     * This simulates the state after a soft-delete operation.
     */
    fun registerSoftDeletedModel(modelId: Long, asset: LocalModelAsset) {
        softDeletedModels[modelId] = asset.copy(
            metadata = asset.metadata.copy(id = modelId)
        )
    }
}
