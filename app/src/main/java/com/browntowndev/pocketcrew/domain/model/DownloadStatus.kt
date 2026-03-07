package com.browntowndev.pocketcrew.domain.model

/**
 * Status of the overall download process.
 */
enum class DownloadStatus {
    IDLE,       // No downloads needed, all models present
    CHECKING,   // Checking existing files
    DOWNLOADING,// Actively downloading
    READY,      // All models ready
    PAUSED,     // Downloads paused by user
    ERROR       // Error occurred
}
