package com.browntowndev.enclave.core.data.download

import androidx.work.WorkInfo
import com.browntowndev.enclave.domain.model.download.DownloadKey

/**
 * Shared chain-aware helpers for interpreting WorkManager state in a two-worker
 * download → finalize pipeline.
 */
object DownloadWorkChainObserver {
    fun workerStage(workInfo: WorkInfo): String? {
        val tagStage =
            when {
                workInfo.tags.any { it == ModelDownloadWorker::class.java.name } -> DownloadWorkKeys.STAGE_DOWNLOAD
                workInfo.tags.any { it == DownloadFinalizeWorker::class.java.name } -> DownloadWorkKeys.STAGE_FINALIZE
                else -> null
            }
        return tagStage
            ?: runCatching { workInfo.outputData.getString(DownloadWorkKeys.KEY_WORKER_STAGE) }.getOrNull()
            ?: runCatching { workInfo.progress.getString(DownloadWorkKeys.KEY_WORKER_STAGE) }.getOrNull()
    }

    fun isIntermediateDownloadSuccess(workInfo: WorkInfo): Boolean =
        workInfo.state == WorkInfo.State.SUCCEEDED &&
            workerStage(workInfo) == DownloadWorkKeys.STAGE_DOWNLOAD

    fun isTerminalSuccess(workInfo: WorkInfo): Boolean =
        workInfo.state == WorkInfo.State.SUCCEEDED &&
            workerStage(workInfo) != DownloadWorkKeys.STAGE_DOWNLOAD

    fun isTerminalFailure(workInfo: WorkInfo): Boolean =
        workInfo.state == WorkInfo.State.FAILED ||
            workInfo.state == WorkInfo.State.CANCELLED

    fun isFinalizing(workInfo: WorkInfo): Boolean {
        val stage = workerStage(workInfo)
        return when (workInfo.state) {
            WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED, WorkInfo.State.ENQUEUED ->
                stage == DownloadWorkKeys.STAGE_FINALIZE
            WorkInfo.State.SUCCEEDED ->
                stage == DownloadWorkKeys.STAGE_DOWNLOAD
            else -> false
        }
    }

    fun readPostProcessProgress(workInfo: WorkInfo): Float? {
        val progress = workInfo.progress
        val totalBytes = progress.getLong(DownloadKey.POST_PROCESS_TOTAL.key, 0L)
        if (totalBytes <= 0L) {
            return null
        }
        val extractedBytes = progress.getLong(DownloadKey.POST_PROCESS_BYTES.key, 0L).coerceAtLeast(0L)
        return (extractedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun readDownloadAggregateProgress(workInfo: WorkInfo): Float? {
        val filesProgress = workInfo.progress.getStringArray(DownloadKey.FILES_PROGRESS.key) ?: return null
        val fractions =
            filesProgress.mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size < 3) {
                    return@mapNotNull null
                }
                val downloaded = parts[1].toLongOrNull() ?: 0L
                val total = parts[2].toLongOrNull() ?: 1L
                if (total > 0) downloaded.toFloat() / total else null
            }
        if (fractions.isEmpty()) {
            return null
        }
        return fractions.sum() / fractions.size
    }
}
