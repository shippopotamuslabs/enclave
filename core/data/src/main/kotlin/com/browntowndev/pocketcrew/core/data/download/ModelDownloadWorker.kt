package com.browntowndev.pocketcrew.core.data.download

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.requiredArtifacts
import com.browntowndev.pocketcrew.domain.model.config.totalArtifactSizeInBytes
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
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
import org.json.JSONObject

/**
 * WorkManager worker for downloading model files.
 * Delegates to extracted components for notification handling and progress tracking.
 * SHA-256 integrity validation is performed during streaming download in HttpFileDownloader.
 */
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
        val modelsMap = filesData.mapNotNull { parseModelData(it) }.toMap()

        if (modelsMap.isEmpty()) {
            logger.warning(TAG, "No valid models to download from ${filesData.size} input entries")
            return if (sessionId != null) {
                Result.success(workDataOf(DownloadWorkScheduler.KEY_SESSION_ID to sessionId))
            } else {
                Result.success()
            }
        }

        val uniqueAssets = modelsMap.values.distinctBy { it.metadata.sha256 }
        logger.info(TAG, "Starting download of ${uniqueAssets.size} unique assets (${modelsMap.size} model slots): ${uniqueAssets.joinToString { it.metadata.remoteFileName }}")

        // Initialize progress tracker with model map
        progressTracker.initialize(modelsMap)

        val totalSize = uniqueAssets.sumOf { it.metadata.totalArtifactSizeInBytes() }
        val totalFiles = uniqueAssets.size

        return try {
            val completedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)

            coroutineScope {
                uniqueAssets.map { asset ->
                    async {
                        try {
                            downloadFile(asset)
                            completedCount.incrementAndGet()
                            logger.debug(TAG, "Successfully downloaded ${asset.metadata.localFileName}")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failedCount.incrementAndGet()
                            logger.error(TAG, "Failed to download ${asset.metadata.localFileName}: ${e.message}", e)

                            val errorMessage = when (e) {
                                is SocketException -> "Connection reset: ${e.message}"
                                is SocketTimeoutException -> "Download timed out: ${e.message}"
                                else -> e.message
                            }
                            // Use tracking key (SHA256) for state updates
                            val trackingKey = asset.metadata.sha256
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
                    logger.warning(TAG, "Download completed with $failedCount failed out of ${uniqueAssets.size}, will retry (attempt $currentAttempt)")
                    Result.retry()
                }
            } else {
                logger.info(TAG, "All ${uniqueAssets.size} unique assets downloaded successfully")

                // Read session ID from input (needed for both success and failure paths)
                val sessionId = inputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)

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

    private suspend fun downloadFile(asset: LocalModelAsset): Result {
        val targetDir = File(applicationContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        if (!targetDir.exists()) targetDir.mkdirs()

        // Use tracking key (SHA256) for state updates
        val trackingKey = asset.metadata.sha256
        val artifacts = asset.metadata.requiredArtifacts()
        val totalExpectedBytes = asset.metadata.totalArtifactSizeInBytes()

        val allArtifactsPresent = artifacts.all { artifact ->
            val targetFile = File(targetDir, artifact.localFileName)
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
            val sessionId = inputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)
                ?: throw Exception("sessionId was null. This should be set when download begins.")
            return Result.success(workDataOf(DownloadWorkScheduler.KEY_SESSION_ID to sessionId))
        }

        progressTracker.updateFileState(trackingKey) {
            it.copy(status = FileStatus.DOWNLOADING)
        }

        // Initial progress update
        setProgress(progressTracker.serializeToWorkData())

        try {
            var completedBytes = 0L
            artifacts.forEach { artifact ->
                val downloadUrl = modelUrlProvider.getModelDownloadUrl(
                    asset.copy(
                        metadata = asset.metadata.copy(
                            remoteFileName = artifact.remoteFileName,
                            localFileName = artifact.localFileName,
                            sha256 = artifact.sha256,
                            sizeInBytes = artifact.sizeInBytes,
                        )
                    )
                )
                logger.info(
                    TAG,
                    "[DOWNLOAD] Starting download: url=$downloadUrl, remoteFileName=${artifact.remoteFileName}, localFileName=${artifact.localFileName}"
                )

                val targetFile = File(targetDir, artifact.localFileName)
                if (targetFile.exists() && targetFile.length() == artifact.sizeInBytes) {
                    completedBytes += artifact.sizeInBytes
                    return@forEach
                }

                val tempFile = File(targetDir, "${artifact.localFileName}${ModelConfig.TEMP_EXTENSION}")
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
                                pendingProgressUpdates.incrementAndGet()
                                try {
                                    runBlocking {
                                        setProgress(progressTracker.serializeToWorkData())
                                        updateNotificationForeground()
                                    }
                                    progressTracker.markProgressUpdated()
                                } finally {
                                    pendingProgressUpdates.decrementAndGet()
                                }
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

            // Final progress update after download completes
            updateIntermediateProgress()

            val sessionId = inputData.getString(DownloadWorkScheduler.KEY_SESSION_ID)
            return Result.success(workDataOf(DownloadWorkScheduler.KEY_SESSION_ID to sessionId))
        } catch (e: Exception) {
            logger.error(TAG, "Download failed: ${e.message}")
            throw e
        }
    }

    private fun parseModelData(data: String): Pair<ModelType, LocalModelAsset>? {
        return try {
            val json = JSONObject(data)
            val modelType = try {
                ModelType.valueOf(json.getString("modelType"))
            } catch (e: Exception) {
                logger.error(TAG, "Invalid model type: ${json.optString("modelType")}")
                return null
            }

            val metadata = LocalModelMetadata(
                id = LocalModelId(""),
                huggingFaceModelName = json.optString("huggingFaceModelName", ""),
                remoteFileName = json.getString("remoteFileName"),
                localFileName = json.getString("localFileName"),
                sha256 = json.getString("sha256"),
                sizeInBytes = json.optLong("sizeInBytes", 0L),
                mmprojRemoteFileName = json.optString("mmprojRemoteFileName").takeIf { it.isNotBlank() },
                mmprojLocalFileName = json.optString("mmprojLocalFileName").takeIf { it.isNotBlank() },
                mmprojSha256 = json.optString("mmprojSha256").takeIf { it.isNotBlank() },
                mmprojSizeInBytes = json.optLong("mmprojSizeInBytes").takeIf { it > 0L },
                modelFileFormat = try {
                    ModelFileFormat.valueOf(json.getString("modelFileFormat"))
                } catch (e: Exception) {
                    ModelFileFormat.LITERTLM
                },
                source = try {
                    DownloadSource.valueOf(json.optString("source", "HUGGING_FACE"))
                } catch (e: Exception) {
                    DownloadSource.HUGGING_FACE
                },
            )

            val configuration = LocalModelConfiguration(
                id = LocalModelConfigurationId(""),
                localModelId = LocalModelId(""), // Assigned later by repository
                displayName = json.getString("presetName"),
                temperature = json.optDouble("temperature", 0.7),
                topK = json.optInt("topK", 40),
                topP = json.optDouble("topP", 0.95),
                minP = json.optDouble("minP", 0.0),
                repetitionPenalty = json.optDouble("repetitionPenalty", 1.1),
                maxTokens = json.optInt("maxTokens", 4096),
                contextWindow = json.optInt("contextWindow", 4096),
                systemPrompt = json.optString("systemPrompt", ""),
                isSystemPreset = json.optBoolean("isSystemPreset", true),
                thinkingEnabled = json.optBoolean("thinkingEnabled", false)
            )

            // DIAGNOSTIC: Log parsed data
            logger.info(TAG, "[DIAGNOSTIC] parseModelData: modelType=$modelType, remoteFileName=${metadata.remoteFileName}, sha256=${metadata.sha256}, size=${metadata.sizeInBytes}")

            modelType to LocalModelAsset(
                metadata = metadata,
                configurations = listOf(configuration)
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
            updateNotificationForeground()
        }
    }

    private suspend fun updateOverallProgress(completed: Int, total: Int, totalSize: Long) {
        val snapshot = progressTracker.computeOverallProgress()

        // Throttle setProgress to prevent overwhelming WorkManager's SQLite database
        if (progressTracker.shouldUpdateProgress()) {
            setProgress(progressTracker.serializeToWorkData())
            progressTracker.markProgressUpdated()
        }

        updateNotificationForeground()
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
