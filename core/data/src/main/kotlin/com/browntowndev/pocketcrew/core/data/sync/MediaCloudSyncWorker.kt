package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.storage.FirebaseStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.io.File

@HiltWorker
class MediaCloudSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val studioMediaDao: StudioMediaDao,
        private val storage: FirebaseStorage,
        private val loggingPort: LoggingPort,
        private val cryptoManager: CryptoManagerPort,
        private val userIdProvider: () -> String?,
        private val syncTrigger: CloudSyncTrigger,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "MediaCloudSyncWorker"
        }

        @Suppress("LoopWithTooManyJumpStatements", "MaxLineLength")
        override suspend fun doWork(): Result {
            val userId = userIdProvider() ?: return Result.failure()

            val pendingUploads = studioMediaDao.getPendingUploads()
            val pendingDeletions = studioMediaDao.getPendingDeletions()
            if (pendingUploads.isEmpty() && pendingDeletions.isEmpty()) return Result.success()

            var hasFailure = false

            for (media in pendingUploads) {
                try {
                    val storageRef = storage.reference.child("users/$userId/media/${media.id}")
                    var downloadUrl: String? = null

                    if (!media.stagingRemoteUrl.isNullOrBlank()) {
                        try {
                            storageRef.metadata.await()
                            downloadUrl = media.stagingRemoteUrl
                            loggingPort.debug(TAG, "Recovered from stagingRemoteUrl for media ${media.id}: $downloadUrl")
                        } catch (e: Exception) {
                            loggingPort.debug(
                                TAG,
                                "Staging URL present but metadata check failed for media ${media.id}, re-uploading. ${e.message}",
                            )
                        }
                    }

                    if (downloadUrl == null) {
                        val fileBytes =
                            if (media.mediaUri.startsWith("content://")) {
                                val androidUri = android.net.Uri.parse(media.mediaUri)
                                applicationContext.contentResolver.openInputStream(androidUri)?.use { it.readBytes() }
                                    ?: throw IllegalStateException("Unable to open content stream for ${media.mediaUri}")
                            } else {
                                val localFile = File(media.mediaUri.replace("file://", ""))
                                if (!localFile.exists()) {
                                    loggingPort.error(TAG, "Local file not found for media ${media.id}: ${media.mediaUri}")
                                    studioMediaDao.markUploadFailed(media.id)
                                    continue
                                }
                                localFile.readBytes()
                            }

                        val encryptionResult = cryptoManager.encryptBytes(fileBytes)

                        if (encryptionResult.isFailure) {
                            loggingPort.error(TAG, "Encryption failed for media ${media.id}", encryptionResult.exceptionOrNull())
                            hasFailure = true
                            continue
                        }

                        val encryptedBytes = encryptionResult.getOrThrow()
                        loggingPort.debug(TAG, "Uploading to bucket: ${storage.reference.bucket}, path: users/$userId/media/${media.id}")
                        storageRef.putBytes(encryptedBytes).await()

                        downloadUrl = storageRef.downloadUrl.await().toString()
                        studioMediaDao.updateStagingUrl(media.id, downloadUrl)
                    }

                    studioMediaDao.markUploaded(media.id, downloadUrl)
                    loggingPort.debug(TAG, "Uploaded media ${media.id} to $downloadUrl")
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to upload media ${media.id}", e)
                    hasFailure = true
                }
            }

            for (media in pendingDeletions) {
                try {
                    loggingPort.debug(TAG, "Deleting cloud media for ${media.id}")
                    val storageRef = storage.reference.child("users/$userId/media/${media.id}")
                    try {
                        storageRef.delete().await()
                    } catch (e: Exception) {
                        // If file doesn't exist, we consider it deleted
                        loggingPort.debug(TAG, "Cloud file already gone or error: ${e.message}")
                    }
                    studioMediaDao.markCloudDeleted(media.id)
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to delete cloud media ${media.id}", e)
                    hasFailure = true
                }
            }

            // Trigger metadata sync once at the end if we made progress
            if (!hasFailure) {
                syncTrigger.triggerMetadata()
            }

            return if (hasFailure) Result.retry() else Result.success()
        }
    }
