package com.browntowndev.pocketcrew.data.download

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
        return when (workInfo.state) {
            WorkInfo.State.RUNNING -> parseRunning(workInfo, currentDownloads)
            WorkInfo.State.SUCCEEDED -> parseSucceeded(workInfo, currentDownloads)
            WorkInfo.State.FAILED -> parseFailed(workInfo, currentDownloads)
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                DownloadProgressUpdate(status = DownloadStatus.CHECKING)
            }
            WorkInfo.State.CANCELLED -> {
                DownloadProgressUpdate(status = DownloadStatus.PAUSED)
            }
        }
    }

    private fun parseRunning(workInfo: WorkInfo, currentDownloads: List<FileProgress>): DownloadProgressUpdate {
        val progress = workInfo.progress
        val overallProgress = progress.getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f)
        val modelsComplete = progress.getInt(DownloadKey.MODELS_COMPLETE.key, 0)
        val modelsTotal = progress.getInt(DownloadKey.MODELS_TOTAL.key, currentDownloads.size)
        val speedMBs = progress.getDouble(DownloadKey.SPEED_MBPS.key, 0.0)
        val etaSeconds = progress.getLong(DownloadKey.ETA_SECONDS.key, -1L)

        val filesData = progress.getStringArray(DownloadKey.FILES_PROGRESS.key) ?: emptyArray()

        // DIAGNOSTIC: Log raw files data from work progress
        Log.w(TAG, "[DIAGNOSTIC] parseRunning: raw filesData count=${filesData.size}, data=${filesData.joinToString("; ")}")

        val parsedFiles = filesData.mapNotNull { file ->
            parseFileProgress(file).also {
                if (it == null) Log.w(TAG, "Failed to parse file progress: $it")
            }
        }

        // Merge modelTypes from existing downloads for backward compatibility
        // This handles multi-type models like DRAFT + VISION where one file has multiple filenames
        Log.d(TAG, "[DEBUG] parseRunning: currentDownloads count=${currentDownloads.size}, filenames=${currentDownloads.map { it.filename }}")
        Log.d(TAG, "[DEBUG] parseRunning: parsedFiles count=${parsedFiles.size}, filenames=${parsedFiles.map { it.filename }}")

        // DIAGNOSTIC: Log if currentDownloads is empty (this is the likely root cause!)
        if (currentDownloads.isEmpty()) {
            Log.e(TAG, "[DIAGNOSTIC] CRITICAL: currentDownloads is EMPTY! This causes 'Waiting for model configuration...' message. Parsed files will be SKIPPED!")
        }
        val fileProgressList = parsedFiles.mapNotNull { parsed ->
            // Match by filename directly - this is the correct way to find the existing file
            val existing = currentDownloads.firstOrNull { download ->
                download.filename == parsed.filename
            }
            Log.d(TAG, "[DEBUG] parseRunning: looking up filename=${parsed.filename}, found=${existing != null}, existingModelTypes=${existing?.modelTypes}")

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
                Log.d(TAG, "[DEBUG] parseRunning: MERGED modelTypes=${result.modelTypes} for filename=${parsed.filename}")
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
            wifiBlocked = false,
            errorMessage = null
        )
    }

    private fun parseSucceeded(workInfo: WorkInfo, currentDownloads: List<FileProgress>): DownloadProgressUpdate? {
        val workSessionId = workInfo.outputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)

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
        val workSessionId = workInfo.outputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)

        // Ignore failures from old/stale sessions - these are from previous app runs
        if (sessionManager.isSessionStale(workSessionId)) {
            Log.w(TAG, "Ignoring stale FAILED: workSessionId=$workSessionId")
            return null
        }

        val errorMessage = workInfo.outputData.getString("error_message") ?: "Download failed"
        return DownloadProgressUpdate(
            status = DownloadStatus.ERROR,
            errorMessage = errorMessage,
            currentDownloads = currentDownloads
        )
    }

    internal fun parseFileProgress(data: String): FileProgress? {
        val parts = data.split("|")
        // FIX: Check for at least 6 parts (0-5 indices) since we need modelTypes at index 5
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

            // FIX: Safely parse modelTypes, handle empty string
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

            Log.d(TAG, "[DEBUG] parseFileProgress: filename=$filename, bytes=$bytesDownloaded, total=$totalBytes, status=$status, modelTypes=$modelTypes")

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
