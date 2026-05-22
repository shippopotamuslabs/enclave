package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Fake implementation of LocalModelRepositoryPort for testing.
 * Provides in-memory storage to test real behavioral scenarios.
 */
class FakeLocalModelRepository : LocalModelRepositoryPort {
    private val modelsMap = mutableMapOf<ModelType, Pair<LocalModelAsset, LocalModelConfiguration>>()
    private val _registeredModelsFlow = MutableStateFlow<Map<ModelType, String>>(emptyMap())

    // Soft-deleted models: LocalModelEntity rows with no configs
    // Key = model ID, Value = LocalModelAsset (metadata only, no configs)
    private val softDeletedModels = mutableMapOf<LocalModelId, LocalModelAsset>()

    private val assignments = mutableMapOf<ModelType, LocalModelAsset>()
    private val assets = mutableListOf<LocalModelAsset>()
    private val softDeleted = mutableListOf<LocalModelAsset>()
    private val configsById = mutableMapOf<LocalModelConfigurationId, Pair<LocalModelAsset, LocalModelConfiguration>>()
    private var nextAssetId = 1

    override suspend fun getAllLocalAssets(): List<LocalModelAsset> = assets

    override fun observeAllLocalAssets(): Flow<List<LocalModelAsset>> = _registeredModelsFlow.map { assets }

    override suspend fun getAssetByConfigId(configId: LocalModelConfigurationId): LocalModelAsset? = configsById[configId]?.first

    override suspend fun upsertLocalAsset(asset: LocalModelAsset): LocalModelId {
        val existingIndex = assets.indexOfFirst { it.metadata.id == asset.metadata.id || it.metadata.sha256 == asset.metadata.sha256 }
        val assignedId =
            when {
                asset.metadata.id.value
                    .isNotEmpty() &&
                    asset.metadata.id.value != "0" -> asset.metadata.id
                existingIndex >= 0 -> assets[existingIndex].metadata.id
                else -> LocalModelId((nextAssetId++).toString())
            }
        val normalizedAsset = asset.copy(metadata = asset.metadata.copy(id = assignedId))
        if (existingIndex >= 0) {
            assets[existingIndex] = normalizedAsset
        } else {
            assets.add(normalizedAsset)
        }
        return assignedId
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId {
        val configId = if (config.id.value.isNotEmpty()) config.id else LocalModelConfigurationId(Uuid.random().toString())
        val asset =
            assets.find { it.metadata.id == config.localModelId }
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

    override suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): LocalModelId = metadata.id

    override suspend fun deleteLocalModelMetadata(id: LocalModelId) {
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId =
        if (config.id.value.isNotEmpty()) config.id else LocalModelConfigurationId(Uuid.random().toString())

    override suspend fun deleteConfiguration(id: LocalModelConfigurationId) {
        configsById.remove(id)
    }

    override suspend fun getConfigurationById(id: LocalModelConfigurationId): LocalModelConfiguration? = configsById[id]?.second

    override suspend fun getAllConfigurationsForAsset(localModelId: LocalModelId): List<LocalModelConfiguration> =
        configsById.values
            .filter {
                it.first.metadata.id == localModelId
            }.map { it.second }

    override suspend fun deleteAllConfigurationsForAsset(localModelId: LocalModelId) {
        configsById.entries.removeIf { it.value.first.metadata.id == localModelId }
    }

    fun setAssets(newAssets: List<LocalModelAsset>) {
        assets.clear()
        assets.addAll(newAssets)
    }

    @Suppress("UnusedPrivateMember")
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

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> = softDeletedModels.values.toList()

    override suspend fun getAssetById(id: LocalModelId): LocalModelAsset? {
        // First check active models
        modelsMap.values.find { it.first.metadata.id == id }?.let { return it.first }
        // Then check soft-deleted models
        return softDeletedModels[id]
    }

    /**
     * Test helper: registers a model as soft-deleted (metadata preserved, no configs).
     * This simulates the state after a soft-delete operation.
     */
    fun registerSoftDeletedModel(
        modelId: LocalModelId,
        asset: LocalModelAsset,
    ) {
        softDeletedModels[modelId] =
            asset.copy(
                metadata = asset.metadata.copy(id = modelId),
            )
    }

    override suspend fun restoreSoftDeletedModel(
        id: LocalModelId,
        configurations: List<LocalModelConfiguration>,
    ): LocalModelAsset {
        val model = softDeletedModels.remove(id) ?: throw IllegalStateException("Model $id not found in soft-deleted models")
        val restoredAsset = model.copy(configurations = configurations)
        assets.add(restoredAsset)
        configurations.forEach { configsById[it.id] = restoredAsset to it }
        return restoredAsset
    }
}
