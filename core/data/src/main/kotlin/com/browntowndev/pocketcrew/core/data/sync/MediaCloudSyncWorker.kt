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
class MediaCloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val studioMediaDao: StudioMediaDao,
    private val storage: FirebaseStorage,
    private val loggingPort: LoggingPort,
    private val cryptoManager: CryptoManagerPort,
    private val userIdProvider: () -> String?,
    private val syncTrigger: CloudSyncTrigger
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MediaCloudSyncWorker"
    }

    override suspend fun doWork(): Result {
        val userId = userIdProvider() ?: return Result.failure()

        val pendingUploads = studioMediaDao.getPendingUploads()
        val pendingDeletions = studioMediaDao.getPendingDeletions()
        if (pendingUploads.isEmpty() && pendingDeletions.isEmpty()) return Result.success()

        var hasFailure = false

        for (media in pendingUploads) {
            try {
                val localFile = File(media.mediaUri.replace("file://", ""))
                if (!localFile.exists()) {
                    loggingPort.error(TAG, "Local file not found for media ${media.id}: ${media.mediaUri}")
                    // File is permanently gone — mark FAILED without setting hasFailure so we don't retry uselessly
                    studioMediaDao.markUploadFailed(media.id)
                    continue
                }

                val storageRef = storage.reference.child("users/$userId/media/${media.id}")

                val fileBytes = localFile.readBytes()
                val encryptionResult = cryptoManager.encryptBytes(fileBytes)

                if (encryptionResult.isFailure) {
                    loggingPort.error(TAG, "Encryption failed for media ${media.id}", encryptionResult.exceptionOrNull())
                    hasFailure = true
                    continue
                }

                val encryptedBytes = encryptionResult.getOrThrow()
                storageRef.putBytes(encryptedBytes).await()

                val downloadUrl = storageRef.downloadUrl.await().toString()

                studioMediaDao.markUploaded(media.id, downloadUrl)
                syncTrigger.trigger()
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

        return if (hasFailure) Result.retry() else Result.success()
    }
}
