package com.browntowndev.pocketcrew.domain.model.download

/**
 * Request type for download finalization.
 * Tells DownloadFinalizeWorker which business path to execute after bytes are transferred.
 */
enum class DownloadRequestKind {
    /**
     * Startup model initialization.
     * Finalizer should activate downloaded models into their role slots
     * and run orphan cleanup.
     */
    INITIALIZE_MODELS,

    /**
     * Re-download of a soft-deleted model.
     * Finalizer should restore the model's configurations after bytes are present.
     */
    RESTORE_SOFT_DELETED_MODEL,
}