package com.browntowndev.enclave.core.data.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.browntowndev.enclave.domain.port.repository.SyncOrchestrator
import com.browntowndev.enclave.domain.port.repository.SyncProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOrchestratorImpl @Inject constructor(
    private val workManager: WorkManager,
) : SyncOrchestrator {

    companion object {
        private const val UNIQUE_WORK_NAME = "initial_sync_work"
        private const val WORK_TAG = "initial_sync"
    }

    override val syncProgress: Flow<SyncProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@map null
                if (workInfo.state.isFinished) return@map null

                val progress = workInfo.progress
                val fraction = progress.getFloat(InitialSyncWorker.KEY_PROGRESS_FRACTION, -1f).takeIf { it >= 0f }
                val status = progress.getString(InitialSyncWorker.KEY_PROGRESS_STATUS)
                SyncProgress(fraction, status)
            }

    override suspend fun startInitialSync(uploadLocalFirst: Boolean): Result<Unit> {
        val workRequest = OneTimeWorkRequestBuilder<InitialSyncWorker>()
            .setInputData(
                workDataOf(
                    InitialSyncWorker.KEY_UPLOAD_LOCAL_FIRST to uploadLocalFirst
                )
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        val finishedWorkInfo = workManager.getWorkInfoByIdFlow(workRequest.id)
            .first { it?.state?.isFinished == true }

        val isSuccess = finishedWorkInfo?.state == WorkInfo.State.SUCCEEDED
        return if (isSuccess) {
            Result.success(Unit)
        } else {
            val failureMessage = finishedWorkInfo?.outputData?.getString(InitialSyncWorker.KEY_PROGRESS_STATUS)
                ?: "Unknown sync error"
            Result.failure(IOException(failureMessage))
        }
    }
}
