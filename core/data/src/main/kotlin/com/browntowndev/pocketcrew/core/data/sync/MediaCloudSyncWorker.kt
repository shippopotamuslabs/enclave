package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
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
    private val userIdProvider: () -> String?
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MediaCloudSyncWorker"
    }

    override suspend fun doWork(): Result {
        val userId = userIdProvider() ?: return Result.failure()
        
        val pendingUploads = studioMediaDao.getPendingUploads()
        if (pendingUploads.isEmpty()) return Result.success()

        var hasFailure = false

        for (media in pendingUploads) {
            try {
                val localFile = File(media.mediaUri.replace("file://", ""))
                if (!localFile.exists()) {
                    loggingPort.error(TAG, "Local file not found for media ${media.id}: ${media.mediaUri}")
                    studioMediaDao.insertMedia(media.copy(syncStatus = "FAILED"))
                    continue
                }

                val storageRef = storage.reference.child("users/$userId/media/${media.id}")
                storageRef.putFile(Uri.fromFile(localFile)).await()
                
                val downloadUrl = storageRef.downloadUrl.await().toString()
                
                studioMediaDao.insertMedia(
                    media.copy(
                        remoteUrl = downloadUrl,
                        syncStatus = "UPLOADED",
                        pendingSync = 1 // Trigger metadata sync
                    )
                )
                loggingPort.debug(TAG, "Uploaded media ${media.id} to $downloadUrl")
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to upload media ${media.id}", e)
                hasFailure = true
            }
        }

        return if (hasFailure) Result.retry() else Result.success()
    }
}
