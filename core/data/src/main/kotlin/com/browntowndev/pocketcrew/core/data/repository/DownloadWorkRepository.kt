package com.browntowndev.pocketcrew.core.data.repository

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for observing WorkManager download progress using reactive Flow.
 *
 * Uses WorkManager's getWorkInfosForUniqueWorkFlow() API which properly emits
 * on both state AND progress changes - no manual polling required.
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
     * Observe download progress using WorkManager's Flow API.
     *
     * This uses getWorkInfosForUniqueWorkFlow() which properly emits WorkInfo
     * updates including progress changes - no manual polling required.
     *
     * @param workId The UUID of the work to observe
     * @return Flow that emits WorkInfo updates until terminal state is reached
     */
    fun observeDownloadProgress(workId: UUID): Flow<WorkInfo?> {
        Log.d(TAG, "[FLOW] Starting progress observation for workId=$workId")

        // Use getWorkInfosForUniqueWorkFlow which returns a Flow that properly
        // emits on state AND progress changes (WorkManager 2.9+)
        return workManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
        .map { workInfos ->
            // Filter to the specific work ID we're interested in
            workInfos.find { it.id == workId }
        }
        .catch { e ->
            Log.e(TAG, "[FLOW] Error observing work: ${e.message}", e)
            emit(null)
        }
        .flowOn(Dispatchers.IO)
    }

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
        return try {
            val workInfo = workManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get().firstOrNull()
            workInfo?.state == WorkInfo.State.ENQUEUED ||
                   workInfo?.state == WorkInfo.State.RUNNING ||
                   workInfo?.state == WorkInfo.State.BLOCKED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if work is running: ${e.message}")
            false
        }
    }
}
