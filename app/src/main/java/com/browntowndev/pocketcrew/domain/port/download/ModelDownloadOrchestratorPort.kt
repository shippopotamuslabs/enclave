package com.browntowndev.pocketcrew.domain.port.download

import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import kotlinx.coroutines.flow.StateFlow

interface ModelDownloadOrchestratorPort {
    val downloadState: StateFlow<DownloadState>
    val speedTracker: DownloadSpeedTrackerPort

    /**
     * Called exactly once during app startup after model checking completes.
     * Initializes internal state machine, progress tracking, and status using
     * the pre-computed DownloadModelsResult from cold start.
     */
    fun initializeWithStartupResult(result: DownloadModelsResult)

    /**
     * Start downloads using pre-computed models result (from checkModels).
     * This avoids duplicate scanning.
     */
    suspend fun startDownloads(modelsResult: DownloadModelsResult, wifiOnly: Boolean): Boolean
    
    /**
     * Legacy method for backward compatibility - do a scan and start downloads.
     */
    suspend fun startDownloads(wifiOnly: Boolean): Boolean
    suspend fun updateFromWorkProgress(workInfo: WorkInfo)
    fun pauseDownloads()
    suspend fun resumeDownloads()
    suspend fun cancelDownloads()
    suspend fun retryFailed()
    suspend fun downloadOnMobileData()
    fun setError(message: String)
}
