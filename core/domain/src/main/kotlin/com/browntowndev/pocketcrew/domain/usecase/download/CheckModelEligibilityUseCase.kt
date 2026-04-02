package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject

/**
 * Use case to determine which models need downloading based on:
 * 1. Registry Check - Compare new models to original models
 * 2. Config Change Detection - Detect changes in SHA256 or modelFileFormat
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
     * @param originalModels The map of ORIGINAL model types to assets from registry (before remote config update)
     * @param newModels The map of NEW model types to assets from remote config
     * @param scanResult Result of scanning the filesystem for existing model files
     * @return List of LocalModelAsset that need downloading (not already available)
     */
    fun check(
        originalModels: Map<ModelType, LocalModelAsset>,
        newModels: Map<ModelType, LocalModelAsset>,
        scanResult: ModelScanResult
    ): List<LocalModelAsset> {
        // 1. Compare with registered models in database to detect ALL field changes
        val assetsToDownload = determineAssetsNeedingDownload(originalModels, newModels)
        logger.debug(TAG, "Assets to download from registry: $assetsToDownload")

        // 2. Combine: need download if either config says so OR file is missing/invalid
        // Also include partial downloads (incomplete .tmp files from failed downloads)
        val partialDownloadAssets = scanResult.partialDownloads.keys.mapNotNull { filename ->
            newModels.values.find { it.metadata.localFileName == filename }
        }
        logger.debug(TAG, "Partial downloads: $partialDownloadAssets")

        // Check for invalid models from scan (format changes, MD5 mismatches)
        val invalidAssets = scanResult.invalidModels
        logger.debug(TAG, "Invalid assets: $invalidAssets")

        // Add assets that need re-download due to config changes
        val configChangedAssets = assetsToDownload.filter { asset ->
            scanResult.missingModels.none { missing -> missing.metadata.localFileName == asset.metadata.localFileName } &&
                scanResult.partialDownloads.keys.none { it == asset.metadata.localFileName } &&
                scanResult.invalidModels.none { invalid -> invalid.metadata.localFileName == asset.metadata.localFileName }
        }
        logger.debug(TAG, "Config changed assets: $configChangedAssets")

        // Combine missing, partial, and invalid models
        val allMissingAssets = scanResult.missingModels + partialDownloadAssets + invalidAssets + configChangedAssets

        // Combine models with the same SHA256 (multi-type models like DRAFT_ONE + FAST share the same file)
        // by merging their modelTypes into a single entry
        val combinedMissingAssets = allMissingAssets
            .groupBy { it.metadata.sha256 }
            .map { (_, assets) ->
                val firstAsset = assets.first()
                firstAsset
            }

        return combinedMissingAssets
    }

    /**
     * Determines which assets need downloading based on registry comparison.
     * Checks for unregistered assets and config changes (huggingFaceModelName, SHA256, format).
     */
    private fun determineAssetsNeedingDownload(
        originalModels: Map<ModelType, LocalModelAsset>,
        newModels: Map<ModelType, LocalModelAsset>,
    ): List<LocalModelAsset> {
        if (originalModels.isEmpty()) return newModels.values.toList()

        val assetsToDownload = mutableListOf<LocalModelAsset>()
        val originalAssetsBySha256 = originalModels.values.associateBy { it.metadata.sha256 }

        // Check for unregistered models or config changes
        for (asset in newModels.values) {
            val registeredAsset = originalAssetsBySha256[asset.metadata.sha256]
            val assetFileUpdated = registeredAsset == null ||
                registeredAsset.metadata.modelFileFormat != asset.metadata.modelFileFormat

            if (assetFileUpdated) {
                logger.debug(TAG, "[ASSET UPDATED] Asset updated: ${asset.metadata.huggingFaceModelName}. Registered: $registeredAsset")
                assetsToDownload.add(asset)
            }
        }

        return assetsToDownload
    }
}
