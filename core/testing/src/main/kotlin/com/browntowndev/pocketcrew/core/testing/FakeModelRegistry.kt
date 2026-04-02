package com.browntowndev.pocketcrew.core.testing

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake implementation of ModelRegistryPort for testing.
 * Provides in-memory storage to test real behavioral scenarios.
 */
class FakeModelRegistry : ModelRegistryPort {

    private val modelsMap = mutableMapOf<ModelType, Pair<LocalModelAsset, LocalModelConfiguration>>()
    private val modelStatuses = mutableMapOf<String, ModelStatus>() // SHA256 -> Status
    private val _registeredModelsFlow = MutableStateFlow<Map<ModelType, String>>(emptyMap())

    // Soft-deleted models: LocalModelEntity rows with no configs
    // Key = model ID, Value = LocalModelAsset (metadata only, no configs)
    private val softDeletedModels = mutableMapOf<Long, LocalModelAsset>()

    override suspend fun getRegisteredAsset(modelType: ModelType): LocalModelAsset? {
        return modelsMap[modelType]?.first
    }

    override suspend fun getRegisteredConfiguration(modelType: ModelType): LocalModelConfiguration? {
        return modelsMap[modelType]?.second
    }

    override suspend fun getRegisteredAssets(): List<LocalModelAsset> {
        return modelsMap.values.map { it.first }.distinctBy { it.metadata.sha256 }
    }

    override suspend fun getRegisteredConfigurations(): List<LocalModelConfiguration> {
        return modelsMap.values.map { it.second }
    }

    override fun observeAsset(modelType: ModelType): Flow<LocalModelAsset?> {
        return _registeredModelsFlow.map { modelsMap[modelType]?.first }
    }

    override fun observeConfiguration(modelType: ModelType): Flow<LocalModelConfiguration?> {
        return _registeredModelsFlow.map { modelsMap[modelType]?.second }
    }

    override fun observeAssets(): Flow<List<LocalModelAsset>> {
        return _registeredModelsFlow.map { modelsMap.values.map { it.first }.distinctBy { it.metadata.id } }
    }

    override suspend fun setRegisteredModel(
        modelType: ModelType,
        asset: LocalModelAsset,
        status: ModelStatus,
        markExistingAsOld: Boolean
    ) {
        val currentEntry = modelsMap[modelType]
        if (status == ModelStatus.CURRENT && markExistingAsOld && currentEntry != null) {
            modelStatuses[currentEntry.first.metadata.sha256] = ModelStatus.OLD
        }

        // Use the first configuration as the one being registered for the slot.
        val config = asset.configurations.firstOrNull()
            ?: throw IllegalStateException("Asset must have at least one configuration")

        modelsMap[modelType] = asset to config
        modelStatuses[asset.metadata.sha256] = status
        updateFlow()
    }

    override suspend fun clearAll() {
        modelsMap.clear()
        modelStatuses.clear()
        softDeletedModels.clear()
        updateFlow()
    }

    override suspend fun clearOld() {
        // Simple implementation for fakes
    }

    override suspend fun getAssetsPreferringOld(): Map<ModelType, LocalModelAsset> {
        return modelsMap.mapValues { it.value.first }
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

    private fun updateFlow() {
        _registeredModelsFlow.value = modelsMap.mapValues { it.value.second.displayName }
    }

    fun reset() {
        modelsMap.clear()
        modelStatuses.clear()
        softDeletedModels.clear()
        _registeredModelsFlow.value = emptyMap()
    }

    fun getModelStatus(modelType: ModelType): ModelStatus? {
        val asset = modelsMap[modelType]?.first ?: return null
        return modelStatuses[asset.metadata.sha256]
    }

    fun getStatusBySha256(sha256: String): ModelStatus? {
        return modelStatuses[sha256]
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

    override suspend fun reuseModelForRedownload(modelId: Long, newAsset: LocalModelAsset): Long {
        // Reuse the existing model row if it was soft-deleted
        // The fake just returns the same ID
        val existingModel = softDeletedModels[modelId]
            ?: throw IllegalStateException("Model $modelId not found in soft-deleted models")

        // Remove from soft-deleted and register as active
        softDeletedModels.remove(modelId)

        // Register with the new asset (which has configs with isSystemPreset=true)
        val config = newAsset.configurations.firstOrNull()
            ?: throw IllegalStateException("Asset must have at least one configuration")

        // Find the modelType from the asset (using huggingFaceModelName)
        val modelType = ModelType.entries.find { newAsset.metadata.huggingFaceModelName.contains(it.name, ignoreCase = true) }
            ?: ModelType.FAST

        modelsMap[modelType] = newAsset to config
        modelStatuses[newAsset.metadata.sha256] = ModelStatus.CURRENT
        updateFlow()

        return modelId
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