package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate

@Singleton
class WorkProgressParser @Inject constructor(
    private val sessionManager: DownloadSessionManager
) {
    companion object {
        private const val TAG = "WorkProgressParser"
    }

    fun parse(
        workInfo: WorkInfo,
        currentDownloads: List<FileProgress>
    ): DownloadProgressUpdate? {
        // Extract worker stage from output data (available on terminal states)
        // or from progress data (available during running state).
        // Safe to null: legacy single-worker chains don't set worker_stage.
        val workerStage = runCatching {
            workInfo.outputData.getString(DownloadWorkKeys.KEY_WORKER_STAGE)
        }.getOrNull()
            ?: runCatching {
                workInfo.progress.getString(DownloadWorkKeys.KEY_WORKER_STAGE)
            }.getOrNull()

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
                        clearSession = false
                    )
                } else {
                    parseRunning(workInfo, currentDownloads)
                }
            }
            WorkInfo.State.ENQUEUED -> {
                DownloadProgressUpdate(status = DownloadStatus.CHECKING)
            }
            WorkInfo.State.BLOCKED -> {
                DownloadProgressUpdate(
                    status = DownloadStatus.WIFI_BLOCKED,
                    waitingForUnmeteredNetwork = true
                )
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
        currentDownloads: List<FileProgress>
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
            clearSession = false
        )
    }

    private fun parseRunning(workInfo: WorkInfo, currentDownloads: List<FileProgress>): DownloadProgressUpdate {
        val progress = workInfo.progress
        val overallProgress = progress.getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f)
        val modelsComplete = progress.getInt(DownloadKey.MODELS_COMPLETE.key, 0)
        val modelsTotal = progress.getInt(DownloadKey.MODELS_TOTAL.key, currentDownloads.size)
        val speedMBs = progress.getDouble(DownloadKey.SPEED_MBPS.key, 0.0)
        val etaSeconds = progress.getLong(DownloadKey.ETA_SECONDS.key, -1L)

        val filesData = progress.getStringArray(DownloadKey.FILES_PROGRESS.key) ?: emptyArray()

        val parsedFiles = filesData.mapNotNull { file ->
            parseFileProgress(file).also {
                if (it == null) Log.w(TAG, "Failed to parse file progress: $it")
            }
        }

        // DIAGNOSTIC: Log if currentDownloads is empty (this is the likely root cause!)
        if (currentDownloads.isEmpty()) {
            Log.e(TAG, "[DIAGNOSTIC] CRITICAL: currentDownloads is EMPTY! This causes 'Waiting for model configuration...' message. Parsed files will be SKIPPED!")
        }
        val fileProgressList = parsedFiles.mapNotNull { parsed ->
            // Match by filename directly - this is the correct way to find the existing file
            val existing = currentDownloads.firstOrNull { download ->
                download.filename == parsed.filename
            }
            // FIX: Handle empty currentDownloads by using parsed.modelTypes directly
            // This fixes the "Waiting for model configuration..." bug when currentDownloads is empty
            if (existing == null) {
                // No existing entry - use parsed data directly (from worker)
                if (parsed.modelTypes.isNotEmpty()) {
                    Log.w(TAG, "[FIX_APPLIED] parseRunning: Using parsed modelTypes=${parsed.modelTypes} for filename=${parsed.filename} (no existing entry)")
                    parsed
                } else {
                    // Try to derive from filename
                    val derivedTypes = deriveModelTypesFromFilename(parsed.filename)
                    if (derivedTypes.isNotEmpty()) {
                        Log.w(TAG, "[FIX_APPLIED] parseRunning: Derived modelTypes=${derivedTypes} for filename=${parsed.filename}")
                        parsed.copy(modelTypes = derivedTypes)
                    } else {
                        Log.e(TAG, "[ERROR] parseRunning: No modelTypes for filename=${parsed.filename}, falling back to filename")
                        // Return with empty modelTypes - UI will handle this gracefully
                        parsed
                    }
                }
            } else if (existing.modelTypes.isNotEmpty()) {
                // MERGE both sets of modelTypes - union with deduplication
                // parsed.modelTypes is authoritative (from worker), existing from currentDownloads
                val mergedModelTypes = (parsed.modelTypes + existing.modelTypes).distinctBy { it.name }
                val result = parsed.copy(modelTypes = mergedModelTypes)
                result
            } else if (parsed.modelTypes.isNotEmpty()) {
                // Existing has no modelTypes but parsed does - use parsed
                Log.w(TAG, "[FIX_APPLIED] parseRunning: Using parsed modelTypes=${parsed.modelTypes} for filename=${parsed.filename}")
                parsed
            } else {
                // Neither has modelTypes - derive from filename
                val derivedTypes = deriveModelTypesFromFilename(parsed.filename)
                if (derivedTypes.isNotEmpty()) {
                    Log.w(TAG, "[FIX_APPLIED] parseRunning: Derived modelTypes=${derivedTypes} for filename=${parsed.filename}")
                    parsed.copy(modelTypes = derivedTypes)
                } else {
                    Log.e(TAG, "[ERROR] parseRunning: No modelTypes for filename=${parsed.filename}")
                    parsed
                }
            }
        }
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
            errorMessage = null
        )
    }

    private fun parseSucceeded(workInfo: WorkInfo, currentDownloads: List<FileProgress>): DownloadProgressUpdate? {
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
            clearSession = true
        )
    }

    private fun parseFailed(
        workInfo: WorkInfo,
        currentDownloads: List<FileProgress>
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
            clearSession = true
        )
    }

    fun parseFileProgress(data: String): FileProgress? {
        val parts = data.split("|")
        // Check for at least 6 parts (0-5 indices) since we need modelTypes at index 5
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

            // Safely parse modelTypes, handle empty string
            val modelTypesStr = parts.getOrNull(5) ?: ""
            val modelTypes = if (modelTypesStr.isNotBlank()) {
                modelTypesStr.split(",").mapNotNull { typeStr ->
                    try {
                        ModelType.fromApiValue(typeStr.trim())
                    } catch (e: Exception) {
                        Log.w(TAG, "[PARSE_ERROR] Failed to parse modelType: '$typeStr'")
                        null
                    }
                }
            } else {
                Log.w(TAG, "[PARSE_WARN] parseFileProgress: Empty modelTypes for filename=$filename, will derive from filename")
                emptyList()
            }

            if (totalBytes in 1..<bytesDownloaded) return null

            FileProgress(
                filename = filename,
                modelTypes = modelTypes,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                status = status,
                speedMBs = speedMBs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse file progress: $data", e)
            null
        }
    }

    private fun formatEta(seconds: Long): String = when {
        seconds < 60 -> "< 1 min"
        seconds < 3600 -> "${seconds / 60} min"
        else -> String.format(Locale.US, "%.1f hours", seconds / 3600.0)
    }

    /**
     * Derives modelTypes from filename when the backward compatibility merge fails.
     * Filename format: "{modelType}.{extension}" e.g., "vision.litertlm", "main.task"
     */
    private fun deriveModelTypesFromFilename(filename: String): List<ModelType> {
        val baseName = filename.substringBefore(".")
        return try {
            listOf(ModelType.valueOf(baseName.uppercase()))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not derive ModelType from filename: $filename")
            emptyList()
        }
    }
}
