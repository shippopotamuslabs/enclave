package com.browntowndev.pocketcrew.domain.model.download

/**
 * Status of the overall download process.
 */
enum class DownloadStatus {
    IDLE,        // No downloads needed, all models present
    CHECKING,    // Checking existing files
    DOWNLOADING, // Actively downloading
    WIFI_BLOCKED, // Download blocked — WiFi-only is on and device is on metered connection
    READY,       // All models ready
    PAUSED,      // Downloads paused by user
    ERROR        // Error occurred
}
