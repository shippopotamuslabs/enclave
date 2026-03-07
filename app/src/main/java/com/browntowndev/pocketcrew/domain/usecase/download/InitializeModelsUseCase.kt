package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for initializing models at app startup.
 * Fetches remote config, updates the registry, and checks which models need to be downloaded.
 */
class InitializeModelsUseCase @Inject constructor(
    private val modelConfigFetcher: ModelConfigFetcherPort,
    private val modelRegistry: ModelRegistryPort,
    private val modelDownloadOrchestrator: ModelDownloadOrchestratorPort,
    private val modelConfigCache: ModelConfigCachePort,
    private val modelUrlProvider: ModelUrlProviderPort,
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
        // Grab current models from registry
        val currentModels = modelRegistry.getRegisteredModels()
        logPort.debug(TAG, "Current models: $currentModels")

        // Fetch remote config - now returns ModelConfiguration directly
        val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
        val remoteConfigs = remoteConfigResult.getOrElse {
            Log.e(TAG, "Failed to fetch remote config: ${it.message}")
            emptyList()
        }

        // CRITICAL: Update ModelRegistry with remote config BEFORE any downloads
        // This allows us to trust the registry for partial download validation
        preUpdateModelRegistry(remoteConfigs)

        // Initialize the model config cache
        modelConfigCache.initialize()
        Log.d(TAG, "Model config cache initialized: ${modelConfigCache.fullConfig}")

        // Check if models are ready using CheckModelsUseCase
        // This determines the initial route before Compose even starts
        val modelsResult = checkModelsUseCase(
            originalModels = currentModels,
            newModels = modelConfigCache.fullConfig
        )

        // Initialize the orchestrator with the startup result
        modelDownloadOrchestrator.initializeWithStartupResult(modelsResult)

        return modelsResult
    }

    /**
     * Pre-update ModelRegistry with remote config values BEFORE any downloads start.
     * This allows us to trust the registry for partial download validation.
     */
    private suspend fun preUpdateModelRegistry(allModels: List<ModelConfiguration>) {
        for (model in allModels) {
            try {
                modelRegistry.setRegisteredModel(model)
                Log.d(TAG, "Pre-updated registry: ${model.modelType} -> ${model.metadata.displayName} (MD5: ${model.metadata.md5}, format: ${model.metadata.modelFileFormat})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-update registry for ${model.modelType}: ${e.message}")
            }
        }
    }
}
