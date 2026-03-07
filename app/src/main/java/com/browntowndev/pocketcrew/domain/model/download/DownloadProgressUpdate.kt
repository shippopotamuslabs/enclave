package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.FileProgress

data class DownloadProgressUpdate(
    val status: DownloadStatus,
    val overallProgress: Float? = null,
    val modelsComplete: Int? = null,
    val modelsTotal: Int? = null,
    val currentDownloads: List<FileProgress>? = null,
    val estimatedTimeRemaining: String? = null,
    val currentSpeedMBs: Double? = null,
    val wifiBlocked: Boolean? = null,
    val errorMessage: String? = null,
    val clearSession: Boolean = false
)
