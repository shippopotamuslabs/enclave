package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

interface DefaultModelRepositoryPort {
    suspend fun getDefault(modelType: ModelType): DefaultModelAssignment?
    fun observeDefaults(): Flow<List<DefaultModelAssignment>>
    suspend fun setDefault(modelType: ModelType, localConfigId: Long?, apiConfigId: Long?)
    suspend fun clearDefault(modelType: ModelType)
}