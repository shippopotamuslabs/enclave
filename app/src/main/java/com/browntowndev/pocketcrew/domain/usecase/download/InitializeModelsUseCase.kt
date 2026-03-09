package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.ModelStatus
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
        logPort.debug(TAG, "Current downloaded/fallback models: $currentModels")

        // Fetch remote config
        val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
        val remoteConfigs = remoteConfigResult.getOrElse {
            Log.e(TAG, "Failed to fetch remote config: ${it.message}")
            emptyList()
        }

        logPort.debug(TAG, "Fetched ${remoteConfigs.size} remote configs")

        // Check if models are ready using CheckModelsUseCase
        // Pass remoteConfigs instead of registeredModels
        val modelsResult = checkModelsUseCase(
            downloadedModels = currentModels,
            expectedModels = remoteConfigs
        )

        // Now register each remote config in the registry with CURRENT status
        remoteConfigs.forEach { config ->
            modelRegistry.setRegisteredModel(config, ModelStatus.CURRENT)
        }
        logPort.debug(TAG, "Registered ${remoteConfigs.size} remote configs in registry")

        // Initialize the orchestrator with the startup result
        modelDownloadOrchestrator.initializeWithStartupResult(modelsResult)

        return modelsResult
    }
}
