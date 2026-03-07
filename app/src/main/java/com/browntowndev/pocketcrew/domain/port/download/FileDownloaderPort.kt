package com.browntowndev.pocketcrew.domain.port.download

import com.browntowndev.pocketcrew.domain.model.DownloadWorkerModelFile
import java.io.File

/**
 * Domain abstraction for file download operations.
 * This is implemented in the data layer to keep domain pure.
 */
interface FileDownloaderPort {
    /**
     * Callback interface for download progress updates.
     */
    interface ProgressCallback {
        /**
         * Called periodically during download to report progress.
         *
         * @param bytesDownloaded Number of bytes downloaded so far
         * @param totalBytes Total expected bytes (may be 0 if unknown)
         */
        fun onProgress(bytesDownloaded: Long, totalBytes: Long)
    }

    /**
     * Result of a download operation containing metadata about the download.
     */
    data class DownloadResult(
        val file: File,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isResumed: Boolean
    )

    /**
     * Download a file from the given URL to the target directory.
     * Supports resumable downloads via Range headers.
     *
     * @param model The model file to download
     * @param targetDir The target directory for the download
     * @param existingBytes Bytes already downloaded (for resume support)
     * @param progressCallback Optional callback for progress updates during download
     * @return DownloadResult with download metadata
     */
    suspend fun downloadFile(
        model: DownloadWorkerModelFile,
        targetDir: File,
        existingBytes: Long = 0,
        progressCallback: ProgressCallback? = null
    ): DownloadResult

    /**
     * Get the file size from the server without downloading.
     *
     * @param url The URL to check
     * @return The file size in bytes, or null if unknown
     */
    suspend fun getServerFileSize(url: String): Long?
}
