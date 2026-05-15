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
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val cloudSyncPort: CloudSyncPort,
        private val loggingPort: LoggingPort,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "SyncWorker"
        }

        override suspend fun doWork(): Result =
            try {
                val result = cloudSyncPort.syncPendingChanges()
                if (result.isSuccess) {
                    loggingPort.debug(TAG, "Sync completed successfully")
                    Result.success()
                } else {
                    loggingPort.warning(TAG, "Sync failed: ${result.exceptionOrNull()?.message}")
                    Result.retry()
                }
            } catch (e: Exception) {
                loggingPort.error(TAG, "Sync threw exception", e)
                Result.retry()
            }
    }
