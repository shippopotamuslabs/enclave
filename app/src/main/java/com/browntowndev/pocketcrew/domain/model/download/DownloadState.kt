package com.browntowndev.pocketcrew.domain.model.download

/**
 * Overall download state exposed via StateFlow.
 */
data class DownloadState(
    val status: DownloadStatus,
    val overallProgress: Float = 0f,              // 0.0-1.0
    val modelsTotal: Int = 0,
    val modelsComplete: Int = 0,
    val currentDownloads: List<FileProgress> = emptyList(),
    val estimatedTimeRemaining: String? = null,   // "23 min", "1.8 hours", "Calculating..."
    val currentSpeedMBs: Double? = null,          // e.g. 12.4
    val errorMessage: String? = null,
    val wifiBlocked: Boolean = false                // True if blocked due to WiFi-only setting
)
