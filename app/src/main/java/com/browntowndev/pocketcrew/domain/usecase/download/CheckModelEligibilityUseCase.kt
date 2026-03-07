package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.ModelFile
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject

/**
 * Use case to determine which models need downloading based on:
 * 1. Registry Check - Compare new models to original models
 * 2. Config Change Detection - Detect changes in MD5 or modelFileFormat
 * 3. File Scan Integration - Combine with ModelScanResult (missing, partial, invalid files)
 *
 * This use case extracts the eligibility determination logic from ModelDownloadOrchestratorImpl,
 * providing a clean separation of concerns for the domain layer.
 */
class CheckModelEligibilityUseCase @Inject constructor(
    private val logger: LoggingPort
) {
    companion object {
        private const val TAG = "CheckModelEligibilityUseCase"
    }

    /**
     * Determines which models need downloading based on registry, config changes, and file scan.
     *
     * @param scanResult Result of scanning the filesystem for existing model files
     * @return List of ModelFile that need downloading (not already available)
     */
    fun check(
        originalModels: List<ModelFile>,
        newModels: List<ModelFile>,
        scanResult: ModelScanResult
    ): List<ModelFile> {
        // 1. Compare with registered models in database to detect ALL field changes
        val modelsToDownload = determineModelsNeedingDownload(originalModels, newModels)
        logger.debug(TAG, "Models to download: $modelsToDownload")

        // 2. Combine: need download if either config says so OR file is missing/invalid
        // Also include partial downloads (incomplete .tmp files from failed downloads)
        // Match by checking if model has a file that could correspond to a partial download
        // (the scanner already validated these partials match registry by MD5)
        val partialDownloadModels = scanResult.partialDownloads.keys.mapNotNull { filename ->
            newModels.find { filename in it.filenames }
        }
        logger.debug(TAG, "Partial downloads: $partialDownloadModels")


        // Check for invalid models from scan (format changes, MD5 mismatches)
        val invalidModels = scanResult.invalidModels
        logger.debug(TAG, "Invalid models: $invalidModels")

        // Add models that need re-download due to config changes
        val configChangedModels = modelsToDownload.filter { model ->
            scanResult.missingModels.none { missing -> missing.filenames.any { fn -> fn in model.filenames } } &&
                scanResult.partialDownloads.keys.none { it in model.filenames } &&
                scanResult.invalidModels.none { invalid -> invalid.filenames.any { fn -> fn in model.filenames } }
        }
        logger.debug(TAG, "Config changed models: $configChangedModels")

        // Combine missing, partial, and invalid models
        val missingModels = scanResult.missingModels + partialDownloadModels + invalidModels + configChangedModels

        // Combine models with the same MD5 (multi-type models like DRAFT + VISION share the same file)
        // by merging their modelTypes into a single entry
        val combinedMissingModels = missingModels
            .groupBy { it.md5 }
            .map { (_, models) ->
                require(models.distinctBy { it.originalFileName }.size == 1) { "Multiple filenames for same MD5" }
                val firstModel = models.first()
                val combinedModelTypes = models.flatMap { it.modelTypes }.distinct()
                firstModel.copy(modelTypes = combinedModelTypes)
            }

        return combinedMissingModels
    }

    /**
     * Determines which models need downloading based on registry comparison.
     * Checks for unregistered models and config changes (displayName, MD5, format).
     */
    private fun determineModelsNeedingDownload(
        originalModels: List<ModelFile>,
        newModels: List<ModelFile>,
    ): List<ModelFile> {
        if (originalModels.isEmpty()) return newModels

        val modelsToDownload = mutableListOf<ModelFile>()
        val originalModelsByMd5 = originalModels.associateBy { it.md5 }

        // Check for unregistered models
        for (model in originalModels) {
            // Check if model file was updated
            val registeredModel = originalModelsByMd5[model.md5]
            val modelFileUpdated = model.anyDifferentModelsThan(registeredModel) ||
                    !model.hasSameFormatAs(registeredModel)

            if (modelFileUpdated) {
                logger.debug(TAG, "[MODEL UPDATED] Model file updated: $model. Registered: $registeredModel")
                modelsToDownload.add(model)
            }
        }

        return modelsToDownload
    }
}
