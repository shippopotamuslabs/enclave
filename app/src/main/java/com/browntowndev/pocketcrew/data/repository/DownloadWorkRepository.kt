package com.browntowndev.pocketcrew.data.repository

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import com.browntowndev.pocketcrew.domain.model.DownloadKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for observing WorkManager download progress using reactive Flow.
 * 
 * Uses polling-based Flow since WorkManager's InvalidationTracker doesn't
 * reliably trigger on progress-only updates.
 */
@Singleton
class DownloadWorkRepository @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        private const val TAG = "DownloadWorkRepository"
    }

    /**
     * Get the work ID for the current unique work.
     * Returns null if no work is scheduled in a meaningful state.
     * Only returns work that is RUNNING, ENQUEUED, or BLOCKED.
     */
    suspend fun getWorkId(): UUID? {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
            // Get the most recent work that's either RUNNING, ENQUEUED, or BLOCKED
            workInfos
                .filter { it.state == WorkInfo.State.RUNNING || 
                          it.state == WorkInfo.State.ENQUEUED || 
                          it.state == WorkInfo.State.BLOCKED }
                .maxByOrNull { it.id.toString() }
                ?.id
        } catch (e: Exception) {
            Log.e(TAG, "Error getting work ID: ${e.message}")
            null
        }
    }

    /**
     * Observe download progress using polling-based Flow.
     * 
     * Handles race condition by:
     * 1. First polling for work existence (work may not be created yet)
     * 2. Then polling for progress while work is RUNNING
     * 
     * WorkManager's InvalidationTracker doesn't reliably trigger on progress-only
     * updates, so we use explicit polling to ensure progress updates are always observed.
     */
    fun observeDownloadProgress(workId: UUID): Flow<WorkInfo> = flow {
        Log.d(TAG, "[POLLING] Starting progress observation for workId=$workId")

        // Phase 1: Wait for work to be created (handles race condition)
        Log.d(TAG, "[POLLING] Waiting for work to be created: $workId")
        var workInfo: WorkInfo? = null
        while (workInfo == null) {
            try {
                workInfo = workManager.getWorkInfoById(workId).get()
                if (workInfo == null) {
                    Log.d(TAG, "[POLLING] Work not yet created, waiting 500ms...")
                    delay(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[POLLING] Error checking work existence: ${e.message}")
                delay(500)
            }
        }
        Log.d(TAG, "[POLLING] Work found: ${workInfo.state}")
        emit(workInfo)

        // Phase 2: Poll for progress while RUNNING, ENQUEUED, or BLOCKED
        while (workInfo != null && (workInfo.state == WorkInfo.State.RUNNING || 
                            workInfo.state == WorkInfo.State.ENQUEUED ||
                            workInfo.state == WorkInfo.State.BLOCKED)) {
            try {
                delay(1000L)
                val newWorkInfo = workManager.getWorkInfoById(workId).get()
                if (newWorkInfo != null) {
                    workInfo = newWorkInfo
                    emit(workInfo)
                    Log.d(
                        TAG,
                        "[POLLING] EMITTED state=${workInfo.state}, progress=${
                            workInfo.progress.getInt(
                                "progress",
                                -1
                            )
                        }%"
                    )
                } else {
                    // Work was deleted/cancelled
                    break
                }
            } catch (e: Exception) {
                // Ignore cancellation - this happens when we cancel previous observation to start a new one
                if (e is kotlinx.coroutines.CancellationException) {
                    // Silently return - this is expected when we cancel previous job
                    return@flow
                }
                Log.e(TAG, "[POLLING] Error: ${e.message}", e)
                // Check for specific WorkManager channel errors
                if (e.message?.contains("Channel") == true) {
                    Log.e(TAG, "[POLLING] FATAL: WorkManager channel error - likely concurrent collection or work cancelled")
                }
            }
        }
        
        // Phase 3: Emit final state (SUCCEEDED, FAILED, or CANCELLED) to ensure
        // the observer receives the terminal state and can update UI accordingly
        try {
            val finalWorkInfo = workManager.getWorkInfoById(workId).get()
            if (finalWorkInfo != null && (finalWorkInfo.state == WorkInfo.State.SUCCEEDED ||
                finalWorkInfo.state == WorkInfo.State.FAILED ||
                finalWorkInfo.state == WorkInfo.State.CANCELLED)) {
                Log.d(TAG, "[POLLING] Emitting final state: ${finalWorkInfo.state}")
                emit(finalWorkInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[POLLING] Error emitting final state: ${e.message}")
        }
    }
        .flowOn(Dispatchers.IO)

    /**
     * Get work info synchronously.
     */
    suspend fun getCurrentWorkInfo(): WorkInfo? {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
            val info = workInfos.firstOrNull()
            if (info != null) {
                val progress = info.progress.getLong(DownloadKey.PROGRESS.key, -1)
                val downloaded = info.progress.getLong(DownloadKey.BYTES_DOWNLOADED.key, -1)
                Log.d(TAG, "getCurrentWorkInfo: state=${info.state}, progress=$progress%, downloaded=$downloaded bytes")
            }
            info
        } catch (e: Exception) {
            Log.e(TAG, "Error getting work info: ${e.message}")
            null
        }
    }

    /**
     * Cancel the current download work.
     */
    fun cancelDownload() {
        workManager.cancelUniqueWork(ModelConfig.WORK_TAG)
    }

    /**
     * Check if there's active download work.
     */
    fun isWorkRunning(): Boolean {
        val workInfo = workManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get().firstOrNull()
        return workInfo?.state == WorkInfo.State.ENQUEUED || 
               workInfo?.state == WorkInfo.State.RUNNING ||
               workInfo?.state == WorkInfo.State.BLOCKED
    }
}
