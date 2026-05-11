package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.storage.FirebaseStorage
import android.util.Base64
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

@HiltWorker
class MessageFileCloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageFileDao: MessageFileDao,
    private val storage: FirebaseStorage,
    private val loggingPort: LoggingPort,
    private val cryptoManager: CryptoManagerPort,
    private val userIdProvider: () -> String?,
    private val syncTrigger: CloudSyncTrigger
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MessageFileSyncWorker"
    }

    override suspend fun doWork(): Result {
        val userId = userIdProvider() ?: return Result.failure()

        val pendingUploads = messageFileDao.getPendingUploads()
        if (pendingUploads.isEmpty()) {
            val pendingDeletions = messageFileDao.getPendingDeletions()
            if (pendingDeletions.isEmpty()) return Result.success()
        }

        var hasFailure = false

        for (file in pendingUploads) {
            try {
                if (file.content.isBlank() && file.remoteUrl == null && file.stagingRemoteUrl == null) {
                    loggingPort.error(TAG, "Empty content for file ${file.id}")
                    messageFileDao.updateStatus(file.id, "FAILED")
                    continue
                }

                val storageRef = storage.reference.child("users/$userId/files/${file.id}")
                var downloadUrl: String? = null

                if (!file.stagingRemoteUrl.isNullOrBlank()) {
                    try {
                        storageRef.metadata.await()
                        downloadUrl = file.stagingRemoteUrl
                        loggingPort.debug(TAG, "Recovered from stagingRemoteUrl for file ${file.id}: $downloadUrl")
                    } catch (e: Exception) {
                        loggingPort.debug(TAG, "Staging URL present but metadata check failed for file ${file.id}, re-uploading. ${e.message}")
                    }
                }

                if (downloadUrl == null && file.content.isNotBlank()) {
                    val fileBytes = if (file.content.startsWith("BASE64:")) {
                        Base64.decode(file.content.substring(7), Base64.NO_WRAP)
                    } else {
                        file.content.toByteArray(Charsets.UTF_8)
                    }
                    val encryptionResult = cryptoManager.encryptBytes(fileBytes)
                    
                    if (encryptionResult.isFailure) {
                        loggingPort.error(TAG, "Encryption failed for file ${file.id}", encryptionResult.exceptionOrNull())
                        hasFailure = true
                        continue
                    }
                    
                    val encryptedBytes = encryptionResult.getOrThrow()
                    loggingPort.debug(TAG, "Uploading to bucket: ${storage.reference.bucket}, path: users/$userId/files/${file.id}")
                    storageRef.putBytes(encryptedBytes).await()
                    
                    downloadUrl = storageRef.downloadUrl.await().toString()
                    messageFileDao.updateStagingUrl(file.id, downloadUrl)
                }

                if (downloadUrl != null) {
                    messageFileDao.markUploaded(file.id, downloadUrl)
                    loggingPort.debug(TAG, "Uploaded file ${file.id} to $downloadUrl")
                } else if (file.content.isBlank() && file.remoteUrl == null) {
                    // This should have been caught by the first check, but just in case
                    loggingPort.error(TAG, "Nothing to upload and no staging URL for file ${file.id}")
                    hasFailure = true
                }
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to upload file ${file.id}", e)
                hasFailure = true
            }
        }

        val pendingDeletions = messageFileDao.getPendingDeletions()
        for (file in pendingDeletions) {
            try {
                loggingPort.debug(TAG, "Deleting cloud file for ${file.id}")
                val storageRef = storage.reference.child("users/$userId/files/${file.id}")
                try {
                    storageRef.delete().await()
                } catch (e: Exception) {
                    // If file doesn't exist, we consider it deleted
                    loggingPort.debug(TAG, "Cloud file already gone or error: ${e.message}")
                }
                messageFileDao.markCloudDeleted(file.id)
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to delete cloud file ${file.id}", e)
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

