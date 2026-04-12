package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import javax.inject.Inject

/**
 * Use case to re-download a soft-deleted local model.
 * Matches soft-deleted model by SHA256 against remote config, restores system presets,
 * and enqueues download work for the model and its mmproj (if applicable).
 */
class ReDownloadModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
    private val modelConfigFetcher: ModelConfigFetcherPort,
    private val downloadWorkScheduler: DownloadWorkSchedulerPort,
    private val loggingPort: LoggingPort
) {
    suspend operator fun invoke(modelId: LocalModelId): Result<Unit> = runCatching {
        loggingPort.debug("ReDownloadModelUseCase", "Starting re-download for model: $modelId")
        
        // 1. Get the existing model asset (which is currently soft-deleted)
        val existingAsset = localModelRepository.getAssetById(modelId)
            ?: throw IllegalStateException("Soft-deleted model $modelId not found in repository")

        // 2. Fetch remote configurations
        val remoteAssetsMap = modelConfigFetcher.fetchRemoteConfig()
            .getOrThrow()

        // 3. Find matching model in remote config by SHA256
        val matchingRemoteAsset = remoteAssetsMap.values.find { 
            it.metadata.sha256 == existingAsset.metadata.sha256 
        } ?: throw IllegalStateException("Model with SHA256 ${existingAsset.metadata.sha256} no longer exists in remote configuration")

        // 4. Create new configurations for the model from remote presets
        // We set isSystemPreset=true and use our current localModelId
        val restoredConfigs = matchingRemoteAsset.configurations.map { remoteConfig ->
            remoteConfig.copy(
                localModelId = modelId,
                isSystemPreset = true
            )
        }

        // 5. Restore the model in the repository with the new configurations
        localModelRepository.restoreSoftDeletedModel(modelId, restoredConfigs)

        // 6. Schedule the download via the orchestrator/worker
        // Use ModelType.UNASSIGNED to avoid overwriting current logic
        downloadWorkScheduler.scheduleModelDownload(
            modelType = ModelType.UNASSIGNED,
            modelAsset = existingAsset.copy(configurations = restoredConfigs)
        )
        
        loggingPort.debug("ReDownloadModelUseCase", "Successfully scheduled re-download for model: $modelId")
    }
}

