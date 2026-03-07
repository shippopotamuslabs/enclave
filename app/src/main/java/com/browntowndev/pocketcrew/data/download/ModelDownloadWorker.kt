package com.browntowndev.pocketcrew.data.download

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.domain.model.DownloadWorkerModelFile
import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.service.FileIntegrityValidator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * WorkManager worker for downloading model files.
 * Delegates to extracted components for notification handling and progress tracking.
 * File integrity validation is centralized in FileIntegrityValidator.verifyModelsExist().
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fileIntegrityValidator: FileIntegrityValidator,
    private val logger: LoggingPort,
    private val notificationManager: DownloadNotificationManager,
    private val progressTracker: DownloadProgressTracker,
    private val fileDownloader: FileDownloaderPort
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ModelDownloadWorker"
        // Counter for tracking pending setProgress calls that must complete before worker finishes
        private val pendingProgressUpdates = AtomicLong(0)
    }

    /**
     * Wait for all pending setProgress() calls to complete.
     * This ensures the progress is persisted to WorkManager's database before
     * the worker returns a Result.
     */
    private suspend fun waitForPendingProgressUpdates() {
        // Wait for any in-flight progress updates to complete
        // Each setProgress call increments this counter, we decrement after completion
        var attempts = 0
        while (pendingProgressUpdates.get() > 0 && attempts < 50) {
            delay(10)
            attempts++
        }
        // Additional delay to ensure database write is fully flushed
        delay(50)
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

        // Parse input data
        val filesData = inputData.getStringArray("model_files") ?: return Result.failure(
            workDataOf("error_message" to "No files specified")
        )

        val sessionId = inputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)
        val models = filesData.mapNotNull { parseModelData(it) }

        if (models.isEmpty()) {
            logger.warning(TAG, "No valid models to download from ${filesData.size} input entries")
            return if (sessionId != null) {
                Result.success(workDataOf(DownloadWorkScheduler.KEY_SESSION_ID to sessionId))
            } else {
                Result.success()
            }
        }

        logger.info(TAG, "Starting download of ${models.size} models: ${models.joinToString { checkNotNull(it.originalFileName) { "originalFileName must not be null - this is a bug in model configuration" } }}")

        // Initialize progress tracker with models
        progressTracker.initialize(models)

        val totalSize = models.sumOf { it.sizeBytes }
        val totalFiles = models.size

        return try {
            val completedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)

            coroutineScope {
                models.map { model ->
                    async {
                        try {
                            downloadFile(model)
                            completedCount.incrementAndGet()
                            logger.debug(TAG, "Successfully downloaded ${model.filenames.first()}")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failedCount.incrementAndGet()
                            logger.error(TAG, "Failed to download ${model.filenames.first()}: ${e.message}", e)

                            val errorMessage = when (e) {
                                is SocketException -> "Connection reset: ${e.message}"
                                is SocketTimeoutException -> "Download timed out: ${e.message}"
                                else -> e.message
                            }
                            // Use tracking key (originalFileName) for state updates
                            val trackingKey = checkNotNull(model.originalFileName) { "originalFileName must not be null - this is a bug in model configuration" }
                            progressTracker.updateFileState(trackingKey) {
                                it.copy(status = FileStatus.FAILED, error = errorMessage)
                            }

                            updateOverallProgress(completedCount.get(), totalFiles, totalSize)

                            if (e is SocketException) {
                                delay(500)
                            }
                        } finally {
                            updateOverallProgress(completedCount.get(), totalFiles, totalSize)
                        }
                    }
                }.awaitAll()
            }

            // Wait for any pending progress updates to complete before returning Result
            waitForPendingProgressUpdates()

            val result = if (failedCount.get() > 0) {
                val currentAttempt = runAttemptCount
                if (currentAttempt >= ModelConfig.MAX_RETRY_ATTEMPTS) {
                    logger.error(TAG, "Max retry attempts ($currentAttempt) exceeded, failing permanently")
                    Result.failure(workDataOf("error_message" to "Download failed after $currentAttempt attempts"))
                } else {
                    logger.warning(TAG, "Download completed with $failedCount failed out of ${models.size}, will retry (attempt $currentAttempt)")
                    Result.retry()
                }
            } else {
                logger.info(TAG, "All ${models.size} models downloaded successfully")

                // Read session ID from input (needed for both success and failure paths)
                val sessionId = inputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)

                // Verify all downloaded files - centralized validation in FileIntegrityValidator
                fileIntegrityValidator.verifyModelsExist().fold(
                    onSuccess = {
                        logger.info(TAG, "File integrity verification passed for all models")
                    },
                    onFailure = {
                        logger.error(TAG, "File integrity verification failed for all models: ${it.message}")

                        return Result.failure(workDataOf(
                            "error_message" to "Download completed but files verification failed. Error: ${it.message}",
                            DownloadWorkScheduler.KEY_SESSION_ID to sessionId
                        ))
                    }
                )

                if (sessionId != null) {
                    Result.success(workDataOf(DownloadWorkScheduler.KEY_SESSION_ID to sessionId))
                } else {
                    Result.success()
                }
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Download error: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun downloadFile(model: DownloadWorkerModelFile): Result {
        val targetDir = File(applicationContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        if (!targetDir.exists()) targetDir.mkdirs()

        // Log the actual URL being used
        logger.info(TAG, "[DOWNLOAD] Starting download: url=${model.url}, originalFileName=${model.originalFileName}, filenames=${model.filenames}")

        // Use tracking key for state updates
        val trackingKey = checkNotNull(model.originalFileName) { "originalFileName must not be null - this is a bug in model configuration" }

        // Check if target file already exists - will verify with MD5 later
        // Skip size validation - rely 100% on MD5 verification for integrity
        val targetFile = File(targetDir, model.filenames.first())
        if (targetFile.exists()) {
            progressTracker.updateFileState(trackingKey) {
                it.copy(
                    status = FileStatus.COMPLETE,
                    bytesDownloaded = targetFile.length(),
                    totalBytes = targetFile.length()
                )
            }
            val sessionId = inputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)
                ?: throw Exception("sessionId was null. This should be set when download begins.")
            return Result.success(workDataOf(DownloadWorkScheduler.KEY_SESSION_ID to sessionId))
        }

        progressTracker.updateFileState(trackingKey) {
            it.copy(status = FileStatus.DOWNLOADING)
        }

        // Initial progress update
        setProgress(progressTracker.serializeToWorkData())

        // Get existing bytes for resume support
        val tempFile = File(targetDir, "${model.filenames.first()}${ModelConfig.TEMP_EXTENSION}")
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        try {
            // Delegate download to FileDownloaderPort with progress callback
            // File integrity (size + MD5) validation is done centrally in verifyModelsExist() after all downloads
            val downloadResult = fileDownloader.downloadFile(
                model = model,
                targetDir = targetDir,
                existingBytes = existingBytes,
                progressCallback = object : FileDownloaderPort.ProgressCallback {
                    override fun onProgress(bytesDownloaded: Long, totalBytes: Long) {
                        // Update progress tracker - this runs on the download thread
                        progressTracker.updateFileState(trackingKey) {
                            it.copy(
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes.coerceAtLeast(model.sizeBytes),
                                status = FileStatus.DOWNLOADING
                            )
                        }

                        // Push progress to WorkManager (throttled by shouldUpdateProgress)
                        // Use runBlocking with tracking because this callback is not a suspend function
                        if (progressTracker.shouldUpdateProgress()) {
                            pendingProgressUpdates.incrementAndGet()
                            try {
                                runBlocking {
                                    setProgress(progressTracker.serializeToWorkData())
                                }
                                progressTracker.markProgressUpdated()
                            } finally {
                                pendingProgressUpdates.decrementAndGet()
                            }
                        }
                    }
                }
            )

            progressTracker.updateFileState(trackingKey) {
                it.copy(
                    status = FileStatus.COMPLETE,
                    bytesDownloaded = downloadResult.bytesDownloaded,
                    totalBytes = downloadResult.totalBytes
                )
            }

            // Final progress update after download completes
            updateIntermediateProgress()

            val sessionId = inputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)
            return Result.success(workDataOf(DownloadWorkScheduler.KEY_SESSION_ID to sessionId))
        } catch (e: Exception) {
            logger.error(TAG, "Download failed: ${e.message}")
            throw e
        }
    }

    private fun parseModelData(data: String): DownloadWorkerModelFile? {
        val parts = data.split("|")
        if (parts.size < 5) return null

        return try {
            val originalFileName = parts[0]
            val modelTypesStr = parts[4]
            val modelTypes = if (modelTypesStr.isNotEmpty()) {
                modelTypesStr.split(",").map { ModelType.valueOf(it) }
            } else {
                listOf(ModelType.fromApiValue(originalFileName.substringBefore(".")))
            }
            val modelFileFormat = try {
                ModelFileFormat.valueOf(parts[5])
            } catch (e: Exception) {
                ModelFileFormat.LITERTLM
            }
            val md5Value = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
            
            // DIAGNOSTIC: Log MD5 and filename info
            logger.info(TAG, "[DIAGNOSTIC] parseModelData: originalFileName=$originalFileName, md5=$md5Value, modelTypes=$modelTypes, modelFileFormat=$modelFileFormat")
            
            DownloadWorkerModelFile(
                sizeBytes = parts[1].toLong(),
                url = parts[2],
                md5 = md5Value,
                modelTypes = modelTypes,
                originalFileName = originalFileName,
                modelFileFormat = modelFileFormat,
            )
        } catch (e: Exception) {
            logger.error(TAG, "Failed to parse model data: ${e.message}", e)
            null
        }
    }

    /**
     * Update progress during active download (called frequently from download loop).
     * Uses its own throttling to avoid overwhelming WorkManager's SQLite database.
     * Uses progress tracker for state management.
     */
    private suspend fun updateIntermediateProgress() {
        if (progressTracker.shouldUpdateProgress()) {
            setProgress(progressTracker.serializeToWorkData())
            progressTracker.markProgressUpdated()

            // Update foreground notification - must use setForegroundAsync() to update
            // a notification that was initially set with setForeground()
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

    private suspend fun updateOverallProgress(completed: Int, total: Int, totalSize: Long) {
        val snapshot = progressTracker.computeOverallProgress()

        // Throttle setProgress to prevent overwhelming WorkManager's SQLite database
        if (progressTracker.shouldUpdateProgress()) {
            setProgress(progressTracker.serializeToWorkData())
            progressTracker.markProgressUpdated()
        }

        // Update foreground notification - must use setForegroundAsync() to update
        // a notification that was initially set with setForeground()
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
