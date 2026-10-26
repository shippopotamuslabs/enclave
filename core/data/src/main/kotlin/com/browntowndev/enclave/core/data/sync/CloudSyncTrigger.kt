package com.browntowndev.enclave.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.EntitlementPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class CloudSyncTrigger
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val loggingPort: LoggingPort,
        private val entitlementPort: EntitlementPort,
    ) {
        companion object {
            private const val TAG = "CloudSyncTrigger"
        }

        private fun isSubscriptionBlocked(): Boolean {
            if (entitlementPort.entitlementState.value is EntitlementState.NotEntitled) {
                loggingPort.debug(TAG, "Skipping sync enqueue: subscription required")
                return true
            }
            return false
        }

        open fun trigger() {
            if (isSubscriptionBlocked()) return
            triggerMetadata()
            triggerMedia()
            triggerMediaDownload()
            triggerFiles()
        }

        open fun triggerMetadata() {
            if (isSubscriptionBlocked()) return
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

        open fun triggerMedia() {
            if (isSubscriptionBlocked()) return
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

        open fun triggerMediaDownload() {
            if (isSubscriptionBlocked()) return
            loggingPort.debug(TAG, "Enqueuing enclave_media_download (KEEP)")
            workManager.enqueueUniqueWork(
                "enclave_media_download",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MediaCloudDownloadWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build(),
            )
        }

        open fun triggerFiles() {
            if (isSubscriptionBlocked()) return
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

        open fun triggerCloudChatsPurge() {
            enqueuePurge(CloudPurgeWorker.TYPE_CHATS)
        }

        open fun triggerCloudMediaPurge() {
            enqueuePurge(CloudPurgeWorker.TYPE_MEDIA)
        }

        open fun triggerCloudMemoriesPurge() {
            enqueuePurge(CloudPurgeWorker.TYPE_MEMORIES)
        }

        open fun triggerCloudArtifactsPurge() {
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
