package com.browntowndev.pocketcrew.domain.port.download

/**
 * Domain abstraction for tracking download speed.
 * This is implemented in the data layer to keep domain pure.
 */
interface DownloadSpeedTrackerPort {
    /**
     * Calculate current speed and estimated time remaining for a single file.
     * @param filename The file being tracked (used for per-file sample isolation)
     * @param bytesDownloaded Current bytes downloaded for this file
     * @param totalSize Total size of this file
     * @return Pair of (speed in MB/s, ETA in seconds)
     */
    fun calculateSpeedAndEta(
        filename: String,
        bytesDownloaded: Long,
        totalSize: Long
    ): Pair<Double, Long>

    /**
     * Calculate aggregate speed across all files.
     * @param totalBytesDownloaded Sum of bytes downloaded across all files
     * @param totalSize Sum of all file sizes
     * @return Pair of (speed in MB/s, ETA in seconds)
     */
    fun calculateAggregateSpeedAndEta(
        totalBytesDownloaded: Long,
        totalSize: Long
    ): Pair<Double, Long>

    /**
     * Format ETA in human-readable format.
     */
    fun formatEta(seconds: Long): String

    /**
     * Clear speed samples for a specific file.
     */
    fun clear(filename: String)

    /**
     * Clear all speed samples (for reset).
     */
    fun clearAll()
}
