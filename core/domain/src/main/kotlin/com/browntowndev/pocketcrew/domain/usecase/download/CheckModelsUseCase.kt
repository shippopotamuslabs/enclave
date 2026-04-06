package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.exception.ModelsDirectoryException
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject

/**
 * Use case that performs pure filesystem scanning and eligibility checking.
 * Extracts the check logic from ModelDownloadOrchestratorImpl to provide a
 * clean separation of concerns for the domain layer.
 *
 * This use case:
 * - Scans the filesystem for model files
 * - Determines which models need downloading based on eligibility
 * - Returns the result without modifying any state
 * - Logs all results for debugging/auditing
 */
class CheckModelsUseCase @Inject constructor(
    private val fileScanner: ModelFileScannerPort,
    private val checkModelEligibilityUseCase: CheckModelEligibilityUseCase,
    private val logger: LoggingPort
) {
    companion object {
        private const val TAG = "CheckModelsUseCase"
    }

    /**
     * Performs filesystem scan and eligibility check for the given models.
     *
     * @param expectedModels The map of model types to assets expected from remote config (from cache)
     * @return DownloadModelsResult containing models that need downloading and scan result
     */
    suspend operator fun invoke(
        expectedModels: Map<ModelType, LocalModelAsset>
    ): DownloadModelsResult {
        // Scan filesystem comparing what's expected vs what's physically on disk
        val scan = fileScanner.scanAndCreateDirIfNotExist(
            expectedModels = expectedModels
        )

        // Handle directory creation error
        if (scan.directoryError) {
            throw ModelsDirectoryException("Failed to create models directory")
        }

        // Use CheckModelEligibilityUseCase to determine which models need downloading
        val missingAssets = checkModelEligibilityUseCase.check(expectedModels, scanResult = scan)

        // Log results
        if (missingAssets.isEmpty()) {
            logger.info(TAG, "All ${expectedModels.size} models are ready")
        } else {
            logger.info(TAG, "${missingAssets.size} assets need download: ${missingAssets.map { it.metadata.huggingFaceModelName }}")
        }

        // Return both the scan result and models that need downloading
        return DownloadModelsResult(
            allModels = expectedModels,
            modelsToDownload = missingAssets,
            scanResult = scan
        )
    }
}
