package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration

/**
 * Port interface for local model configuration repository operations.
 * Provides access to local model configurations for the domain layer.
 */
interface LocalModelConfigurationsRepositoryPort {
    /**
     * Returns all configurations for a given local model ID.
     */
    suspend fun getAllForAsset(localModelId: Long): List<LocalModelConfiguration>

    /**
     * Hard-deletes all configurations for a given local model ID.
     * This is used during soft-delete of a local model - all configs are removed
     * but the LocalModelEntity row is preserved.
     */
    suspend fun deleteAllForAsset(localModelId: Long)

    /**
     * Hard-deletes a single configuration by ID.
     */
    suspend fun deleteById(id: Long)

    /**
     * Gets a configuration by its ID.
     */
    suspend fun getById(id: Long): LocalModelConfiguration?

    /**
     * Saves a configuration.
     */
    suspend fun saveConfiguration(config: LocalModelConfiguration): Long
}
