package com.browntowndev.pocketcrew.domain.model.download

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Status of an individual file download.
 */
enum class FileStatus {
    QUEUED,      // Waiting to be downloaded
    DOWNLOADING, // Currently downloading
    COMPLETE,    // Successfully downloaded
    FAILED,      // Download failed
    PAUSED       // Paused by user
}

/**
 * Progress information for an individual file.
 */
@Immutable
data class FileProgress(
    val filename: String,
    val modelTypes: List<ModelType> = emptyList(),
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: FileStatus,
    val speedMBs: Double? = null
)
