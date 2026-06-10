package com.browntowndev.enclave.feature.download

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.browntowndev.enclave.core.data.download.WorkProgressParser
import com.browntowndev.enclave.core.data.repository.DownloadWorkRepository
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.download.DownloadKey
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadState
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.model.download.FileProgress
import com.browntowndev.enclave.domain.model.download.FileStatus
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Model Download screen.
 *
 * Exposes download state and handles user actions like pause/resume/cancel.
 */
@HiltViewModel(assistedFactory = DownloadViewModel.Factory::class)
class DownloadViewModel
    @AssistedInject
    constructor(
        private val modelDownloadOrchestrator: ModelDownloadOrchestratorPort,
        private val downloadWorkRepository: DownloadWorkRepository,
        private val progressParser: WorkProgressParser,
        private val errorHandler: ViewModelErrorHandler,
        @Assisted private val modelsResult: DownloadModelsResult,
        @Assisted private val initialErrorMessage: String?,
        @Assisted private val autoStartDownloads: Boolean = true,
        @Assisted private val refreshDownloadPlan: DownloadPlanRefresher? = null,
    ) : ViewModel() {
        companion object {
            private const val TAG = "DownloadViewModel"
            private const val THROTTLE_MS = 5000L
        }

        private val utilityDisplayNameBySha256: Map<String, String> =
            modelsResult.utilityAssets
                .mapNotNull { asset ->
                    asset.metadata.utilityType?.let { utilityType ->
                        asset.metadata.sha256 to utilityType.displayName()
                    }
                }.toMap()

        @AssistedFactory
        interface Factory {
            fun create(
                modelsResult: DownloadModelsResult,
                initialErrorMessage: String? = null,
                autoStartDownloads: Boolean = true,
                refreshDownloadPlan: DownloadPlanRefresher? = null,
            ): DownloadViewModel
        }

        // UI model for displaying file progress with anthropomorphized names
        data class FileProgressUiModel(
            val filename: String,
            val sha256: String,
            val displayName: String,
            val bytesDownloaded: Long,
            val totalBytes: Long,
            val status: FileStatus,
            val speedMBs: Double? = null,
        ) {
            val progress: Float
                get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
        }

        // Convert FileProgress to UI model with display names from ModelRegistry
        internal fun FileProgress.toUiModel(): FileProgressUiModel {
            val displayName =
                if (modelTypes.isNotEmpty()) {
                    val names = modelTypes.map { it.displayName() }.distinct()
                    if (names.size == 1) {
                        names.first()
                    } else {
                        names.joinToString(" + ")
                    }
                } else {
                    utilityDisplayNameBySha256[sha256] ?: filename
                }
            return FileProgressUiModel(
                filename = filename,
                sha256 = sha256,
                displayName = displayName,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                status = status,
                speedMBs = speedMBs,
            )
        }

        // Throttle tracking for verbose trace logging
        private var lastTraceLogTime = 0L

        // Foreground state tracking for race condition prevention
        // Set to true when the app moves to foreground (ON_RESUME lifecycle event)
        // This allows startDownloads() to know if it's safe to proceed
        @Volatile
        private var isInForeground = false

        // Flag to track pending download checks that were blocked due to not being in foreground
        private var hasPendingDownloadCheck = false

        // Job to track work observation coroutine - prevents concurrent collections
        private var workObservationJob: Job? = null

        // Tracks whether startDownloads() has already been called to prevent
        // duplicate enqueuing from both auto-start and UI-triggered paths
        @Volatile
        private var downloadsStarted = false

        val downloadState: StateFlow<DownloadState> =
            modelDownloadOrchestrator.downloadState
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly, // Start collecting immediately for test compatibility
                    initialValue = modelDownloadOrchestrator.downloadState.value,
                )

        // UI model with anthropomorphized names
        val fileProgressList: StateFlow<List<FileProgressUiModel>> =
            modelDownloadOrchestrator.downloadState
                .map { state -> state.currentDownloads.map { it.toUiModel() } }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly, // Start collecting immediately for test compatibility
                    initialValue = emptyList(),
                )

        // User preferences
        private val _wifiOnly = MutableStateFlow(true)
        val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

        init {
            // If there's an initial error from app initialization, set it and skip download logic
            // Don't try to download - let the user retry manually
            if (initialErrorMessage != null) {
                Log.d(TAG, "[TRACE] init: App initialization failed, setting error and skipping download")
                errorHandler.handleError(TAG, "App initialization failed", Exception(initialErrorMessage), initialErrorMessage)
                modelDownloadOrchestrator.setError(initialErrorMessage)
            } else {
                viewModelScope.launch {
                    // Always observe work progress when on download screen
                    // This handles cases where checkModels() might have transient failures
                    // but downloads are triggered through other paths
                    observeWorkProgress()

                    // Only auto-start downloads if explicitly enabled
                    // This allows UI layer to control when downloads start (e.g., after permission confirmation)
                    val modelsToDownload = modelsResult.modelsToDownload
                    if (autoStartDownloads && modelsToDownload.isNotEmpty()) {
                        Log.d(TAG, "[TRACE] init: ${modelsToDownload.size} models to download (auto-start enabled)")
                        startDownloads()
                    } else if (modelsToDownload.isEmpty()) {
                        Log.d(TAG, "[TRACE] init: No models missing")
                    } else {
                        Log.d(
                            TAG,
                            "[TRACE] init: ${modelsToDownload.size} models to download (auto-start disabled, waiting for UI trigger)",
                        )
                    }
                }
            }
        }

        /**
         * Check and start downloads if needed.
         * Called after user grants notification permission — handles the case where
         * auto-start set downloadsStarted=true but the orchestrator hasn't progressed
         * past IDLE/CHECKING (e.g., due to a race condition during init).
         */
        fun checkModels() {
            Log.d(TAG, "Checking models...")
            viewModelScope.launch {
                val modelsToDownload = modelsResult.modelsToDownload
                if (modelsToDownload.isNotEmpty()) {
                    val currentStatus = modelDownloadOrchestrator.downloadState.value.status
                    // If downloads should have started but status is still IDLE/CHECKING,
                    // the init auto-start likely failed. Reset the guard and retry.
                    val isStalledStatus =
                        currentStatus != null &&
                            currentStatus != DownloadStatus.DOWNLOADING &&
                            currentStatus != DownloadStatus.PAUSED &&
                            currentStatus != DownloadStatus.WIFI_BLOCKED &&
                            currentStatus != DownloadStatus.ERROR
                    if (downloadsStarted && isStalledStatus) {
                        Log.d(TAG, "checkModels: downloadsStarted=true but status=$currentStatus, resetting guard")
                        downloadsStarted = false
                    }
                    Log.d(TAG, "${modelsToDownload.size} models missing, starting downloads")
                    // Start downloads
                    startDownloads()
                } else {
                    Log.d(TAG, "All models are ready")
                }
            }
        }

        /**
         * Called when a download check is requested but should wait for the app to be fully foregrounded.
         */
        fun schedulePendingDownloadCheck() {
            Log.d(TAG, "Scheduling pending download check for when app is foregrounded")
            hasPendingDownloadCheck = true
            // If already foregrounded, execute immediately
            if (isInForeground) {
                onAppForegrounded()
            }
        }

        /**
         * Start downloading models.
         * WorkManager handles ForegroundServiceStartNotAllowedException internally
         * by returning Result.retry(), so we no longer gate on foreground state.
         */
        fun startDownloads() {
            if (downloadsStarted) {
                Log.d(TAG, "startDownloads: already started, skipping duplicate call")
                return
            }
            downloadsStarted = true
            Log.d(TAG, "startDownloads called (wifiOnly=${_wifiOnly.value})")

            viewModelScope.launch {
                modelDownloadOrchestrator.startDownloads(modelsResult = modelsResult, wifiOnly = _wifiOnly.value)
                // Re-observe work progress after starting downloads
                // The work ID may have changed (KEEP_POLICY_REPLACE creates new work)
                observeWorkProgress()
            }
        }

        /**
         * Called when the app moves to foreground.
         * Retries pending download check if one was blocked.
         */
        fun onAppForegrounded() {
            Log.d(TAG, "App foregrounded")
            isInForeground = true
            // Re-trigger download check if there was a pending request
            if (hasPendingDownloadCheck) {
                hasPendingDownloadCheck = false
                viewModelScope.launch {
                    startDownloads()
                }
            }
        }

        /**
         * Called when the app moves to background.
         * Used to track foreground state for race condition prevention.
         */
        fun onAppBackgrounded() {
            Log.d(TAG, "App backgrounded")
            isInForeground = false
        }

        /**
         * Pause all downloads.
         */
        fun pauseDownloads() {
            Log.i(TAG, "User action: pause downloads")
            downloadsStarted = false
            modelDownloadOrchestrator.pauseDownloads()
        }

        /**
         * Resume downloads.
         */
        fun resumeDownloads() {
            Log.i(TAG, "User action: resume downloads")
            viewModelScope.launch {
                modelDownloadOrchestrator.resumeDownloads()
            }
        }

        /**
         * Cancel all downloads.
         */
        fun cancelDownloads() {
            Log.i(TAG, "User action: cancel downloads")
            viewModelScope.launch {
                modelDownloadOrchestrator.cancelDownloads()
            }
        }

        /**
         * Retry failed downloads.
         */
        fun retryFailed() {
            Log.i(TAG, "User action: retry failed downloads")
            downloadsStarted = false
            viewModelScope.launch {
                modelDownloadOrchestrator.pauseDownloads()
                refreshDownloadPlan?.refresh()
                modelDownloadOrchestrator.retryFailed()
                observeWorkProgress()
            }
        }

        /**
         * Override WiFi-only and download on mobile data.
         */
        fun downloadOnMobileData() {
            Log.i(TAG, "User action: download on mobile data (override WiFi-only)")
            _wifiOnly.value = false
            downloadsStarted = false
            viewModelScope.launch {
                modelDownloadOrchestrator.downloadOnMobileData()
                observeWorkProgress()
            }
        }

        /**
         * Toggle WiFi-only preference.
         */
        fun setWifiOnly(enabled: Boolean) {
            Log.d(TAG, "User action: set WiFi-only to $enabled")
            _wifiOnly.value = enabled
        }

        private fun observeWorkProgress() {
            // Cancel any existing observation to prevent concurrent flow collections
            workObservationJob?.cancel()
            workObservationJob =
                viewModelScope.launch {
                    var previousState: WorkInfo.State? = null

                    // Subscribe directly to the chain-aware flow. No UUID polling needed —
                    // the repository's selectBestWorkInfo always picks the active worker.
                    downloadWorkRepository
                        .observeDownloadProgress()
                        .collect { workInfo ->
                            if (workInfo == null) return@collect

                            val currentTime = System.currentTimeMillis()
                            val shouldLog = currentTime - lastTraceLogTime >= THROTTLE_MS

                            if (previousState != workInfo.state) {
                                Log.d(
                                    TAG,
                                    "[TRACE] observeWorkProgress: State changed ${previousState?.name ?: "NULL"} -> ${workInfo.state.name}",
                                )
                                previousState = workInfo.state
                            }

                            if (shouldLog) {
                                lastTraceLogTime = currentTime
                                val progress = workInfo.progress.getLong(DownloadKey.PROGRESS.key, -1)
                                val totalBytes = workInfo.progress.getLong(DownloadKey.TOTAL_BYTES.key, -1)
                                val downloadedBytes = workInfo.progress.getLong(DownloadKey.BYTES_DOWNLOADED.key, -1)
                                Log.d(
                                    TAG,
                                    "[TRACE] observeWorkProgress: FLOW EMITTED - state=${workInfo.state}, " +
                                        "id=${workInfo.id}, progress=$progress, totalBytes=$totalBytes, downloadedBytes=$downloadedBytes",
                                )
                            }

                            val currentDownloads = modelDownloadOrchestrator.downloadState.value.currentDownloads
                            val progressUpdate = progressParser.parse(workInfo, currentDownloads)

                            if (progressUpdate != null) {
                                if (shouldLog) {
                                    Log.d(
                                        TAG,
                                        "[TRACE] observeWorkProgress: Calling updateFromProgressUpdate " +
                                            "with status=${progressUpdate.status}",
                                    )
                                }
                                modelDownloadOrchestrator.updateFromProgressUpdate(progressUpdate)
                            }
                        }
                }
        }
    }
