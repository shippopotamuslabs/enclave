package com.browntowndev.pocketcrew.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncTrigger @Inject constructor(
    private val workManager: WorkManager,
    private val loggingPort: LoggingPort
) {

    companion object {
        private const val TAG = "CloudSyncTrigger"
    }

    fun trigger() {
        triggerMetadata()
        triggerMedia()
        triggerFiles()
    }

    fun triggerMetadata() {
        loggingPort.debug(TAG, "Enqueuing pocketcrew_sync (REPLACE)")
        workManager.enqueueUniqueWork(
            "pocketcrew_sync",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
    }

    fun triggerMedia() {
        loggingPort.debug(TAG, "Enqueuing pocketcrew_media_sync (REPLACE)")
        workManager.enqueueUniqueWork(
            "pocketcrew_media_sync",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MediaCloudSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
    }

    fun triggerFiles() {
        loggingPort.debug(TAG, "Enqueuing pocketcrew_file_sync (REPLACE)")
        workManager.enqueueUniqueWork(
            "pocketcrew_file_sync",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MessageFileCloudSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
    }
}
