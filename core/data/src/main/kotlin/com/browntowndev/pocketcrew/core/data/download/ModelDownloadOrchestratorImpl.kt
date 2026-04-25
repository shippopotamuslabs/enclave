package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadRequestKind
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadWorkRequest
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.DeviceEnvironmentRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeFileProgressUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel


@Singleton
class ModelDownloadOrchestratorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: DownloadSessionManager,
    private val deviceEnvironmentRepository: DeviceEnvironmentRepositoryPort,
    private val initializeFileProgress: InitializeFileProgressUseCase,
    private val workScheduler: DownloadWorkScheduler,
    private val progressParser: WorkProgressParser,
    private val logger: LoggingPort,
    override val speedTracker: DownloadSpeedTrackerPort,
) : ModelDownloadOrchestratorPort {
    companion object {
        private const val TAG = "ModelDownloadOrchestrator"
        private const val TRACE_THROTTLE_MS = 5000L
    }

    private val _snackbarMessages = Channel<String>(Channel.BUFFERED)
    override val snackbarMessages = _snackbarMessages.receiveAsFlow()

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
            val initResult = initializeFileProgress(scan, result.allModels, _downloadState.value.currentDownloads, result.utilityAssets)
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

        // Check storage availability (hard block — can't download without space).
        // WiFi gating is handled by WorkManager's UNMETERED constraint, not a pre-check.
        // The pre-check caused a race condition where ConnectivityManager hadn't
        // updated WiFi state during app startup, blocking downloads that should start.
        if (!deviceEnvironmentRepository.hasRequiredStorage(15L * 1024 * 1024 * 1024)) {
            val errorMsg = "Insufficient storage space. Need at least 15 GB free."
            logger.info(TAG, "Validation failed - $errorMsg")
            val initResult = initializeFileProgress(scan, modelsResult.allModels, _downloadState.value.currentDownloads, modelsResult.utilityAssets)
            stateManager.applyProgressInit(initResult)
            stateManager.updateState { copy(status = DownloadStatus.ERROR, errorMessage = errorMsg) }
            return false
        }

        logger.info(TAG, "Starting downloads for ${modelsToDownload.size} models (wifiOnly=$wifiOnly)")
        speedTracker.clearAll()

        val initResult = initializeFileProgress(scan, modelsResult.allModels, _downloadState.value.currentDownloads, modelsResult.utilityAssets)
        stateManager.applyProgressInit(initResult)

        val assetsToEnqueue = (modelsResult.allModels.values + modelsResult.utilityAssets).filter { asset ->
            modelsToDownload.any { candidate -> candidate.matchesPhysicalAsset(asset) }
        }
        val fileSpecs = assetsToEnqueue
            .distinctBy { it.metadata.sha256 }
            .map { asset ->
                DownloadFileSpec(
                    remoteFileName = asset.metadata.remoteFileName,
                    localFileName = asset.metadata.localFileName,
                    sha256 = asset.metadata.sha256,
                    sizeInBytes = asset.metadata.sizeInBytes,
                    huggingFaceModelName = asset.metadata.huggingFaceModelName,
                    huggingFacePath = asset.metadata.huggingFacePath,
                    source = asset.metadata.source.name,
                    modelFileFormat = asset.metadata.modelFileFormat.name,
                    utilityType = asset.metadata.utilityType?.name,
                    mmprojRemoteFileName = asset.metadata.mmprojRemoteFileName,
                    mmprojLocalFileName = asset.metadata.mmprojLocalFileName,
                    mmprojSha256 = asset.metadata.mmprojSha256,
                    mmprojSizeInBytes = asset.metadata.mmprojSizeInBytes,
                )
            }

        val request = DownloadWorkRequest(
            files = fileSpecs,
            sessionId = sessionId,
            requestKind = DownloadRequestKind.INITIALIZE_MODELS,
            targetModelId = null,
            wifiOnly = wifiOnly,
        )

        logger.info(
            TAG,
            "Enqueuing ${assetsToEnqueue.size} assets for ${fileSpecs.size} physical downloads: ${
                assetsToEnqueue.joinToString { asset ->
                    val label = asset.configurations.firstOrNull()?.displayName ?: asset.metadata.localFileName
                    asset.metadata.utilityType?.let { "${it.name} -> $label" } ?: label
                }
            }"
        )
        workScheduler.enqueue(request)

        // Set DOWNLOADING status immediately — WorkManager handles WiFi gating via
        // UNMETERED constraint. If WiFi is unavailable, the work transitions to BLOCKED
        // and the observer flow reports WIFI_BLOCKED via WorkProgressParser.
        // Do NOT call isWifiConnected() here — it races with ConnectivityManager during
        // app startup and incorrectly sets WIFI_BLOCKED when WiFi is already available.
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
        }
        stateManager.applyProgressUpdate(update)
    }



    override fun pauseDownloads() {
        logger.info(TAG, "Pausing downloads")
        workScheduler.cancel()
        stateManager.updateStatus(DownloadStatus.PAUSED)
    }

    override suspend fun resumeDownloads() {
        val wifiOnly = _downloadState.value.waitingForUnmeteredNetwork
        logger.info(TAG, "Resuming downloads (wifiOnly=$wifiOnly)")
        startDownloads(wifiOnly)
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
        stateManager.updateState { copy(waitingForUnmeteredNetwork = false) }
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

    private fun LocalModelAsset.matchesPhysicalAsset(other: LocalModelAsset): Boolean {
        return metadata.sha256 == other.metadata.sha256 &&
            metadata.localFileName == other.metadata.localFileName &&
            metadata.modelFileFormat == other.metadata.modelFileFormat
    }
}
