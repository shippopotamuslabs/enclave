package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

/**
 * Use case for soft-deleting a local model.
 *
 * Soft-delete strategy:
 * - Preserve LocalModelEntity (metadata for re-download)
 * - Hard-delete all LocalModelConfigurationEntity rows for this model
 * - If model has a config that is a default, require reassignment BEFORE deletion
 * - User can reassign to a config from a DIFFERENT local model OR an API model config
 */
class DeleteLocalModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val modelFileScanner: ModelFileScannerPort,
    private val apiModelRepository: ApiModelRepositoryPort,
    private val loggingPort: LoggingPort
) {
    companion object {
        private const val TAG = "DeleteLocalModelUseCase"
    }
    /**
     * Deletes a local model with optional reassignment of its default slot.
     *
     * @param modelId The LocalModelEntity ID to soft-delete
     * @param replacementLocalConfigId Replacement local config ID (from a different model), or null
     * @param replacementApiConfigId Replacement API config ID, or null (mutually exclusive with localConfigId)
     * @return Result.success(Unit) on success, Result.failure if it's the last model or error occurs
     */
    suspend operator fun invoke(
        modelId: LocalModelId,
        replacementLocalConfigId: LocalModelConfigurationId? = null,
        replacementApiConfigId: ApiModelConfigurationId? = null
    ): Result<Unit> {
        return Result.runCatching {
            // Step 1: Block deletion if this is the last model
            if (isLastModel(modelId)) {
                loggingPort.warning(TAG, "Attempted to delete the last model (id=$modelId). Aborting.")
                throw IllegalStateException("Cannot delete the last model. At least one local or API model must remain.")
            }

            loggingPort.debug(TAG, "Initiating soft-delete for model id: $modelId")

            // Step 2: Check if reassignment is needed
            val needingReassignment = getModelTypesNeedingReassignment(modelId)

            if (needingReassignment.isNotEmpty()) {
                // Reassignment required but no replacement given - this is a precondition failure
                // The UI should have called getModelTypesNeedingReassignment first and shown dialog
                require(replacementLocalConfigId != null || replacementApiConfigId != null) {
                    "Reassignment is required but no replacement was provided"
                }

                // Step 3: Perform reassignment for ALL slots using this model
                needingReassignment.forEach { modelType ->
                    defaultModelRepository.setDefault(
                        modelType = modelType,
                        localConfigId = replacementLocalConfigId,
                        apiConfigId = replacementApiConfigId
                    )
                    loggingPort.info(TAG, "Reassigned default slot for model type $modelType to config (local: ${replacementLocalConfigId?.value}, api: ${replacementApiConfigId?.value})")
                }
            }

            // Step 4: Soft-delete the model
            // Delete physical file
            modelFileScanner.deleteModelFile(modelId)

            // Hard delete ALL configs for this model
            localModelRepository.deleteAllConfigurationsForAsset(modelId)

            // LocalModelEntity is PRESERVED (soft-delete - metadata for re-download)
            // NOT calling localModelRepository.deleteLocalModelMetadata() which would hard-delete
            loggingPort.info(TAG, "Soft-delete complete for model id: $modelId. File deleted, configs cleared, metadata preserved.")
        }.onFailure {
            loggingPort.error(TAG, "Failed to delete local model (id=$modelId): ${it.message}", it)
        }
    }

    /**
     * Checks if a model has any config that is currently a default.
     * Returns the ModelType(s) that would need reassignment before deletion.
     *
     * A model needs reassignment if any of its configs are pointed to by a DefaultModelEntity.
     */
    suspend fun getModelTypesNeedingReassignment(modelId: LocalModelId): List<ModelType> {
        // Get all configs for this model
        val modelConfigs = localModelRepository.getAllConfigurationsForAsset(modelId)
        val modelConfigIds = modelConfigs.map { it.id }.toSet()

        if (modelConfigIds.isEmpty()) return emptyList()

        // For each default, check if it points to a config on this model
        // This requires synchronous collection of the flow
        return defaultModelRepository.observeDefaults()
            .first()
            .filter { default -> default.localConfigId != null && default.localConfigId in modelConfigIds }
            .map { it.modelType }
    }

    /**
     * Checks if the given model ID is the only remaining model (local or API).
     * If so, deletion should be blocked.
     *
     * Returns true when:
     * - There is exactly one local model registered AND it is the given modelId
     * - AND there are zero API models configured
     */
    suspend fun isLastModel(modelId: LocalModelId): Boolean {
        val registeredModels = localModelRepository.getAllLocalAssets()

        val hasApiModels = apiModelRepository.getAllCredentials().isNotEmpty()
        val hasOnlyOneLocalModel = registeredModels.size == 1
        val thisIsTheOnlyLocalModel = registeredModels.any { it.metadata.id == modelId }

        return hasOnlyOneLocalModel && thisIsTheOnlyLocalModel && !hasApiModels
    }
}
