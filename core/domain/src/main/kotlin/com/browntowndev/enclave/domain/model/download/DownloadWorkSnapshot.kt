package com.browntowndev.enclave.domain.model.download

/** Lifecycle of the unique download work chain, mapped from WorkManager in :core:data. */
enum class DownloadWorkLifecycle {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    CANCELLED,
}

/**
 * Domain snapshot of download work state (ENC-11). Replaces WorkInfo in
 * ViewModels so feature code never touches WorkManager or :core:data types.
 * Chain-stage flags and derived progress are computed by the data adapter.
 */
data class DownloadWorkSnapshot(
    val id: String,
    val lifecycle: DownloadWorkLifecycle,
    val isTerminalSuccess: Boolean,
    val isTerminalFailure: Boolean,
    val isIntermediateDownloadSuccess: Boolean,
    val isFinalizing: Boolean,
    val postProcessProgress: Float?,
    val aggregateProgress: Float?,
    /** FILES_PROGRESS entry for the unassigned (model) file, if present. */
    val unassignedFileProgress: String?,
    val progressPercent: Long = -1L,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = -1L,
)
