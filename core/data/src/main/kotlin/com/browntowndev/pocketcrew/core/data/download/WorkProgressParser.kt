package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.util.Clock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkProgressParser
    @Inject
    constructor(
        private val sessionManager: DownloadSessionManager,
        private val clock: Clock,
    ) {
        companion object {
            private const val TAG = "WorkProgressParser"
            private const val WIFI_BLOCKED_THRESHOLD_MS = 5_000L
        }

        // Tracks when the downloader was first observed in ENQUEUED state.
        // Reset whenever the state transitions away from ENQUEUED.
        private var firstEnqueuedTime: Long? = null

        fun parse(
            workInfo: WorkInfo,
            currentDownloads: List<FileProgress>,
        ): DownloadProgressUpdate? {
            // Reset the ENQUEUED debounce timer whenever the worker leaves ENQUEUED state.
            if (workInfo.state != WorkInfo.State.ENQUEUED) {
                firstEnqueuedTime = null
            }

            // Derive worker stage: tags (always set at enqueue) take priority over
            // output/progress data (only set after the worker runs).
            val tagStage =
                try {
                    when {
                        workInfo.tags.any { it == ModelDownloadWorker::class.java.name } -> DownloadWorkKeys.STAGE_DOWNLOAD
                        workInfo.tags.any { it == DownloadFinalizeWorker::class.java.name } -> DownloadWorkKeys.STAGE_FINALIZE
                        else -> null
                    }
                } catch (_: Exception) {
                    null
                }

            val workerStage =
                tagStage
                    ?: runCatching { workInfo.outputData.getString(DownloadWorkKeys.KEY_WORKER_STAGE) }.getOrNull()
                    ?: runCatching { workInfo.progress.getString(DownloadWorkKeys.KEY_WORKER_STAGE) }.getOrNull()

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    // In a two-worker chain, only FINALIZE success is terminal
                    if (workerStage == DownloadWorkKeys.STAGE_DOWNLOAD) {
                        return parseSucceededDownload(workInfo, currentDownloads)
                    }
                    // FINALIZE success (or legacy single-worker) is terminal
                    return parseSucceeded(workInfo, currentDownloads)
                }
                WorkInfo.State.FAILED -> {
                    // Both download and finalize failures are terminal
                    return parseFailed(workInfo, currentDownloads)
                }
                else -> { /* handled below */ }
            }

            return when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    // Finalizer running means bytes are done, just finalizing registry
                    if (workerStage == DownloadWorkKeys.STAGE_FINALIZE) {
                        DownloadProgressUpdate(
                            status = DownloadStatus.DOWNLOADING,
                            overallProgress = 1.0f,
                            modelsComplete = currentDownloads.size,
                            modelsTotal = currentDownloads.size,
                            currentDownloads = currentDownloads,
                            clearSession = false,
                        )
                    } else {
                        parseRunning(workInfo, currentDownloads)
                    }
                }
                WorkInfo.State.ENQUEUED -> parseEnqueued(workInfo, workerStage)
                WorkInfo.State.BLOCKED -> {
                    // BLOCKED = chain prerequisite not satisfied (always the finalizer waiting on the
                    // downloader, never a network-constraint issue). Return null so the UI preserves
                    // its current DOWNLOADING state instead of flipping to "Waiting for Wi-Fi".
                    Log.d(TAG, "BLOCKED state (chain dependency) — preserving current UI state")
                    null
                }
                WorkInfo.State.CANCELLED -> {
                    DownloadProgressUpdate(status = DownloadStatus.PAUSED)
                }
                else -> null
            }
        }

        /**
         * SUCCEEDED download worker is intermediate - not terminal in a two-worker chain.
         * Return null so the UI waits for the finalizer to complete.
         */
        private fun parseSucceededDownload(
            workInfo: WorkInfo,
            currentDownloads: List<FileProgress>,
        ): DownloadProgressUpdate? {
            val workSessionId = workInfo.outputData.getString(DownloadWorkKeys.KEY_SESSION_ID)
            if (sessionManager.isSessionStale(workSessionId)) {
                Log.w(TAG, "Ignoring stale SUCCEEDED download worker: sessionId=$workSessionId")
                return null
            }
            // Download worker succeeded but finalizer hasn't run yet.
            // Preserve current progress but don't emit READY.
            Log.d(TAG, "Download worker SUCCEEDED (intermediate) – waiting for finalizer, sessionId=$workSessionId")
            return DownloadProgressUpdate(
                status = DownloadStatus.DOWNLOADING,
                overallProgress = 1.0f,
                modelsComplete = currentDownloads.size,
                modelsTotal = currentDownloads.size,
                currentDownloads = currentDownloads,
                clearSession = false,
            )
        }

        private fun parseRunning(
            workInfo: WorkInfo,
            currentDownloads: List<FileProgress>,
        ): DownloadProgressUpdate {
            val progress = workInfo.progress
            val overallProgress = progress.getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f)
            val modelsComplete = progress.getInt(DownloadKey.MODELS_COMPLETE.key, 0)
            val modelsTotal = progress.getInt(DownloadKey.MODELS_TOTAL.key, currentDownloads.size)
            val speedMBs = progress.getDouble(DownloadKey.SPEED_MBPS.key, 0.0)
            val etaSeconds = progress.getLong(DownloadKey.ETA_SECONDS.key, -1L)

            val filesData = progress.getStringArray(DownloadKey.FILES_PROGRESS.key)

            // If FILES_PROGRESS key is not set at all, pass null for currentDownloads
            // so applyProgressUpdate preserves the existing list via null-coalescing.
            // Only pass a list when the worker has explicitly set file progress data.
            if (filesData == null) {
                val etaString = if (etaSeconds > 0) formatEta(etaSeconds) else null
                return DownloadProgressUpdate(
                    status = DownloadStatus.DOWNLOADING,
                    overallProgress = overallProgress,
                    modelsComplete = modelsComplete,
                    modelsTotal = modelsTotal,
                    currentDownloads = null,
                    estimatedTimeRemaining = etaString,
                    currentSpeedMBs = speedMBs.takeIf { it > 0 },
                    waitingForUnmeteredNetwork = false,
                    errorMessage = null,
                )
            }

            val parsedFiles =
                filesData.mapNotNull { file ->
                    parseFileProgress(file).also {
                        if (it == null) Log.w(TAG, "Failed to parse file progress: $it")
                    }
                }

            val fileProgressList =
                parsedFiles
                    .map { parsed ->
                        // Match by SHA256 to preserve role mapping (ModelTypes) from the orchestrator
                        val existing =
                            currentDownloads.firstOrNull { download ->
                                download.sha256 == parsed.sha256
                            }
                        if (existing != null) {
                            parsed.copy(modelTypes = existing.modelTypes)
                        } else {
                            Log.w(TAG, "No existing FileProgress found for SHA256: ${parsed.sha256}. UI roles may be missing.")
                            parsed
                        }
                    }.sortedBy { it.filename }
            val etaString = if (etaSeconds > 0) formatEta(etaSeconds) else null

            return DownloadProgressUpdate(
                status = DownloadStatus.DOWNLOADING,
                overallProgress = overallProgress,
                modelsComplete = modelsComplete,
                modelsTotal = modelsTotal,
                currentDownloads = fileProgressList,
                estimatedTimeRemaining = etaString,
                currentSpeedMBs = speedMBs.takeIf { it > 0 },
                waitingForUnmeteredNetwork = false,
                errorMessage = null,
            )
        }

        private fun parseSucceeded(
            workInfo: WorkInfo,
            currentDownloads: List<FileProgress>,
        ): DownloadProgressUpdate? {
            val workSessionId = workInfo.outputData.getString(DownloadWorkKeys.KEY_SESSION_ID)

            if (sessionManager.isSessionStale(workSessionId)) {
                Log.w(TAG, "Ignoring stale SUCCEEDED: workSessionId=$workSessionId")
                return null
            }

            // SHA-256 validation is now done during streaming download in HttpFileDownloader.
            // If the work succeeded, we can assume files are valid.
            // Calculate final progress - all models complete
            val totalModels = currentDownloads.size
            return DownloadProgressUpdate(
                status = DownloadStatus.READY,
                overallProgress = 1.0f,
                modelsComplete = totalModels,
                modelsTotal = totalModels,
                currentDownloads = currentDownloads,
                clearSession = true,
            )
        }

        private fun parseFailed(
            workInfo: WorkInfo,
            currentDownloads: List<FileProgress>,
        ): DownloadProgressUpdate? {
            val workSessionId = workInfo.outputData.getString(DownloadWorkKeys.KEY_SESSION_ID)

            // Ignore failures from old/stale sessions - these are from previous app runs
            if (sessionManager.isSessionStale(workSessionId)) {
                Log.w(TAG, "Ignoring stale FAILED: workSessionId=$workSessionId")
                return null
            }

            val errorMessage = workInfo.outputData.getString(DownloadWorkKeys.KEY_ERROR_MESSAGE) ?: "Download failed"
            return DownloadProgressUpdate(
                status = DownloadStatus.ERROR,
                errorMessage = errorMessage,
                currentDownloads = currentDownloads,
                clearSession = true,
            )
        }

        fun parseFileProgress(data: String): FileProgress? {
            val parts = data.split("|")
            // Check for at least 6 parts (0-5 indices) since we need sha256 at index 5
            if (parts.size < 6) {
                Log.w(TAG, "[PARSE_ERROR] parseFileProgress: Not enough parts (need 6, got ${parts.size}): $data")
                return null
            }

            return try {
                val filename = parts[0]
                val bytesDownloaded = parts[1].toLong().coerceAtLeast(0L)
                val totalBytes = parts[2].toLong().coerceAtLeast(0L)
                val status = FileStatus.valueOf(parts[3])
                val speedMBs = parts[4].toDoubleOrNull()?.coerceAtLeast(0.0)
                val sha256 = parts[5]

                if (totalBytes in 1..<bytesDownloaded) return null

                FileProgress(
                    filename = filename,
                    sha256 = sha256,
                    modelTypes = emptyList(), // Will be merged from currentDownloads using sha256
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    status = status,
                    speedMBs = speedMBs,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse file progress: $data", e)
                null
            }
        }

        /**
         * Returns WIFI_BLOCKED when the downloader has been ENQUEUED with an UNMETERED constraint
         * for longer than the debounce threshold, indicating the network constraint is genuinely unmet.
         * Returns CHECKING within the debounce window to avoid false positives during cold-start.
         */
        private fun parseEnqueued(
            workInfo: WorkInfo,
            workerStage: String?,
        ): DownloadProgressUpdate {
            if (workerStage != DownloadWorkKeys.STAGE_DOWNLOAD) {
                return DownloadProgressUpdate(status = DownloadStatus.CHECKING)
            }
            val now = clock.currentTimeMillis()
            if (firstEnqueuedTime == null) firstEnqueuedTime = now
            val elapsed = now - firstEnqueuedTime!!
            val requiresUnmetered =
                try {
                    workInfo.constraints.requiredNetworkType == NetworkType.UNMETERED
                } catch (_: Exception) {
                    false
                }
            return if (requiresUnmetered && elapsed >= WIFI_BLOCKED_THRESHOLD_MS) {
                DownloadProgressUpdate(status = DownloadStatus.WIFI_BLOCKED, waitingForUnmeteredNetwork = true)
            } else {
                DownloadProgressUpdate(status = DownloadStatus.CHECKING)
            }
        }

        private fun formatEta(seconds: Long): String =
            when {
                seconds < 60 -> "< 1 min"
                seconds < 3600 -> "${seconds / 60} min"
                else -> String.format(Locale.US, "%.1f hours", seconds / 3600.0)
            }
    }
