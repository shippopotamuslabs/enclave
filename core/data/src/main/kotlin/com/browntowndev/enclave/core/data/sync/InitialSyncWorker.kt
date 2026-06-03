package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

@HiltWorker
class InitialSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val startCloudSyncListeningUseCase: StartCloudSyncListeningUseCase,
        private val loggingPort: LoggingPort,
    ) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "InitialSyncWorker"
        const val KEY_UPLOAD_LOCAL_FIRST = "upload_local_first"
        const val KEY_PROGRESS_FRACTION = "progress_fraction"
        const val KEY_PROGRESS_STATUS = "progress_status"
        const val KEY_APPLIED_COUNT = "applied_count"
        const val KEY_TOTAL_ESTIMATE = "total_estimate"
    }

    override suspend fun doWork(): Result =
        try {
            val uploadLocalFirst = inputData.getBoolean(KEY_UPLOAD_LOCAL_FIRST, true)
            loggingPort.debug(TAG, "Starting initial sync worker, uploadLocalFirst=$uploadLocalFirst")

            val syncResult = startCloudSyncListeningUseCase(
                uploadLocalFirst = uploadLocalFirst,
                onProgress = { progress ->
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS_FRACTION to (progress.progressFraction ?: -1f),
                            KEY_PROGRESS_STATUS to progress.status,
                            KEY_APPLIED_COUNT to progress.appliedCount,
                            KEY_TOTAL_ESTIMATE to (progress.totalEstimate ?: -1L),
                        ),
                    )
                }
            )

            if (syncResult.isSuccess) {
                loggingPort.info(TAG, "Initial sync completed successfully")
                Result.success()
            } else {
                val error = syncResult.exceptionOrNull()
                loggingPort.error(TAG, "Initial sync failed", error)
                if (error is IOException || error?.cause is IOException) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(KEY_PROGRESS_STATUS to (error?.message ?: "Unknown sync error"))
                    )
                }
            }
        } catch (e: CancellationException) {
            loggingPort.info(TAG, "Initial sync worker cancelled")
            throw e
        } catch (e: IOException) {
            loggingPort.error(TAG, "Initial sync worker threw IOException", e)
            Result.retry()
        } catch (e: Exception) {
            loggingPort.error(TAG, "Initial sync worker threw exception", e)
            val cause = e.cause
            if (cause is IOException) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(KEY_PROGRESS_STATUS to (e.message ?: "Sync process threw exception"))
                )
            }
        }
}
