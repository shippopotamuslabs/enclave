package com.browntowndev.pocketcrew.core.data.download

/**
 * Shared WorkManager data keys used by [DownloadWorkScheduler],
 * [ModelDownloadWorker], and [DownloadFinalizeWorker].
 *
 * Defined in a single place to avoid key duplication and risk of divergence.
 */
object DownloadWorkKeys {
    const val KEY_SESSION_ID = "work_session_id"
    const val KEY_REQUEST_KIND = "request_kind"
    const val KEY_TARGET_MODEL_ID = "target_model_id"
    const val KEY_DOWNLOAD_FILES = "download_files"
    const val KEY_WORKER_STAGE = "worker_stage"
    const val KEY_DOWNLOADED_SHAS = "downloaded_shas"
    const val KEY_ERROR_MESSAGE = "error_message"

    // Worker stages — values used in output/progress data
    const val STAGE_DOWNLOAD = "DOWNLOAD"
    const val STAGE_FINALIZE = "FINALIZE"
}