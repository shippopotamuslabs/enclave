package com.browntowndev.pocketcrew.core.data.download
import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.download.CheckModelEligibilityUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeFileProgressUseCase
import com.browntowndev.pocketcrew.domain.usecase.download.ValidateDownloadConditionsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


@Singleton
class ModelDownloadOrchestratorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: DownloadSessionManager,
    private val validateConditions: ValidateDownloadConditionsUseCase,
    private val initializeFileProgress: InitializeFileProgressUseCase,
    private val workScheduler: DownloadWorkScheduler,
    private val progressParser: WorkProgressParser,
    private val modelRegistry: ModelRegistryPort,
    private val logger: LoggingPort,
    override val speedTracker: DownloadSpeedTrackerPort,
) : ModelDownloadOrchestratorPort {
    companion object {
        private const val TAG = "ModelDownloadOrchestrator"
        private const val TRACE_THROTTLE_MS = 5000L
    }

    private val _downloadState = MutableStateFlow(DownloadState(status = DownloadStatus.CHECKING))
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val stateManager = DownloadStateManager(_downloadState)

    private var lastTraceTimeMs = 0L

    // Cached startup result from InitializeModelsUseCase
    private var startupModelsResult: DownloadModelsResult? = null

    /**
     * Initialize the orchestrator with a pre-computed result from startup model check.
     * This avoids duplicate scanning when called from InitializeModelsUseCase.
     */
    override fun initializeWithStartupResult(result: DownloadModelsResult) {
        startupModelsResult = result

        val modelsCount = result.modelsToDownload.size
        logger.info(TAG, "Orchestrator initialized from startup result ($modelsCount models to download)")

        stateManager.updateStatus(DownloadStatus.CHECKING)

        val scan = result.scanResult

        if (scan.directoryError) {
            stateManager.updateState {
                copy(status = DownloadStatus.ERROR, errorMessage = "Failed to create models directory")
            }
            return
        }

        if (result.modelsToDownload.isEmpty()) {
            stateManager.updateStatus(DownloadStatus.READY)
        } else {
            val initResult = initializeFileProgress(scan, result.allModels, _downloadState.value.currentDownloads)
            stateManager.applyProgressInit(initResult)
            stateManager.updateStatus(DownloadStatus.IDLE)
        }
    }

    override suspend fun startDownloads(wifiOnly: Boolean): Boolean {
        val result = startupModelsResult
            ?: throw IllegalStateException("initializeWithStartupResult() was never called")

        return startDownloads(result, wifiOnly)
    }

    /**
     * Start downloads using pre-computed models result from checkModels.
     * This avoids duplicate scanning.
     */
    override suspend fun startDownloads(modelsResult: DownloadModelsResult, wifiOnly: Boolean): Boolean {
        val sessionId = sessionManager.createNewSession()
        logger.debug(TAG, "Starting download session with pre-computed result: $sessionId")

        val modelsToDownload = modelsResult.modelsToDownload
        val scan = modelsResult.scanResult

        if (modelsToDownload.isEmpty()) {
            logger.info(TAG, "No models to download - returning READY")
            stateManager.updateStatus(DownloadStatus.READY)
            return true
        }

        val check = validateConditions(modelsToDownload, wifiOnly)

        if (!check.canStart) {
            logger.info(TAG, "Validation failed - ${check.errorMessage}")
            val initResult = initializeFileProgress(scan, modelsResult.allModels, _downloadState.value.currentDownloads)
            stateManager.applyProgressInit(initResult)
            if (check.errorMessage?.contains("WiFi") == true) {
                stateManager.updateState { copy(wifiBlocked = true) }
            } else {
                stateManager.updateState { copy(status = DownloadStatus.ERROR, errorMessage = check.errorMessage) }
            }
            return false
        }

        logger.info(TAG, "Starting downloads for ${modelsToDownload.size} models (wifiOnly=$wifiOnly)")
        speedTracker.clearAll()

        val initResult = initializeFileProgress(scan, modelsResult.allModels, _downloadState.value.currentDownloads)
        stateManager.applyProgressInit(initResult)

        val modelsToEnqueue = modelsResult.allModels.filter { it.value in modelsToDownload }
        workScheduler.enqueue(modelsToEnqueue, sessionId, wifiOnly)
        stateManager.updateStatus(DownloadStatus.DOWNLOADING)
        return true
    }

    override suspend fun updateFromProgressUpdate(update: DownloadProgressUpdate) {
        val now = System.currentTimeMillis()
        val shouldTrace = (now - lastTraceTimeMs) >= TRACE_THROTTLE_MS

        if (shouldTrace) {
            logger.debug(TAG, "[TRACE] updateFromProgressUpdate: status=${update.status}")
            lastTraceTimeMs = now
            update.currentDownloads?.forEach { dl ->
                logger.debug(TAG, "[TRACE] File: ${dl.filename}, bytes=${dl.bytesDownloaded}/${dl.totalBytes}, status=${dl.status}")
            }
        }

        if (update.clearSession) {
            sessionManager.clearSession()
            // After successful download, update the registry with the downloaded models
            if (update.status == DownloadStatus.READY) {
                updateModelRegistry()
            }
        }
        stateManager.applyProgressUpdate(update)
    }

    private suspend fun updateModelRegistry() {
        // Clear old entries after successful download. This removes fallback versions
        // that were kept during the download process.
        try {
            modelRegistry.clearOld()
            logger.debug(TAG, "Cleared OLD registry entries after successful download")
        } catch (e: Exception) {
            logger.error(TAG, "Failed to clear OLD registry entries: ${e.message}")
        }

        // Clean up old files on filesystem - use ALL registered models, not just downloaded ones
        // This is critical: if we only pass modelsToDownload, valid files will be incorrectly deleted
        try {
            val allRegisteredAssets = modelRegistry.getRegisteredAssets()
            cleanupOrphanedModelFiles(allRegisteredAssets)
        } catch (e: Exception) {
            logger.error(TAG, "Failed during filesystem cleanup: ${e.message}")
        }
    }

    /**
     * Delete any model files on the filesystem that are not in the current model configurations.
     * This handles cases where a model was removed from the remote config.
     */
    private fun cleanupOrphanedModelFiles(currentAssets: List<LocalModelAsset>) {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        if (!modelsDir.exists()) return

        // Get filenames of current assets
        val currentFilenames = currentAssets.map { it.metadata.localFileName }.toSet()

        // Get all model files in the directory (excluding temp files)
        val existingFiles = modelsDir.listFiles { file ->
            file.isFile && !file.name.endsWith(ModelConfig.TEMP_EXTENSION)
        } ?: return

        // Delete any file that is not in the current configuration
        var deletedCount = 0
        for (file in existingFiles) {
            if (file.name !in currentFilenamesSet) {
                val deleted = file.delete()
                if (deleted) {
                    deletedCount++
                    logger.info(TAG, "Deleted orphaned model file: ${file.name}")
                } else {
                    logger.warning(TAG, "Failed to delete orphaned model file: ${file.name}")
                }
            }
        }

        if (deletedCount > 0) {
            logger.info(TAG, "Cleaned up $deletedCount orphaned model file(s)")
        }
    }

    override fun pauseDownloads() {
        logger.info(TAG, "Pausing downloads")
        workScheduler.cancel()
        stateManager.updateStatus(DownloadStatus.PAUSED)
    }

    override suspend fun resumeDownloads() {
        val wifiOnly = _downloadState.value.wifiBlocked
        logger.info(TAG, "Resuming downloads (wifiOnly=$wifiOnly)")
        startDownloads(!wifiOnly)
    }

    override suspend fun cancelDownloads() {
        logger.info(TAG, "Cancelling downloads and cleaning up")
        workScheduler.cancel()
        workScheduler.cleanupTempFiles()
        speedTracker.clearAll()
        stateManager.updateState {
            copy(status = DownloadStatus.IDLE, currentDownloads = emptyList(),
                overallProgress = 0f, modelsComplete = 0)
        }
    }

    override suspend fun retryFailed() {
        cancelDownloads()
        // If startupModelsResult is null (edge case), set error and return
        // This can happen if the download screen was opened without proper initialization
        if (startupModelsResult == null) {
            logger.warning(TAG, "retryFailed called but startupModelsResult is null - setting error state")
            stateManager.updateState {
                copy(status = DownloadStatus.ERROR, errorMessage = "Download state lost. Please restart the app.")
            }
            return
        }
        startDownloads(wifiOnly = false)
    }

    override suspend fun downloadOnMobileData() {
        stateManager.updateState { copy(wifiBlocked = false) }
        // If startupModelsResult is null (edge case), set error and return
        if (startupModelsResult == null) {
            logger.warning(TAG, "downloadOnMobileData called but startupModelsResult is null - setting error state")
            stateManager.updateState {
                copy(status = DownloadStatus.ERROR, errorMessage = "Download state lost. Please restart the app.")
            }
            return
        }
        startDownloads(wifiOnly = false)
    }

    override fun setError(message: String) {
        stateManager.updateState { copy(status = DownloadStatus.ERROR, errorMessage = message) }
    }
}
