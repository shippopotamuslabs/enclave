package com.browntowndev.enclave.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncTrigger
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val loggingPort: LoggingPort,
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
            loggingPort.debug(TAG, "Enqueuing enclave_sync (APPEND_OR_REPLACE)")
            workManager.enqueueUniqueWork(
                "enclave_sync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build(),
            )
        }

        fun triggerMedia() {
            loggingPort.debug(TAG, "Enqueuing enclave_media_sync (REPLACE)")
            workManager.enqueueUniqueWork(
                "enclave_media_sync",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MediaCloudSyncWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build(),
            )
        }

        fun triggerFiles() {
            loggingPort.debug(TAG, "Enqueuing enclave_file_sync (REPLACE)")
            workManager.enqueueUniqueWork(
                "enclave_file_sync",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MessageFileCloudSyncWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build(),
            )
        }

        fun triggerCloudChatsPurge() {
            enqueuePurge(CloudPurgeWorker.TYPE_CHATS)
        }

        fun triggerCloudMediaPurge() {
            enqueuePurge(CloudPurgeWorker.TYPE_MEDIA)
        }

        fun triggerCloudMemoriesPurge() {
            enqueuePurge(CloudPurgeWorker.TYPE_MEMORIES)
        }

        fun triggerCloudArtifactsPurge() {
            enqueuePurge(CloudPurgeWorker.TYPE_ARTIFACTS)
        }

        private fun enqueuePurge(type: String) {
            loggingPort.debug(TAG, "Enqueuing cloud purge: $type")
            workManager.enqueueUniqueWork(
                "cloud_purge_$type",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CloudPurgeWorker>()
                    .setInputData(
                        androidx.work.Data
                            .Builder()
                            .putString(CloudPurgeWorker.KEY_PURGE_TYPE, type)
                            .build(),
                    ).setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build(),
            )
        }
    }
