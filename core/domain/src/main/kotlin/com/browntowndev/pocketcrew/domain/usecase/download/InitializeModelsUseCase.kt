package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for initializing models at app startup.
 * Fetches remote config, registers expected models in registry, and checks which models need to be downloaded.
 */
class InitializeModelsUseCase @Inject constructor(
    private val modelConfigFetcher: ModelConfigFetcherPort,
    private val modelRegistry: ModelRegistryPort,
    private val modelDownloadOrchestrator: ModelDownloadOrchestratorPort,
    private val checkModelsUseCase: CheckModelsUseCase,
    private val logPort: LoggingPort
) {
    companion object {
        private const val TAG = "InitializeModelsUseCase"
    }

    /**
     * Executes the model initialization logic.
     * @return DownloadModelsResult containing which models need to be downloaded
     */
    suspend operator fun invoke(): DownloadModelsResult = withContext(Dispatchers.IO) {
        checkModelsResult()
    }

    /**
     * Returns the full DownloadModelsResult including scan result and models to download.
     * This allows passing scan results downstream to avoid duplicate scanning.
     */
    private suspend fun checkModelsResult(): DownloadModelsResult {
        // Get currently active models from registry
        val currentModels = com.browntowndev.pocketcrew.domain.model.inference.ModelType.entries
            .associateWith { modelRegistry.getRegisteredAsset(it) }
            .filterValues { it != null }
            .mapValues { it.value!! }

        logPort.debug(TAG, "Current active models: ${
            currentModels.map { (type, asset) -> 
                "$type: ${asset.metadata.huggingFaceModelName}"
            }
        }")

        // Fetch remote config
        val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
        val remoteConfigs = remoteConfigResult.getOrElse {
            logPort.error(TAG, "Failed to fetch remote config: ${it.message}")

            // If there are no current models, throw the error
            if (currentModels.isEmpty()) throw it

            // If config fetch error occurs, fallback to using current models
            currentModels
        }

        logPort.debug(TAG, "Fetched ${remoteConfigs.size} remote configs")

        // Source of truth for soft-deleted models is getSoftDeletedModels()
        val softDeletedAssets = modelRegistry.getSoftDeletedModels()
        
        if (softDeletedAssets.isNotEmpty()) {
            logPort.debug(TAG, "Found ${softDeletedAssets.size} soft-deleted models available for re-download: ${
                softDeletedAssets.map { it.metadata.huggingFaceModelName }
            }")
        }

        // Exclude soft-deleted model types from remote configs so CheckModelsUseCase
        // doesn't queue them for download. We identify soft-deleted model types
        // by looking for remote configs that match a soft-deleted asset SHA.
        val softDeletedShaSet = softDeletedAssets.mapTo(mutableSetOf()) { it.metadata.sha256 }
        val filteredRemoteConfigs = remoteConfigs.filter { (_, remoteAsset) ->
            remoteAsset.metadata.sha256 !in softDeletedShaSet
        }

        // Check if models are ready using CheckModelsUseCase
        val modelsResult = checkModelsUseCase(
            downloadedModels = currentModels,
            expectedModels = filteredRemoteConfigs
        )

        // Include soft-deleted models in availableToRedownload ONLY if they are still on remote.
        val remoteShaSet = remoteConfigs.values.mapTo(mutableSetOf()) { it.metadata.sha256 }
        val availableSoftDeleted = softDeletedAssets.filter { it.metadata.sha256 in remoteShaSet }
        val availableToRedownload = (modelsResult.availableToRedownload + availableSoftDeleted)
            .distinctBy { it.metadata.sha256 }

        // Deferred Activation Strategy:
        // 1. If remote SHA matches existing: Update registry immediately (tuning-only change).
        // 2. If remote SHA differs: DO NOT update registry yet. Activation happens after download success.
        
        remoteConfigs.forEach { (modelType, remoteAsset) ->
            val existingAsset = currentModels[modelType]
            val sharedExistingAsset = currentModels.values.firstOrNull { asset ->
                asset.metadata.sha256 == remoteAsset.metadata.sha256 &&
                    asset.metadata.localFileName == remoteAsset.metadata.localFileName &&
                    asset.metadata.modelFileFormat == remoteAsset.metadata.modelFileFormat
            }

            if (sharedExistingAsset != null) {
                // The physical asset is already present locally, either for this slot or another slot
                // sharing the same file. Apply the slot config immediately instead of waiting for
                // a download that will never be scheduled.
                modelRegistry.activateLocalModel(modelType, remoteAsset)
                logPort.debug(TAG, "Applied slot activation for $modelType immediately")
            } else {
                logPort.debug(TAG, "Deferring registration for $modelType until download success")
            }
        }

        // Initialize the orchestrator with the startup result
        modelDownloadOrchestrator.initializeWithStartupResult(modelsResult)

        // Return result with soft-deleted models included in availableToRedownload
        return modelsResult.copy(availableToRedownload = availableToRedownload)
    }
}
