package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.data.download.ModelFileScanner
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
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
    private val fileScanner: ModelFileScanner,
    private val checkModelEligibilityUseCase: CheckModelEligibilityUseCase,
    private val modelUrlProvider: ModelUrlProviderPort,
    private val logger: LoggingPort
) {
    companion object {
        private const val TAG = "CheckModelsUseCase"
    }

    /**
     * Performs filesystem scan and eligibility check for the given models.
     *
     * @param originalModels The list of ORIGINAL models from registry (before remote config update)
     * @param newModels The list of NEW models from remote config
     * @return DownloadModelsResult containing models that need downloading and scan result
     */
    suspend operator fun invoke(
        originalModels: List<ModelConfiguration>,
        newModels: List<ModelConfiguration>
    ): DownloadModelsResult {
        // Scan filesystem for ALL models using ORIGINAL models from registry
        // This ensures we detect partial downloads and compare against the OLD registry state
        val scan = fileScanner.scanAndCreateDirIfNotExist(
            modelsToCheck = originalModels,
            newModels = newModels
        )

        // Handle directory creation error
        if (scan.directoryError) {
            logger.warning(TAG, "Failed to create models directory")
            return DownloadModelsResult(modelsToDownload = emptyList(), scanResult = scan)
        }

        // Use CheckModelEligibilityUseCase to determine which models need downloading
        val missingModels = checkModelEligibilityUseCase.check(originalModels, newModels, scanResult = scan)

        // Log results
        if (missingModels.isEmpty()) {
            logger.info(TAG, "All ${originalModels.size} models are ready")
        } else {
            logger.info(TAG, "${missingModels.size} models need download: ${missingModels.map { it.metadata.displayName }}")
        }

        // Return both the scan result and models that need downloading
        return DownloadModelsResult(modelsToDownload = missingModels, scanResult = scan)
    }
}
