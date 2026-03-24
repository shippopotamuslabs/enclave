package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.exception.ModelsDirectoryException
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
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
     * @param downloadedModels The list of models that are actually downloaded (from registry)
     * @param expectedModels The list of models expected from remote config (from cache)
     * @return DownloadModelsResult containing models that need downloading and scan result
     */
    suspend operator fun invoke(
        downloadedModels: List<ModelConfiguration>,
        expectedModels: List<ModelConfiguration>
    ): DownloadModelsResult {
        // Scan filesystem comparing what's downloaded vs what's expected
        val scan = fileScanner.scanAndCreateDirIfNotExist(
            downloadedModels = downloadedModels,
            expectedModels = expectedModels
        )

        // Handle directory creation error
        if (scan.directoryError) {
            throw ModelsDirectoryException("Failed to create models directory")
        }

        // Use CheckModelEligibilityUseCase to determine which models need downloading
        val missingModels = checkModelEligibilityUseCase.check(downloadedModels, expectedModels, scanResult = scan)

        // Log results
        if (missingModels.isEmpty()) {
            logger.info(TAG, "All ${expectedModels.size} models are ready")
        } else {
            logger.info(TAG, "${missingModels.size} models need download: ${missingModels.map { it.metadata.displayName }}")
        }

        // Return both the scan result and models that need downloading
        return DownloadModelsResult(modelsToDownload = missingModels, scanResult = scan)
    }
}
