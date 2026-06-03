package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.browntowndev.enclave.core.data.local.TelemetryEventDao
import com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClient
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TelemetryUploadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val telemetryEventDao: TelemetryEventDao,
        private val otlpClient: TelemetryOtlpClient,
        private val loggingPort: LoggingPort,
    ) : CoroutineWorker(context, params) {
        companion object {
            private const val TAG = "TelemetryUploadWorker"
            const val BATCH_SIZE = 100
        }

        override suspend fun doWork(): ListenableWorker.Result =
            try {
                uploadPendingEvents()
            } catch (error: Exception) {
                loggingPort.warning(TAG, "Telemetry upload worker failed: ${error.message}")
                ListenableWorker.Result.retry()
            }

        private suspend fun uploadPendingEvents(): ListenableWorker.Result {
            var lastUploadedIds: Set<String>? = null
            while (true) {
                val batch = telemetryEventDao.getPendingUploads(BATCH_SIZE)
                if (batch.isEmpty()) {
                    return ListenableWorker.Result.success()
                }
                val batchIds = batch.map { it.id }.toSet()
                if (batchIds == lastUploadedIds) {
                    return ListenableWorker.Result.success()
                }
                val uploadResult = otlpClient.uploadBatch(batch)
                if (uploadResult.isFailure) {
                    loggingPort.warning(
                        TAG,
                        "Telemetry upload failed: ${uploadResult.exceptionOrNull()?.message}",
                    )
                    return ListenableWorker.Result.retry()
                }
                telemetryEventDao.markUploaded(batchIds.toList())
                telemetryEventDao.deleteUploaded()
                lastUploadedIds = batchIds
            }
        }
    }
