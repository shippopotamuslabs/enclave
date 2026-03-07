package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType
import kotlinx.coroutines.flow.Flow

/**
 * Data class representing a fully registered model with all configuration.
 */
data class RegisteredModel(
    val remoteFilename: String,
    val modelType: ModelType,
    val displayName: String,
    val modelFileFormat: ModelFileFormat,
    val md5: String,
    val sizeInBytes: Long,
    val temperature: Double = 0.0,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val maxTokens: Int,
    val systemPrompt: String? = null
)

/**
 * Port (interface) for model registry operations.
 * Tracks which model is currently installed for each model slot.
 */
interface ModelRegistryPort {
    /**
     * Get registered model for a given type.
     * Returns null if no model is registered for that type.
     */
    suspend fun getRegisteredModel(modelType: ModelType): RegisteredModel?

    suspend fun getRegisteredModels(): List<RegisteredModel>

    /**
     * Get all registered models as a Flow for reactive updates.
     */
    fun observeRegisteredModels(): Flow<Map<ModelType, String>>

    /**
     * Update (or insert) the model for a given type with full config.
     * Called after a successful download.
     */
    suspend fun setRegisteredModel(
        remoteFilename: String,
        modelType: ModelType,
        displayName: String,
        modelFileFormat: ModelFileFormat,
        md5: String,
        sizeInBytes: Long,
        temperature: Double,
        topK: Int,
        topP: Double,
        maxTokens: Int,
        systemPrompt: String?
    )

    /**
     * Clear all registered models.
     * Useful for factory reset or clean reinstall.
     */
    suspend fun clearAll()
}
