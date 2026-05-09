package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.storage.FirebaseStorage
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
                if (file.content.isBlank() && file.remoteUrl == null) {
                    loggingPort.error(TAG, "Empty content for file ${file.id}")
                    messageFileDao.updateStatus(file.id, "FAILED")
                    continue
                }

                if (file.content.isNotBlank()) {
                    val storageRef = storage.reference.child("users/$userId/files/${file.id}")
                    
                    val fileBytes = file.content.toByteArray(Charsets.UTF_8)
                    val encryptionResult = cryptoManager.encryptBytes(fileBytes)
                    
                    if (encryptionResult.isFailure) {
                        loggingPort.error(TAG, "Encryption failed for file ${file.id}", encryptionResult.exceptionOrNull())
                        hasFailure = true
                        continue
                    }
                    
                    val encryptedBytes = encryptionResult.getOrThrow()
                    storageRef.putBytes(encryptedBytes).await()
                    
                    val downloadUrl = storageRef.downloadUrl.await().toString()
                    
                    messageFileDao.markUploaded(file.id, downloadUrl)
                    syncTrigger.trigger()
                    loggingPort.debug(TAG, "Uploaded file ${file.id} to $downloadUrl")
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

        return if (hasFailure) Result.retry() else Result.success()
    }
}
