package com.browntowndev.pocketcrew.presentation.screen.download

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.data.repository.DownloadWorkRepository
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelFile
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel for the Model Download screen.
 *
 * Exposes download state and handles user actions like pause/resume/cancel.
 */
@HiltViewModel(assistedFactory = DownloadViewModel.Factory::class)
class DownloadViewModel @AssistedInject constructor(
    private val modelDownloadOrchestrator: ModelDownloadOrchestratorPort,
    private val downloadWorkRepository: DownloadWorkRepository,
    private val modelRegistry: ModelRegistryPort,
    @Assisted private val modelsResult: DownloadModelsResult,
    @Assisted private val initialErrorMessage: String?,
    @Assisted private val autoStartDownloads: Boolean = true,
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadViewModel"
        private const val THROTTLE_MS = 5000L
    }

    // Cache of display names loaded at init time
    private var displayNameCache: Map<ModelType, String> = emptyMap()

    init {
        viewModelScope.launch {
            displayNameCache = ModelType.entries.associateWith { modelType ->
                modelRegistry.getRegisteredModelSync(modelType)?.metadata?.displayName
                    ?: modelType.name.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            modelsResult: DownloadModelsResult?,
            initialErrorMessage: String? = null,
            autoStartDownloads: Boolean = true
        ): DownloadViewModel
    }

    // UI model for displaying file progress with anthropomorphized names
    data class FileProgressUiModel(
        val filename: String,
        val displayName: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val status: FileStatus,
        val speedMBs: Double? = null
    ) {
        val progress: Float
            get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    }

    // Get display name from cached registry values
    private fun ModelType.toDisplayName(): String {
        return displayNameCache[this] ?: this.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    // Convert FileProgress to UI model with display names from ModelRegistry
    internal fun FileProgress.toUiModel(): FileProgressUiModel {
        val displayName = if (modelTypes.isNotEmpty()) {
            val names = modelTypes.map { it.toDisplayName() }
            if (names.size == 1) {
                names.first()
            } else {
                names.joinToString(" + ")
            }
        } else {
            filename
        }
        return FileProgressUiModel(
            filename = filename,
            displayName = displayName,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            status = status,
            speedMBs = speedMBs
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

    val downloadState: StateFlow<DownloadState> = modelDownloadOrchestrator.downloadState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // Start collecting immediately for test compatibility
            initialValue = modelDownloadOrchestrator.downloadState.value
        )

    // UI model with anthropomorphized names
    val fileProgressList: StateFlow<List<FileProgressUiModel>> = modelDownloadOrchestrator.downloadState
        .map { state -> state.currentDownloads.map { it.toUiModel() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // Start collecting immediately for test compatibility
            initialValue = emptyList()
        )

    // User preferences
    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    // Show WiFi override dialog
    private val _showWifiDialog = MutableStateFlow(false)
    val showWifiDialog: StateFlow<Boolean> = _showWifiDialog.asStateFlow()

    init {
        // If there's an initial error from app initialization, set it and skip download logic
        // Don't try to download - let the user retry manually
        if (initialErrorMessage != null) {
            Log.d(TAG, "[TRACE] init: App initialization failed, setting error and skipping download")
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
                    Log.d(TAG, "[TRACE] init: ${modelsToDownload.size} models to download (auto-start disabled, waiting for UI trigger)")
                }
            }
        }
    }

    /**
     * Check and start downloads if needed.
     */
    fun checkModels() {
        Log.d(TAG, "Checking models...")
        viewModelScope.launch {
            val modelsToDownload = modelsResult.modelsToDownload
            if (modelsToDownload.isNotEmpty()) {
                Log.d(TAG, "${modelsToDownload.size} models missing, starting downloads")
                // Start downloads
                startDownloads()
            } else {
                Log.d(TAG, "All models are ready")
            }
        }
    }

    /**
     * Start downloading models.
     * The foreground state is tracked via lifecycle callbacks in the UI layer.
     * If the app is in foreground when this is called, downloads proceed normally.
     * If not in foreground, downloads will fail with ForegroundServiceStartNotAllowedException
     * but that's handled gracefully by the worker returning failure.
     */
    fun startDownloads() {
        Log.d(TAG, "startDownloads called (wifiOnly=${_wifiOnly.value}, isInForeground=$isInForeground)")
        
        viewModelScope.launch {
            // Block downloads when NOT in foreground to prevent ForegroundServiceStartNotAllowedException
            if (!isInForeground) {
                hasPendingDownloadCheck = true
                Log.w(TAG, "App not in foreground - blocking download, will retry when foregrounded")
                return@launch
            }

            val started = modelDownloadOrchestrator.startDownloads(modelsResult = modelsResult, wifiOnly = _wifiOnly.value)
            if (!started) {
                // Blocked by WiFi-only
                Log.w(TAG, "Downloads blocked, showing WiFi dialog")
                _showWifiDialog.value = true
            } else {
                // IMPORTANT: Re-observe work progress after starting downloads
                // The work ID may have changed (KEEP_POLICY_REPLACE creates new work)
                observeWorkProgress()
            }
        }
    }
    
    /**
     * Called when the app moves to foreground.
     * Used to track foreground state for race condition prevention.
     */
    fun onAppForegrounded() {
        Log.d(TAG, "App foregrounded")
        isInForeground = true
        // Re-trigger download check if there was a pending request
        if (hasPendingDownloadCheck) {
            hasPendingDownloadCheck = false
            viewModelScope.launch {
                checkModels()
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
        viewModelScope.launch {
            modelDownloadOrchestrator.retryFailed()
        }
    }

    /**
     * Override WiFi-only and download on mobile data.
     */
    fun downloadOnMobileData() {
        Log.i(TAG, "User action: download on mobile data (override WiFi-only)")
        _wifiOnly.value = false
        _showWifiDialog.value = false
        viewModelScope.launch {
            modelDownloadOrchestrator.downloadOnMobileData()
        }
    }

    /**
     * Dismiss WiFi dialog.
     */
    fun dismissWifiDialog() {
        Log.d(TAG, "User action: dismiss WiFi dialog")
        _showWifiDialog.value = false
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
        // This is the fix for "Channel was consumed, consumer had failed" error
        workObservationJob?.cancel()
        workObservationJob = viewModelScope.launch {
            // Step 1: Wait for work to be created (handles race condition)
            // The work might not exist yet when init runs - poll until it appears
            var workId = downloadWorkRepository.getWorkId()
            
            // Track previous state for change detection
            var previousState: WorkInfo.State? = null
            
            if (workId == null) {
                Log.d(TAG, "[TRACE] observeWorkProgress: No work ID yet, waiting for work to be created...")
                // Poll for work ID - max 30 seconds (60 x 500ms)
                var attempts = 0
                val maxAttempts = 60
                while (workId == null && attempts < maxAttempts) {
                    delay(500)
                    workId = downloadWorkRepository.getWorkId()
                    attempts++
                }
                
                if (workId == null) {
                    Log.w(TAG, "[TRACE] observeWorkProgress: No work ID found after 30s, giving up")
                    return@launch
                }
            }
            
            Log.d(TAG, "[TRACE] observeWorkProgress: Work ID found: $workId, starting polling")
            
            // Step 2: Use the repository's Flow which handles waiting for work + progress
            downloadWorkRepository.observeDownloadProgress(workId)
                .collect { workInfo ->
                    // Handle null workInfo (work not found or error)
                    if (workInfo == null) {
                        Log.w(TAG, "[TRACE] observeWorkProgress: Work info is null, skipping update")
                        return@collect
                    }

                    // Throttle logging to prevent Logcat spam (every 5 seconds)
                    val currentTime = System.currentTimeMillis()
                    val shouldLog = currentTime - lastTraceLogTime >= THROTTLE_MS

                    // Log state changes
                    if (previousState != workInfo.state) {
                        Log.d(TAG, "[TRACE] observeWorkProgress: State changed ${previousState?.name ?: "NULL"} -> ${workInfo.state.name}")
                        previousState = workInfo.state
                    }

                    if (shouldLog) {
                        lastTraceLogTime = currentTime
                        val progress = workInfo.progress.getLong(DownloadKey.PROGRESS.key, -1)
                        val totalBytes = workInfo.progress.getLong(DownloadKey.TOTAL_BYTES.key, -1)
                        val downloadedBytes = workInfo.progress.getLong(DownloadKey.BYTES_DOWNLOADED.key, -1)
                        Log.d(TAG, "[TRACE] observeWorkProgress: FLOW EMITTED - state=${workInfo.state}, " +
                            "id=${workInfo.id}, progress=$progress, totalBytes=$totalBytes, downloadedBytes=$downloadedBytes")
                    }

                    // Step 3: Update the orchestrator with the latest work progress
                    if (shouldLog) {
                        Log.d(TAG, "[TRACE] observeWorkProgress: Calling updateFromWorkProgress with state=${workInfo.state}")
                    }
                    modelDownloadOrchestrator.updateFromWorkProgress(workInfo)
                }
        }
    }
}
