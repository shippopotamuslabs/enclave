package com.browntowndev.enclave.core.data.sync

import androidx.work.ExistingPeriodicWorkPolicy
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
            val request =
                PeriodicWorkRequestBuilder<TelemetryUploadWorker>(
                    UPLOAD_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        companion object {
            const val WORK_NAME = "telemetry_upload"
            private const val UPLOAD_INTERVAL_MINUTES = 15L
        }
    }
