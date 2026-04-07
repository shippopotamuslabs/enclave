package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject

/**
 * Use case to determine which models need downloading based purely on the filesystem scan.
 * 
 * If a file is missing, invalid (hash mismatch), or partially downloaded, it needs to be downloaded.
 * This removes the fragile database diffing logic that previously caused bugs with BYOK models.
 */
class CheckModelEligibilityUseCase @Inject constructor(
    private val logger: LoggingPort
) {
    companion object {
        private const val TAG = "CheckModelEligibilityUseCase"
    }

    /**
     * Determines which models need downloading based purely on the file scan.
     *
     * @param newModels The map of NEW model types to assets from remote config
     * @param scanResult Result of scanning the filesystem for existing model files
     * @return List of LocalModelAsset that need downloading (not already available)
     */
    fun check(
        newModels: Map<ModelType, LocalModelAsset>,
        scanResult: ModelScanResult
    ): List<LocalModelAsset> {
        // Also include partial downloads (incomplete .tmp files from failed downloads)
        val partialDownloadAssets = scanResult.partialDownloads.keys.mapNotNull { filename ->
            newModels.values.find { it.metadata.localFileName == filename }
        }
        logger.debug(TAG, "Partial downloads: $partialDownloadAssets")

        // Check for invalid models from scan (MD5 mismatches)
        val invalidAssets = scanResult.invalidModels
        logger.debug(TAG, "Invalid assets: $invalidAssets")

        // Missing models from scan
        val missingAssets = scanResult.missingModels
        logger.debug(TAG, "Missing assets: $missingAssets")

        // Combine missing, partial, and invalid models
        val allMissingAssets = missingAssets + partialDownloadAssets + invalidAssets

        // Preserve slot-specific assets even when they share the same file. The worker will
        // deduplicate the physical download by SHA256, but activation must keep every role.
        return allMissingAssets.distinct()
    }
}
