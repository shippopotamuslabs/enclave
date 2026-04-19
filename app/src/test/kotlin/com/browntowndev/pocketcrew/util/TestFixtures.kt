package com.browntowndev.pocketcrew.util

import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

object TestFixtures {
    fun downloadStatus(status: DownloadStatus = DownloadStatus.IDLE): DownloadStatus = status

    fun downloadState(
        status: DownloadStatus = DownloadStatus.IDLE,
        overallProgress: Float = 0f,
        modelsTotal: Int = 0,
        modelsComplete: Int = 0,
        currentDownloads: List<FileProgress> = emptyList(),
        estimatedTimeRemaining: String? = null,
        currentSpeedMBs: Double? = null,
        errorMessage: String? = null,
        waitingForUnmeteredNetwork: Boolean = false,
    ): DownloadState =
        DownloadState(
            status = status,
            overallProgress = overallProgress,
            modelsTotal = modelsTotal,
            modelsComplete = modelsComplete,
            currentDownloads = currentDownloads,
            estimatedTimeRemaining = estimatedTimeRemaining,
            currentSpeedMBs = currentSpeedMBs,
            errorMessage = errorMessage,
            waitingForUnmeteredNetwork = waitingForUnmeteredNetwork,
        )

    fun fileProgress(
        filename: String = "test_model.bin",
        sha256: String = "test_sha256",
        modelTypes: List<ModelType> = listOf(ModelType.MAIN),
        bytesDownloaded: Long = 500_000_000L,
        totalBytes: Long = 1_000_000_000L,
        status: FileStatus = FileStatus.DOWNLOADING,
        speedMBs: Double? = null,
    ): FileProgress =
        FileProgress(
            filename = filename,
            sha256 = sha256,
            modelTypes = modelTypes,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            status = status,
            speedMBs = speedMBs,
        )
}
