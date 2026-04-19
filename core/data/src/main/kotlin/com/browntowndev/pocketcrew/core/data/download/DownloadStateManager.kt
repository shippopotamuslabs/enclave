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
                // Null means no update was provided — preserve existing list.
                // Non-null (including empty list) is an explicit update from the parser.
                currentDownloads = update.currentDownloads ?: it.currentDownloads,
                estimatedTimeRemaining = update.estimatedTimeRemaining ?: it.estimatedTimeRemaining,
                currentSpeedMBs = update.currentSpeedMBs ?: it.currentSpeedMBs,
                waitingForUnmeteredNetwork = update.waitingForUnmeteredNetwork ?: it.waitingForUnmeteredNetwork,
                errorMessage = update.errorMessage
            )
        }
    }

    fun getCurrentState(): DownloadState = stateFlow.value
}
