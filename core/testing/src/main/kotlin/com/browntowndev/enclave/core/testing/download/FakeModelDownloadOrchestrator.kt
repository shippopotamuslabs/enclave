package com.browntowndev.enclave.core.testing.download

import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadProgressUpdate
import com.browntowndev.enclave.domain.model.download.DownloadState
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Trivial [DownloadSpeedTrackerPort] fake (ENC-28): zero speed, no ETA. */
class FakeDownloadSpeedTracker : DownloadSpeedTrackerPort {
    val clearedFiles = mutableListOf<String>()
    var clearAllCount = 0
        private set

    override fun calculateSpeedAndEta(filename: String, bytesDownloaded: Long, totalSize: Long): Pair<Double, Long> =
        0.0 to 0L

    override fun calculateAggregateSpeedAndEta(totalBytesDownloaded: Long, totalSize: Long): Pair<Double, Long> =
        0.0 to 0L

    override fun formatEta(seconds: Long): String = "${seconds}s"

    override fun clear(filename: String) {
        clearedFiles += filename
    }

    override fun clearAll() {
        clearAllCount++
        clearedFiles.clear()
    }
}

/**
 * In-memory [ModelDownloadOrchestratorPort] fake (ENC-28). State is a plain
 * [MutableStateFlow] tests can drive directly; every entry point records its
 * invocations. [updateFromProgressUpdate] applies the update's status (and any
 * non-null fields) to the state, mirroring the visible contract of the real
 * orchestrator. [onStartDownloads] lets a test script side effects of a start.
 */
class FakeModelDownloadOrchestrator : ModelDownloadOrchestratorPort {
    val downloadStateFlow = MutableStateFlow(DownloadState(status = DownloadStatus.IDLE))
    override val downloadState: StateFlow<DownloadState> = downloadStateFlow

    override val speedTracker: DownloadSpeedTrackerPort = FakeDownloadSpeedTracker()

    private val snackbarFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val snackbarMessages: Flow<String> = snackbarFlow

    fun emitSnackbarMessage(message: String) {
        snackbarFlow.tryEmit(message)
    }

    val initializeCalls = mutableListOf<DownloadModelsResult>()
    val startCalls = mutableListOf<Pair<DownloadModelsResult, Boolean>>()
    val startWithoutResultCalls = mutableListOf<Boolean>()
    val progressUpdates = mutableListOf<DownloadProgressUpdate>()
    val setErrorCalls = mutableListOf<String>()

    var startResult: Boolean = true
    var onStartDownloads: ((DownloadModelsResult) -> Unit)? = null

    var pauseCount = 0
        private set
    var resumeCount = 0
        private set
    var cancelCount = 0
        private set
    var retryCount = 0
        private set
    var downloadOnMobileDataCount = 0
        private set

    override fun initializeWithStartupResult(result: DownloadModelsResult) {
        initializeCalls += result
    }

    override suspend fun startDownloads(modelsResult: DownloadModelsResult, wifiOnly: Boolean): Boolean {
        startCalls += modelsResult to wifiOnly
        onStartDownloads?.invoke(modelsResult)
        return startResult
    }

    override suspend fun startDownloads(wifiOnly: Boolean): Boolean {
        startWithoutResultCalls += wifiOnly
        return startResult
    }

    override suspend fun updateFromProgressUpdate(progressUpdate: DownloadProgressUpdate) {
        progressUpdates += progressUpdate
        val current = downloadStateFlow.value
        downloadStateFlow.value = current.copy(
            status = progressUpdate.status,
            overallProgress = progressUpdate.overallProgress ?: current.overallProgress,
            modelsComplete = progressUpdate.modelsComplete ?: current.modelsComplete,
            modelsTotal = progressUpdate.modelsTotal ?: current.modelsTotal,
            currentDownloads = progressUpdate.currentDownloads ?: current.currentDownloads,
            estimatedTimeRemaining = progressUpdate.estimatedTimeRemaining ?: current.estimatedTimeRemaining,
            currentSpeedMBs = progressUpdate.currentSpeedMBs ?: current.currentSpeedMBs,
            waitingForUnmeteredNetwork = progressUpdate.waitingForUnmeteredNetwork ?: current.waitingForUnmeteredNetwork,
            errorMessage = progressUpdate.errorMessage ?: current.errorMessage,
        )
    }

    override fun pauseDownloads() {
        pauseCount++
    }

    override suspend fun resumeDownloads() {
        resumeCount++
    }

    override suspend fun cancelDownloads() {
        cancelCount++
    }

    override suspend fun retryFailed() {
        retryCount++
    }

    override suspend fun downloadOnMobileData() {
        downloadOnMobileDataCount++
    }

    override fun setError(message: String) {
        setErrorCalls += message
    }
}
