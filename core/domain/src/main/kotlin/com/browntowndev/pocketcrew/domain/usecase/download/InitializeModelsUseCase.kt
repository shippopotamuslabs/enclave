package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
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
        // Get models preferring OLD if it exists (for handling failed downloads)
        val currentModels = modelRegistry.getModelsPreferringOld()
        logPort.debug(TAG, "Current downloaded/fallback models: ${
            currentModels.map { cfg -> 
                cfg.copy(persona = cfg.persona.copy(systemPrompt = if (cfg.persona.systemPrompt.isNotEmpty()) "TRUNCATED FOR LOGS" else "EMPTY")) 
            }
        }")

        // Fetch remote config
        val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
        val remoteConfigs = remoteConfigResult.getOrElse {
            Log.e(TAG, "Failed to fetch remote config: ${it.message}")

            // If there are no current models, throw the error
            if (currentModels.isEmpty()) throw it

            // If config fetch error occurs, fallback to using current models
            currentModels
        }

        logPort.debug(TAG, "Fetched ${remoteConfigs.size} remote configs")

        // Check if models are ready using CheckModelsUseCase
        // Pass remoteConfigs instead of registeredModels
        val modelsResult = checkModelsUseCase(
            downloadedModels = currentModels,
            expectedModels = remoteConfigs
        )

        // Now register each remote config in the registry with CURRENT status.
        //
        // CRITICAL: Only mark existing config as OLD if:
        // 1. SHA256 changed, AND
        // 2. No other model configuration still uses the OLD SHA256 (shared file case).
        //    If another modelType still uses that SHA256, the file is still needed.
        val currentModelsByType = currentModels.associateBy { it.modelType }

        // Build a map of SHA256 -> count of modelTypes using it
        // This helps detect if a file is shared (used by multiple models)
        val sha256UsageCount = currentModels
            .groupBy { it.metadata.sha256 }
            .mapValues { it.value.size }

        remoteConfigs.forEach { remoteConfig ->
            val existingConfig = currentModelsByType[remoteConfig.modelType]
            val oldSha256 = existingConfig?.metadata?.sha256
            val newSha256 = remoteConfig.metadata.sha256

            // SHA256 changed if the old config has a different SHA256 than remote
            val sha256Changed = existingConfig == null ||
                existingConfig.metadata.sha256 != newSha256

            // Mark as OLD only if SHA256 changed AND no other model type
            // still uses the old SHA256 (file would be orphaned otherwise)
            val markAsOld = sha256Changed && oldSha256 != null &&
                (sha256UsageCount[oldSha256] ?: 0) <= 1

            modelRegistry.setRegisteredModel(
                remoteConfig,
                status = ModelStatus.CURRENT,
                markExistingAsOld = markAsOld
            )
        }
        logPort.debug(TAG, "Registered ${remoteConfigs.size} remote configs in registry")

        // Initialize the orchestrator with the startup result
        modelDownloadOrchestrator.initializeWithStartupResult(modelsResult)

        return modelsResult
    }
}
