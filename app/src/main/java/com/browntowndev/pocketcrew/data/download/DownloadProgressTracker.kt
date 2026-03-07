package com.browntowndev.pocketcrew.data.download

import androidx.work.Data
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.domain.model.DownloadKey
import com.browntowndev.pocketcrew.domain.model.FileProgress
import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.util.formatBytes
import java.util.Locale

/**
 * Data class representing the download state of a single file.
 * This is moved from ModelDownloadWorker to enable centralized progress tracking.
 */
data class FileDownloadState(
    val data: ModelConfiguration,
    val status: FileStatus = FileStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = data.metadata.sizeInBytes,
    val error: String? = null
)

/**
 * Snapshot of overall download progress across all files.
 */
data class ProgressSnapshot(
    val overallProgress: Float,
    val totalBytesDownloaded: Long,
    val totalSize: Long,
    val completedFiles: Int,
    val totalFiles: Int,
    val currentFile: String,
    val currentSpeedMBps: Double,
    val etaSeconds: Long,
    val filesProgress: List<FileProgress>
)

/**
 * Manages download progress tracking for multi-file model downloads.
 * Handles file state management, progress computation, and WorkManager data serialization.
 */
class DownloadProgressTracker(
    private val speedTracker: DownloadSpeedTrackerPort,
    private val formatBytes: (Long) -> String = ::formatBytes
) {
    companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        const val TRACE_LOG_INTERVAL_MS = 5000L
    }

    private var fileStates = mutableMapOf<String, FileDownloadState>()
    private var lastProgressUpdateTime = 0L
    private var lastTraceLogTime = 0L

    /**
     * Initialize tracker with list of model files.
     * Uses remoteFileName as the tracking key.
     */
    fun initialize(models: List<ModelConfiguration>) {
        val initialStates = models.associate { model ->
            val trackingKey = model.metadata.remoteFileName
            trackingKey to FileDownloadState(model)
        }
        fileStates = initialStates.toMutableMap()
    }

    /**
     * Update single file state using an update function.
     * @param filename The tracking key (remoteFileName) for the file
     * @param update Function that transforms the current FileDownloadState
     */
    fun updateFileState(filename: String, update: (FileDownloadState) -> FileDownloadState) {
        val currentState = fileStates[filename]
        val newState = update(currentState ?: FileDownloadState(
            ModelConfiguration(
                modelType = com.browntowndev.pocketcrew.domain.model.ModelType.MAIN,
                metadata = ModelConfiguration.Metadata(
                    huggingFaceModelName = "",
                    remoteFileName = filename,
                    localFileName = filename,
                    displayName = filename,
                    md5 = "",
                    sizeInBytes = 0L,
                    modelFileFormat = ModelFileFormat.LITERTLM
                ),
                tunings = ModelConfiguration.Tunings(),
                persona = ModelConfiguration.Persona(systemPrompt = "")
            )
        ))
        fileStates[filename] = newState
    }

    /**
     * Compute overall progress from all file states.
     * @return ProgressSnapshot containing aggregated progress information
     */
    fun computeOverallProgress(): ProgressSnapshot {
        val totalFiles = fileStates.size
        val completedFiles = fileStates.values.count { it.status == FileStatus.COMPLETE }
        val totalSize = fileStates.values.sumOf { it.totalBytes }
        val totalBytesDownloaded = fileStates.values.sumOf { it.bytesDownloaded }

        val overallProgress = if (totalSize > 0) {
            totalBytesDownloaded.toFloat() / totalSize
        } else {
            completedFiles.toFloat() / totalFiles.coerceAtLeast(1)
        }

        val currentFile = fileStates.values.find { it.status == FileStatus.DOWNLOADING }?.data?.metadata?.remoteFileName ?: ""

        // Calculate AGGREGATE speed for the header (total speed across all files)
        val (currentSpeedMBps, etaSeconds) = speedTracker.calculateAggregateSpeedAndEta(
            totalBytesDownloaded,
            totalSize
        )

        // Calculate PER-FILE speed for each file
        val filesProgress = fileStates.values.map { state ->
            val filename = state.data.metadata.remoteFileName
            val (fileSpeed, _) = if (state.status == FileStatus.DOWNLOADING) {
                speedTracker.calculateSpeedAndEta(
                    filename,
                    state.bytesDownloaded,
                    state.totalBytes
                )
            } else {
                Pair(0.0, -1L)
            }
            FileProgress(
                filename = filename,
                modelTypes = listOf(state.data.modelType),
                bytesDownloaded = state.bytesDownloaded,
                totalBytes = state.totalBytes,
                status = state.status,
                speedMBs = fileSpeed
            )
        }

        return ProgressSnapshot(
            overallProgress = overallProgress,
            totalBytesDownloaded = totalBytesDownloaded,
            totalSize = totalSize,
            completedFiles = completedFiles,
            totalFiles = totalFiles,
            currentFile = currentFile,
            currentSpeedMBps = currentSpeedMBps,
            etaSeconds = etaSeconds,
            filesProgress = filesProgress
        )
    }

    /**
     * Check if enough time has passed since last progress update (throttling).
     * @return true if progress update should be sent
     */
    fun shouldUpdateProgress(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastProgressUpdateTime > PROGRESS_UPDATE_INTERVAL_MS
    }

    /**
     * Check if enough time has passed since last trace log (throttling).
     * @return true if trace log should be sent
     */
    fun shouldLogTrace(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastTraceLogTime > TRACE_LOG_INTERVAL_MS
    }

    /**
     * Mark that progress was updated (update timestamp).
     */
    fun markProgressUpdated() {
        lastProgressUpdateTime = System.currentTimeMillis()
    }

    /**
     * Mark that trace log was written (update timestamp).
     */
    fun markTraceLogged() {
        lastTraceLogTime = System.currentTimeMillis()
    }

    /**
     * Serialize current progress to WorkManager Data for progress reporting.
     * @return Data object ready for WorkManager setProgress()
     */
    fun serializeToWorkData(): Data {
        val snapshot = computeOverallProgress()

        val speedString = if (snapshot.currentSpeedMBps > 0) {
            String.format(Locale.US, "%.1f MB/s", snapshot.currentSpeedMBps)
        } else ""

        val etaString = speedTracker.formatEta(snapshot.etaSeconds)

        val subText = buildString {
            append(formatBytes(snapshot.totalBytesDownloaded))
            append(" / ")
            append(formatBytes(snapshot.totalSize))
            if (speedString.isNotEmpty()) {
                append(" • ")
                append(speedString)
            }
            if (snapshot.etaSeconds >= 0) {
                append(" • ETA: ")
                append(etaString)
            }
        }

        // Create a lookup map for per-file speeds from the computed snapshot
        val filesProgressMap = snapshot.filesProgress.associateBy { it.filename }

        val filesProgressArray = fileStates.values.map { state ->
            val trackingFilename = state.data.metadata.remoteFileName
            val fileSpeed = if (state.status == FileStatus.DOWNLOADING) {
                filesProgressMap[trackingFilename]?.speedMBs ?: 0.0
            } else {
                0.0
            }
            val modelTypesStr = listOf(state.data.modelType).joinToString(",") { it.apiValue }
            "$trackingFilename|${state.bytesDownloaded}|${state.totalBytes}|${state.status}|$fileSpeed|$modelTypesStr"
        }.toTypedArray()

        return workDataOf(
            DownloadKey.OVERALL_PROGRESS.key to snapshot.overallProgress,
            DownloadKey.MODELS_COMPLETE.key to snapshot.completedFiles,
            DownloadKey.MODELS_TOTAL.key to snapshot.totalFiles,
            DownloadKey.TOTAL_BYTES.key to snapshot.totalSize,
            DownloadKey.BYTES_DOWNLOADED.key to snapshot.totalBytesDownloaded,
            DownloadKey.CURRENT_FILE.key to snapshot.currentFile,
            DownloadKey.PROGRESS.key to (snapshot.overallProgress * 100).toInt(),
            DownloadKey.SPEED_MBPS.key to snapshot.currentSpeedMBps,
            DownloadKey.ETA_SECONDS.key to snapshot.etaSeconds,
            DownloadKey.FILES_PROGRESS.key to filesProgressArray
        )
    }

    /**
     * Build subText string for notification display.
     * @return Formatted string like "10.5 MB / 100 MB • 1.2 MB/s • ETA: 1 min"
     */
    fun buildSubText(): String {
        val snapshot = computeOverallProgress()

        val speedString = if (snapshot.currentSpeedMBps > 0) {
            String.format(Locale.US, "%.1f MB/s", snapshot.currentSpeedMBps)
        } else ""

        val etaString = speedTracker.formatEta(snapshot.etaSeconds)

        return buildString {
            append(formatBytes(snapshot.totalBytesDownloaded))
            append(" / ")
            append(formatBytes(snapshot.totalSize))
            if (speedString.isNotEmpty()) {
                append(" • ")
                append(speedString)
            }
            if (snapshot.etaSeconds >= 0) {
                append(" • ETA: ")
                append(etaString)
            }
        }
    }

    /**
     * Get current file states map.
     * @return Map of filename (tracking key) to FileDownloadState
     */
    fun getFileStates(): Map<String, FileDownloadState> = fileStates.toMap()

    /**
     * Get the number of completed files.
     */
    fun getCompletedCount(): Int = fileStates.values.count { it.status == FileStatus.COMPLETE }

    /**
     * Get the total number of files.
     */
    fun getTotalCount(): Int = fileStates.size

    /**
     * Get total size in bytes.
     */
    fun getTotalSize(): Long = fileStates.values.sumOf { it.totalBytes }

    /**
     * Check if all files are completed.
     */
    fun isAllComplete(): Boolean = fileStates.values.all { it.status == FileStatus.COMPLETE }

    /**
     * Get current downloading file name.
     */
    fun getCurrentDownloadingFile(): String? = fileStates.values
        .find { it.status == FileStatus.DOWNLOADING }?.data?.metadata?.remoteFileName
}
