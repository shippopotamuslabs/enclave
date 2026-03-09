package com.browntowndev.pocketcrew.domain.model.download

/**
 * Enum representing all WorkManager Data keys used for download progress.
 * Single source of truth for progress key names to prevent key mismatches.
 */
enum class DownloadKey(val key: String) {
    OVERALL_PROGRESS("overall_progress"),
    MODELS_COMPLETE("models_complete"),
    MODELS_TOTAL("models_total"),
    TOTAL_BYTES("total_bytes"),
    BYTES_DOWNLOADED("bytes_downloaded"),
    CURRENT_FILE("current_file"),
    PROGRESS("progress"),
    SPEED_MBPS("speed_mbps"),
    ETA_SECONDS("eta_seconds"),
    FILES_PROGRESS("files_progress");

    companion object {
        fun fromKey(key: String): DownloadKey? = entries.find { it.key == key }
    }
}
