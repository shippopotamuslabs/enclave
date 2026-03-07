package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelType
import kotlinx.coroutines.flow.Flow

/**
 * Port (interface) for model registry operations.
 * Tracks which model is currently installed for each model slot.
 */
interface ModelRegistryPort {
    /**
     * Get registered model for a given type.
     * Returns null if no model is registered for that type.
     */
    suspend fun getRegisteredModel(modelType: ModelType): ModelConfiguration?

    suspend fun getRegisteredModels(): List<ModelConfiguration>

    /**
     * Get all registered models as a Flow for reactive updates.
     */
    fun observeRegisteredModels(): Flow<Map<ModelType, String>>

    /**
     * Update (or insert) the model for a given type with full config.
     * Called after a successful download.
     */
    suspend fun setRegisteredModel(config: ModelConfiguration)

    /**
     * Clear all registered models.
     * Useful for factory reset or clean reinstall.
     */
    suspend fun clearAll()
}
