package com.browntowndev.pocketcrew.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CloudPurgeWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val cloudSyncPort: CloudSyncPort,
        private val loggingPort: LoggingPort,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "CloudPurgeWorker"
            const val KEY_PURGE_TYPE = "purge_type"

            const val TYPE_CHATS = "CHATS"
            const val TYPE_MEDIA = "MEDIA"
            const val TYPE_MEMORIES = "MEMORIES"
            const val TYPE_ARTIFACTS = "ARTIFACTS"
        }

        override suspend fun doWork(): Result {
            val purgeType = inputData.getString(KEY_PURGE_TYPE) ?: return Result.failure()
            loggingPort.debug(TAG, "Starting cloud purge for type: $purgeType")

            val result =
                when (purgeType) {
                    TYPE_CHATS -> cloudSyncPort.deleteAllChatsFromCloud()
                    TYPE_MEDIA -> cloudSyncPort.deleteAllMediaFromCloud()
                    TYPE_MEMORIES -> cloudSyncPort.deleteAllMemoriesFromCloud()
                    TYPE_ARTIFACTS -> cloudSyncPort.deleteAllArtifactsFromCloud()
                    else -> {
                        loggingPort.error(TAG, "Unknown purge type: $purgeType")
                        return Result.failure()
                    }
                }

            return if (result.isSuccess) {
                loggingPort.debug(TAG, "Cloud purge successful for type: $purgeType")
                Result.success()
            } else {
                val error = result.exceptionOrNull()?.message
                loggingPort.warning(TAG, "Cloud purge failed for type: $purgeType, error: $error")
                Result.retry()
            }
        }
    }
