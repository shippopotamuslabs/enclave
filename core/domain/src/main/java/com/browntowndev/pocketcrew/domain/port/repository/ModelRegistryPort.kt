package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

/**
 * Port (interface) for model registry operations.
 * Tracks which model is currently installed for each model slot.
 */
interface ModelRegistryPort {
    /**
     * Get registered model for a given type (suspend version).
     * Returns null if no model is registered for that type.
     */
    suspend fun getRegisteredModel(modelType: ModelType): ModelConfiguration?

    /**
     * Get registered model for a given type (non-suspend version).
     * Uses internal cache - suitable for use in DI provider methods.
     * Returns null if no model is registered for that type.
     */
    fun getRegisteredModelSync(modelType: ModelType): ModelConfiguration?

    /**
     * Get all registered models (suspend version).
     */
    suspend fun getRegisteredModels(): List<ModelConfiguration>

    /**
     * Get all registered models (non-suspend version).
     * Uses internal cache - suitable for use in DI provider methods.
     */
    fun getRegisteredModelsSync(): List<ModelConfiguration>

    /**
     * Get all registered models as a Flow for reactive updates.
     */
    fun observeRegisteredModels(): Flow<Map<ModelType, String>>

    /**
     * Observe a single model's configuration as a Flow for reactive updates.
     */
    fun observeModel(modelType: ModelType): Flow<ModelConfiguration?>

    /**
     * Update (or insert) the model for a given type with full config.
     * Called after a successful download.
     *
     * @param config The model configuration to save
     * @param status The status (CURRENT or OLD)
     * @param markExistingAsOld If true and there's an existing CURRENT, it will be marked as OLD.
     *                          If false, existing CURRENT will be updated in place (no OLD entry created).
     */
    suspend fun setRegisteredModel(
        config: ModelConfiguration,
        status: ModelStatus = ModelStatus.CURRENT,
        markExistingAsOld: Boolean = true
    )

    /**
     * Clear all registered models.
     * Useful for factory reset or clean reinstall.
     */
    suspend fun clearAll()

    /**
     * Clear all OLD entries from the database.
     * Called after successful download completion.
     */
    suspend fun clearOld()

    /**
     * Get models for all ModelTypes, preferring OLD if it exists, otherwise CURRENT.
     * Used during initialization to handle failed downloads - if a download failed,
     * the OLD entry should be used so the download is retried on restart.
     */
    suspend fun getModelsPreferringOld(): List<ModelConfiguration>
}
