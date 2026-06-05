package com.browntowndev.enclave.domain.model.download

/**
 * Status of the overall download process.
 */
enum class DownloadStatus {
    IDLE, // No downloads needed, all models present
    CHECKING, // Checking existing files
    DOWNLOADING, // Actively downloading
    FINALIZING, // Bytes on disk — extracting QNN packs and syncing registry
    WIFI_BLOCKED, // Download blocked — WiFi-only is on and device is on metered connection
    READY, // All models ready
    PAUSED, // Downloads paused by user
    ERROR, // Error occurred
}
