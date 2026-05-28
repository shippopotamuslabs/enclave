package com.browntowndev.enclave.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryUploadScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val config: TelemetryUploadConfig,
    ) {
        fun schedule() {
            if (!config.isConfigured) {
                return
            }
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            // 1. One-time upload immediately on startup to flush any pending telemetry
            val oneTimeRequest =
                OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                    .setConstraints(constraints)
                    .build()
            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest,
            )

            // 2. Periodic upload every 15 minutes
            val periodicRequest =
                PeriodicWorkRequestBuilder<TelemetryUploadWorker>(
                    UPLOAD_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                )
                    .setConstraints(constraints)
                    .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest,
            )
        }

        companion object {
            const val WORK_NAME = "telemetry_upload"
            const val ONE_TIME_WORK_NAME = "telemetry_upload_one_time"
            private const val UPLOAD_INTERVAL_MINUTES = 15L
        }
    }
