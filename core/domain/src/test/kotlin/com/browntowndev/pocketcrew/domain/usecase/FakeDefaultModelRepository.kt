package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeDefaultModelRepository : DefaultModelRepositoryPort {

    private val _defaults = MutableStateFlow<Map<ModelType, DefaultModelAssignment>>(emptyMap())

    var lastSetCall: Triple<ModelType, Long?, Long?>? = null

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? {
        return _defaults.value[modelType]
    }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> {
        return _defaults.asStateFlow().map { it.values.toList() }
    }

    override suspend fun setDefault(modelType: ModelType, localConfigId: Long?, apiConfigId: Long?) {
        lastSetCall = Triple(modelType, localConfigId, apiConfigId)
        val current = _defaults.value.toMutableMap()
        current[modelType] = DefaultModelAssignment(
            modelType = modelType,
            localConfigId = localConfigId,
            apiConfigId = apiConfigId
        )
        _defaults.value = current
    }

    override suspend fun clearDefault(modelType: ModelType) {
        val current = _defaults.value.toMutableMap()
        current.remove(modelType)
        _defaults.value = current
    }

    fun seed(defaults: List<DefaultModelAssignment>) {
        _defaults.value = defaults.associateBy { it.modelType }
    }
}
