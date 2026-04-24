package com.browntowndev.pocketcrew.core.data.download

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.annotation.Keep
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.domain.model.download.DownloadArtifact
import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.download.requiredArtifacts
import com.browntowndev.pocketcrew.domain.model.download.totalArtifactSizeInBytes
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.core.data.download.remote.DownloadSecurity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * WorkManager worker for downloading model files.
 * Delegates to extracted components for notification handling and progress tracking.
 * SHA-256 integrity validation is performed during streaming download in HttpFileDownloader.
 *
 * Operates entirely on [DownloadFileSpec] — does not know about model types,
 * configurations, or repository state. Business logic is handled by
 * [DownloadFinalizeWorker] after bytes are verified on disk.
 */
@Keep
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val logger: LoggingPort,
    private val notificationManager: DownloadNotificationManager,
    private val progressTracker: DownloadProgressTracker,
    private val fileDownloader: FileDownloaderPort,
    private val modelUrlProvider: ModelUrlProviderPort
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ModelDownloadWorker"

        // Worker stage constant (shared keys are in DownloadWorkKeys)
        const val STAGE_DOWNLOAD = DownloadWorkKeys.STAGE_DOWNLOAD
    }

    override suspend fun doWork(): Result {
        // Create notification channel using extracted component
        notificationManager.createNotificationChannel()

        // Set as foreground service with progress notification
        try {
            val cancelIntent = WorkManager.getInstance(applicationContext)
                .createCancelPendingIntent(id)
            val foregroundInfo = notificationManager.createForegroundInfo(cancelIntent)
            setForeground(foregroundInfo)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            logger.warning(TAG, "Foreground not allowed (app not in foreground), returning retry: ${e.message}")
            return Result.retry()
        }

        // Parse input data — sessionId is required; fail immediately if missing
        val requestKind = inputData.getString(DownloadWorkKeys.KEY_REQUEST_KIND) ?: ""
        val sessionId = inputData.getString(DownloadWorkKeys.KEY_SESSION_ID)
            ?: return failWithError("Missing required input: $DownloadWorkKeys.KEY_SESSION_ID")
        val targetModelId = inputData.getString(DownloadWorkKeys.KEY_TARGET_MODEL_ID)

        val specs = parseDownloadSpecs()
        if (specs == null) {
            logger.error(TAG, "No download files specified (key $DownloadWorkKeys.KEY_DOWNLOAD_FILES not found)")
            return Result.failure(workDataOf(DownloadWorkKeys.KEY_ERROR_MESSAGE to "No files specified"))
        }

        if (specs.isEmpty()) {
            logger.warning(TAG, "No valid file specs to download")
            return Result.success(
                workDataOf(
                    DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                    DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                    DownloadWorkKeys.KEY_WORKER_STAGE to STAGE_DOWNLOAD,
                    DownloadWorkKeys.KEY_DOWNLOADED_SHAS to "",
                    DownloadWorkKeys.KEY_TARGET_MODEL_ID to (targetModelId ?: "")
                )
            )
        }

        val uniqueSpecs = specs.distinctBy { it.sha256 }
        logger.info(TAG, "Starting download of ${uniqueSpecs.size} unique file specs: ${uniqueSpecs.joinToString { it.remoteFileName }}")

        // Initialize progress tracker with spec list (deduplicates by SHA256 internally)
        progressTracker.initialize(uniqueSpecs)

        val totalSize = uniqueSpecs.sumOf { it.totalArtifactSizeInBytes() }
        val totalFiles = uniqueSpecs.size

        return coroutineScope {
            // Channel for conflated progress updates to avoid overwhelming WorkManager's SQLite
            val progressChannel = Channel<Unit>(Channel.CONFLATED)

            // Collector coroutine to handle progress updates sequentially
            val progressCollector = launch {
                for (signal in progressChannel) {
                    try {
                        setProgress(progressTracker.serializeToWorkData())
                        updateNotificationForeground()
                        progressTracker.markProgressUpdated()
                    } catch (e: Exception) {
                        logger.warning(TAG, "Failed to update progress: ${e.message}")
                    }
                }
            }

            try {
                val completedCount = AtomicInteger(0)
                val failedCount = AtomicInteger(0)

                val downloadedShas = uniqueSpecs.map { spec ->
                    async {
                        try {
                            val downloadResult = downloadSpec(spec, progressChannel)
                            if (downloadResult is Result.Success) {
                                spec.sha256
                            } else {
                                null
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failedCount.incrementAndGet()
                            logger.error(TAG, "Failed to download ${spec.localFileName}: ${e.message}", e)

                            val errorMessage = when (e) {
                                is SocketException -> "Connection reset: ${e.message}"
                                is SocketTimeoutException -> "Download timed out: ${e.message}"
                                else -> e.message
                            }
                            progressTracker.updateFileState(spec.sha256) {
                                it.copy(status = FileStatus.FAILED, error = errorMessage)
                            }

                            if (e is SocketException) {
                                delay(500)
                            }
                            null
                        } finally {
                            completedCount.incrementAndGet()
                            updateOverallProgress(progressChannel)
                            logger.debug(TAG, "Finished processing ${spec.localFileName}")
                        }
                    }
                }.awaitAll().filterNotNull()

                // Final result building
                if (failedCount.get() > 0) {
                    val currentAttempt = runAttemptCount
                    if (currentAttempt >= ModelConfig.MAX_RETRY_ATTEMPTS) {
                        logger.error(TAG, "Max retry attempts ($currentAttempt) exceeded, failing permanently")
                        Result.failure(
                            workDataOf(
                                DownloadWorkKeys.KEY_ERROR_MESSAGE to "Download failed after $currentAttempt attempts",
                                DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                                DownloadWorkKeys.KEY_WORKER_STAGE to STAGE_DOWNLOAD,
                                DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                                DownloadWorkKeys.KEY_TARGET_MODEL_ID to (targetModelId ?: "")
                            )
                        )
                    } else {
                        logger.warning(TAG, "Download completed with ${failedCount.get()} failed out of ${uniqueSpecs.size}, will retry (attempt $currentAttempt)")
                        Result.retry()
                    }
                } else {
                    logger.info(TAG, "All ${uniqueSpecs.size} unique file specs downloaded successfully")

                    // Build output data with chain metadata
                    val shasJson = Json.encodeToString(downloadedShas)
                    Result.success(
                        workDataOf(
                            DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                            DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                            DownloadWorkKeys.KEY_WORKER_STAGE to STAGE_DOWNLOAD,
                            DownloadWorkKeys.KEY_DOWNLOADED_SHAS to shasJson,
                            DownloadWorkKeys.KEY_TARGET_MODEL_ID to (targetModelId ?: "")
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "Download error: ${e.message}", e)
                Result.retry()
            } finally {
                // Ensure all progress updates are flushed before finishing
                progressChannel.close()
                progressCollector.join()
            }
        }
    }

    /**
     * Parses the DownloadWorkKeys.KEY_DOWNLOAD_FILES input data into a list of DownloadFileSpec objects.
     */
    private fun parseDownloadSpecs(): List<DownloadFileSpec>? {
        val filesJson = inputData.getString(DownloadWorkKeys.KEY_DOWNLOAD_FILES) ?: return null
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<DownloadFileSpec>>(filesJson)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to parse download files JSON: ${e.message}", e)
            null
        }
    }

    /**
     * Downloads all artifacts (primary file + optional mmproj) for a single DownloadFileSpec.
     */
    private suspend fun downloadSpec(
        spec: DownloadFileSpec,
        progressChannel: Channel<Unit>
    ): Result {
        val targetDir = File(applicationContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        if (!targetDir.exists()) targetDir.mkdirs()

        val trackingKey = spec.sha256
        val artifacts = spec.requiredArtifacts()
        val totalExpectedBytes = spec.totalArtifactSizeInBytes()

        // Check if all artifacts already exist
        val allArtifactsPresent = artifacts.all { artifact ->
            val targetFile = DownloadSecurity.resolveDownloadPaths(targetDir, artifact.localFileName).targetFile
            targetFile.exists() && targetFile.length() == artifact.sizeInBytes
        }
        if (allArtifactsPresent) {
            progressTracker.updateFileState(trackingKey) {
                it.copy(
                    status = FileStatus.COMPLETE,
                    bytesDownloaded = totalExpectedBytes,
                    totalBytes = totalExpectedBytes
                )
            }
            // Force an immediate progress update for already-complete files
            try {
                setProgress(progressTracker.serializeToWorkData())
            } catch (e: Exception) {
                logger.warning(TAG, "Failed to update progress for pre-existing file: ${e.message}")
            }
            // Trigger final progress update for this spec
            progressChannel.trySend(Unit)

            val sessionId = requireNotNull(inputData.getString(DownloadWorkKeys.KEY_SESSION_ID)) {
                "sessionId was null. This should be set when download begins."
            }
            return Result.success(workDataOf(DownloadWorkKeys.KEY_SESSION_ID to sessionId))
        }

        progressTracker.updateFileState(trackingKey) {
            it.copy(status = FileStatus.DOWNLOADING)
        }

        // Initial progress update for this file
        progressChannel.trySend(Unit)

        try {
            var completedBytes = 0L
            artifacts.forEach { artifact ->
                val downloadUrl = modelUrlProvider.getModelDownloadUrl(
                    specWithArtifactName(spec, artifact)
                )
                logger.info(
                    TAG,
                    "[DOWNLOAD] Starting download: url=$downloadUrl, remoteFileName=${artifact.remoteFileName}, localFileName=${artifact.localFileName}"
                )

                val paths = DownloadSecurity.resolveDownloadPaths(targetDir, artifact.localFileName)
                val targetFile = paths.targetFile
                if (targetFile.exists() && targetFile.length() == artifact.sizeInBytes) {
                    completedBytes += artifact.sizeInBytes
                    return@forEach
                }

                val tempFile = paths.tempFile
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                val downloadConfig = FileDownloaderPort.FileDownloadConfig(
                    filename = artifact.localFileName,
                    expectedSha256 = artifact.sha256,
                    expectedSizeBytes = artifact.sizeInBytes
                )

                val downloadResult = fileDownloader.downloadFile(
                    config = downloadConfig,
                    downloadUrl = downloadUrl,
                    targetDir = targetDir,
                    existingBytes = existingBytes,
                    progressCallback = object : FileDownloaderPort.ProgressCallback {
                        override fun onProgress(bytesDownloaded: Long, totalBytes: Long) {
                            progressTracker.updateFileState(trackingKey) {
                                it.copy(
                                    bytesDownloaded = completedBytes + bytesDownloaded,
                                    totalBytes = totalExpectedBytes.coerceAtLeast(completedBytes + totalBytes),
                                    status = FileStatus.DOWNLOADING
                                )
                            }

                            if (progressTracker.shouldUpdateProgress()) {
                                progressChannel.trySend(Unit)
                            }
                        }
                    }
                )

                completedBytes += downloadResult.totalBytes
            }

            progressTracker.updateFileState(trackingKey) {
                it.copy(
                    status = FileStatus.COMPLETE,
                    bytesDownloaded = totalExpectedBytes,
                    totalBytes = totalExpectedBytes
                )
            }

            // Force an immediate progress update when a file completes
            // to ensure the UI shows the checkmark without waiting for throttle
            try {
                setProgress(progressTracker.serializeToWorkData())
            } catch (e: Exception) {
                logger.warning(TAG, "Failed to update progress on completion: ${e.message}")
            }
            progressChannel.trySend(Unit)

            val sessionId = requireNotNull(inputData.getString(DownloadWorkKeys.KEY_SESSION_ID)) {
                "sessionId was null. This should be set when download begins."
            }
            return Result.success(workDataOf(DownloadWorkKeys.KEY_SESSION_ID to sessionId))
        } catch (e: Exception) {
            logger.error(TAG, "Download failed for spec ${spec.localFileName}: ${e.message}")
            throw e
        }
    }

    /**
     * Creates a copy of the spec with the artifact's file names substituted,
     * so the URL provider can construct the correct URL for each artifact.
     *
     * NOTE: This intentionally only overrides remoteFileName and localFileName.
     * SHA and size fields remain from the primary spec because this copy is
     * used exclusively for URL construction via [ModelUrlProviderPort]. It must
     * NOT be used for integrity checks (SHA verification) or size validation.
     */
    private fun specWithArtifactName(spec: DownloadFileSpec, artifact: DownloadArtifact): DownloadFileSpec {
        return spec.copy(
            remoteFileName = artifact.remoteFileName,
            localFileName = artifact.localFileName,
        )
    }

    private suspend fun updateOverallProgress(progressChannel: Channel<Unit>) {
        if (progressTracker.shouldUpdateProgress()) {
            progressChannel.trySend(Unit)
        } else {
            // Always update notification regardless of throttling for the background status
            updateNotificationForeground()
        }
    }

    /**
     * Returns a failure result with error metadata.
     */
    private fun failWithError(errorMessage: String): Result {
        return Result.failure(
            workDataOf(
                DownloadWorkKeys.KEY_ERROR_MESSAGE to errorMessage,
                DownloadWorkKeys.KEY_WORKER_STAGE to STAGE_DOWNLOAD
            )
        )
    }

    /**
     * Updates the foreground notification with current progress.
     * Must use setForegroundAsync() to update a notification that was initially set with setForeground().
     */
    private suspend fun updateNotificationForeground() {
        val snapshot = progressTracker.computeOverallProgress()
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val foregroundInfo = notificationManager.createForegroundInfoForProgress(
            progress = snapshot.overallProgress,
            currentFile = snapshot.currentFile,
            subText = progressTracker.buildSubText(),
            cancelPendingIntent = cancelIntent
        )
        setForegroundAsync(foregroundInfo)
    }
}