package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDefaultModelRepository : DefaultModelRepositoryPort {
    private val _defaults = MutableStateFlow<List<DefaultModelAssignment>>(emptyList())
    var lastSetCall: Triple<ModelType, ModelSource, Long?>? = null

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? =
        _defaults.value.find { it.modelType == modelType }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> = _defaults

    override suspend fun setDefault(modelType: ModelType, source: ModelSource, apiModelId: Long?) {
        lastSetCall = Triple(modelType, source, apiModelId)
        val assignment = DefaultModelAssignment(modelType, source)
        _defaults.value = _defaults.value.filter { it.modelType != modelType } + assignment
    }

    override suspend fun clearDefault(modelType: ModelType) {
        _defaults.value = _defaults.value.filter { it.modelType != modelType }
    }

    fun seed(assignments: List<DefaultModelAssignment>) {
        _defaults.value = assignments
    }
}
