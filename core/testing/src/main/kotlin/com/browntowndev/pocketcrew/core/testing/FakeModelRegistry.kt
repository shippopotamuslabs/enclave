package com.browntowndev.pocketcrew.core.testing

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
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

    private val modelsMap = mutableMapOf<ModelType, ModelConfiguration>()
    private val modelStatuses = mutableMapOf<ModelType, ModelStatus>()
    private val _modelsFlow = MutableStateFlow<Map<ModelType, String>>(emptyMap())

    override suspend fun getRegisteredModel(modelType: ModelType): ModelConfiguration? {
        return modelsMap[modelType]
    }

    override fun getRegisteredModelSync(modelType: ModelType): ModelConfiguration? {
        return modelsMap[modelType]
    }

    override suspend fun getRegisteredModels(): List<ModelConfiguration> {
        return modelsMap.values.toList()
    }

    override fun getRegisteredModelsSync(): List<ModelConfiguration> {
        return modelsMap.values.toList()
    }

    override fun observeRegisteredModels(): Flow<Map<ModelType, String>> {
        return _modelsFlow
    }

    override fun observeModel(modelType: ModelType): Flow<ModelConfiguration?> {
        return _modelsFlow.map { it[modelType]?.let { name -> modelsMap[modelType] } }
    }

    override suspend fun setRegisteredModel(
        config: ModelConfiguration,
        status: ModelStatus,
        markExistingAsOld: Boolean
    ) {
        if (markExistingAsOld && modelsMap.containsKey(config.modelType)) {
            modelStatuses[config.modelType] = ModelStatus.OLD
        }
        modelsMap[config.modelType] = config
        modelStatuses[config.modelType] = status
        updateFlow()
    }

    override suspend fun clearAll() {
        modelsMap.clear()
        modelStatuses.clear()
        updateFlow()
    }

    override suspend fun clearOld() {
        val toRemove = modelStatuses.entries
            .filter { it.value == ModelStatus.OLD }
            .map { it.key }
        toRemove.forEach { modelType ->
            modelsMap.remove(modelType)
            modelStatuses.remove(modelType)
        }
        updateFlow()
    }

    override suspend fun getModelsPreferringOld(): List<ModelConfiguration> {
        return modelsMap.values.filter { config ->
            val status = modelStatuses[config.modelType]
            status == ModelStatus.OLD || status == null
        }
    }

    private fun updateFlow() {
        _modelsFlow.value = modelsMap.mapValues { it.value.metadata.displayName }
    }

    fun reset() {
        modelsMap.clear()
        modelStatuses.clear()
        _modelsFlow.value = emptyMap()
    }

    fun getModelStatus(modelType: ModelType): ModelStatus? {
        return modelStatuses[modelType]
    }
}
