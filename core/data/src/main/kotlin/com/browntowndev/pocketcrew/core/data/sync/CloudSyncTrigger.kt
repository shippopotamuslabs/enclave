package com.browntowndev.pocketcrew.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncTrigger @Inject constructor(private val workManager: WorkManager) {

    fun trigger() {
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
