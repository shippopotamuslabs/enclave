package com.browntowndev.pocketcrew.core.data.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.usecase.download.FileProgressInitResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class DownloadStateManager(
    private val stateFlow: MutableStateFlow<DownloadState>
) {
    fun updateStatus(status: DownloadStatus) {
        stateFlow.update { it.copy(status = status) }
    }

    fun updateState(transform: DownloadState.() -> DownloadState) {
        stateFlow.update { it.transform() }
    }

    fun applyProgressInit(result: FileProgressInitResult) {
        stateFlow.update {
            it.copy(
                currentDownloads = result.fileProgressList,
                modelsTotal = result.modelsTotal,
                modelsComplete = result.modelsComplete,
                overallProgress = result.overallProgress
            )
        }
    }

    fun applyProgressUpdate(update: DownloadProgressUpdate) {
        stateFlow.update {
            it.copy(
                status = update.status,
                overallProgress = update.overallProgress ?: it.overallProgress,
                modelsComplete = update.modelsComplete ?: it.modelsComplete,
                modelsTotal = update.modelsTotal ?: it.modelsTotal,
                // FIX: Preserve existing currentDownloads when update returns empty list
                // This prevents UI flicker when transitioning from waiting to downloading
                // Empty list means parser couldn't find currentDownloads but has parsed data
                // Null means no update was provided, empty list means explicit clear (which we ignore)
                currentDownloads = run {
                    val updateDownloads = update.currentDownloads
                    if (updateDownloads.isNullOrEmpty()) {
                        // Only preserve if we actually have existing downloads
                        // and the update is explicitly trying to clear (empty list, not null)
                        if (updateDownloads != null && it.currentDownloads.isNotEmpty()) {
                            it.currentDownloads
                        } else {
                            updateDownloads ?: it.currentDownloads
                        }
                    } else {
                        updateDownloads
                    }
                },
                estimatedTimeRemaining = update.estimatedTimeRemaining ?: it.estimatedTimeRemaining,
                currentSpeedMBs = update.currentSpeedMBs ?: it.currentSpeedMBs,
                wifiBlocked = update.wifiBlocked ?: it.wifiBlocked,
                errorMessage = update.errorMessage
            )
        }
    }

    fun getCurrentState(): DownloadState = stateFlow.value
}
