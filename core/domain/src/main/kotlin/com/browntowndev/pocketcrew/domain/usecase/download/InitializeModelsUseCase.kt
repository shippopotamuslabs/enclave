package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for initializing models at app startup.
 * Fetches remote config, registers expected models in registry, and checks which models need to be downloaded.
 */
class InitializeModelsUseCase @Inject constructor(
    private val modelConfigFetcher: ModelConfigFetcherPort,
    private val activeModelProvider: ActiveModelProviderPort,
    private val syncLocalModelRegistryUseCase: SyncLocalModelRegistryUseCase,
    private val localModelRepository: com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort,
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
        // Fetch remote config
        val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
        val remoteConfigs = remoteConfigResult.getOrElse {
            logPort.error(TAG, "Failed to fetch remote config: ${it.message}")

            // If config fetch error occurs, fallback to using current models
            val currentModels = com.browntowndev.pocketcrew.domain.model.inference.ModelType.entries
                .associateWith { modelType -> 
                    val config = activeModelProvider.getActiveConfiguration(modelType)
                    if (config != null && config.isLocal) {
                        localModelRepository.getAssetByConfigId(config.id)
                    } else null
                }
                .filterValues { it != null }
                .mapValues { it.value!! }

            // If there are no current models, throw the error
            if (currentModels.isEmpty()) throw it
            
            currentModels
        }

        logPort.debug(TAG, "Fetched ${remoteConfigs.size} remote configs")

        // Source of truth for soft-deleted models is getSoftDeletedModels()
        val softDeletedAssets = localModelRepository.getSoftDeletedModels()
        
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
            expectedModels = filteredRemoteConfigs
        )

        // Include soft-deleted models in availableToRedownload ONLY if they are still on remote.
        val remoteShaMap = remoteConfigs.values.associateBy { it.metadata.sha256 }
        val availableSoftDeleted = softDeletedAssets
            .filter { it.metadata.sha256 in remoteShaMap.keys }
            .map { asset ->
                val remoteAsset = remoteShaMap[asset.metadata.sha256]!!
                asset.copy(
                    metadata = asset.metadata.copy(
                        source = remoteAsset.metadata.source
                    )
                )
            }
        val availableToRedownload = (modelsResult.availableToRedownload + availableSoftDeleted)
            .distinctBy { it.metadata.sha256 }

        // Deferred Activation Strategy:
        // 1. If physical file is already valid (not in modelsToDownload): Update registry immediately (tuning-only change).
        // 2. If physical file is missing/invalid: DO NOT update registry yet. Activation happens after download success.
        
        val modelsToDownloadShaSet = modelsResult.modelsToDownload.mapTo(mutableSetOf()) { it.metadata.sha256 }
        
        remoteConfigs.forEach { (modelType, remoteAsset) ->
            if (remoteAsset.metadata.sha256 !in modelsToDownloadShaSet) {
                // The physical asset is already present locally and valid.
                // Apply the slot config immediately instead of waiting for a download that will never be scheduled.
                syncLocalModelRegistryUseCase(modelType, remoteAsset)
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
