package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

/**
 * Port for managing default model assignments (which ModelType slot uses which source).
 */
interface DefaultModelRepositoryPort {
    suspend fun getDefault(modelType: ModelType): DefaultModelAssignment?
    fun observeDefaults(): Flow<List<DefaultModelAssignment>>
    suspend fun setDefault(modelType: ModelType, source: ModelSource, apiModelId: Long? = null)
    suspend fun clearDefault(modelType: ModelType)
    suspend fun resetDefaultsForApiModel(apiModelId: Long)
}
