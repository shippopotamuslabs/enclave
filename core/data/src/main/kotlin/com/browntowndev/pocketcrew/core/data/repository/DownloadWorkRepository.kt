package com.browntowndev.pocketcrew.core.data.repository

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.core.data.download.DownloadWorkKeys
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for observing WorkManager download progress using reactive Flow.
 *
 * Uses WorkManager's getWorkInfosForUniqueWorkFlow() API which properly emits
 * on both state AND progress changes - no manual polling required.
 *
 * Chain-aware: Prioritizes finalizer over download worker when both exist,
 * because in a two-worker chain, only the finalizer produces terminal states.
 */
@Singleton
class DownloadWorkRepository @Inject constructor(
    private val workManager: WorkManager,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "DownloadWorkRepository"
    }

    /**
     * Get the work ID for the current unique work, preferring the finalizer
     * when both download and finalizer workers exist in the chain.
     * Returns null if no work is scheduled in a meaningful state.
     * Only returns work that is RUNNING, ENQUEUED, BLOCKED, or SUCCEEDED.
     */
    suspend fun getWorkId(): UUID? {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
            selectBestWorkInfo(workInfos)?.id
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
     * In a two-worker chain, this observes the best candidate worker
     * (finalizer > downloader) for the most relevant state.
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
            // Prioritize the specific work ID we're interested in, UNLESS it has
            // reached an intermediate success state in a chain. If so, we must
            // transition to the finalizer to reach the terminal READY state.
            val exact = workInfos.find { it.id == workId }
            if (exact != null && exact.state != WorkInfo.State.SUCCEEDED) {
                return@map exact
            }

            // Fallback or transition: select the best candidate from the chain
            selectBestWorkInfo(workInfos)
        }
        .catch { e ->
            Log.e(TAG, "[FLOW] Error observing work: ${e.message}", e)
            emit(null)
        }
        .flowOn(ioDispatcher)
    }

    /**
     * Get work info synchronously, chain-aware.
     * Selects the best candidate from the two-worker chain.
     */
    suspend fun getCurrentWorkInfo(): WorkInfo? {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(ModelConfig.WORK_TAG).get()
            val info = selectBestWorkInfo(workInfos)
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
    suspend fun isWorkRunning(): Boolean = withContext(ioDispatcher) {
        try {
            // Using getWorkInfosForUniqueWorkFlow().firstOrNull() avoids blocking the thread completely.
            val workInfo = workManager.getWorkInfosForUniqueWorkFlow(ModelConfig.WORK_TAG)
                .firstOrNull()?.firstOrNull()

            workInfo?.state == WorkInfo.State.ENQUEUED ||
                   workInfo?.state == WorkInfo.State.RUNNING ||
                   workInfo?.state == WorkInfo.State.BLOCKED
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error checking if work is running: ${e.message}")
            false
        }
    }

    /**
     * Selects the best WorkInfo from the chain for observation.
     *
     * Priority:
     * 1. Running finalizer (most relevant for UI)
     * 2. Succeeded finalizer (terminal)
     * 3. Failed worker (any stage)
     * 4. Running downloader (still in progress)
     * 5. Succeeded downloader (intermediate - waiting for finalizer)
     * 6. Enqueued/blocked worker (waiting to start)
     */
    private fun selectBestWorkInfo(workInfos: List<WorkInfo>): WorkInfo? {
        if (workInfos.isEmpty()) return null

        fun getStage(info: WorkInfo): String? {
            return runCatching { info.outputData.getString(DownloadWorkKeys.KEY_WORKER_STAGE) }.getOrNull()
                ?: runCatching { info.progress.getString(DownloadWorkKeys.KEY_WORKER_STAGE) }.getOrNull()
        }

        val finalizeWorkers = workInfos.filter { getStage(it) == DownloadWorkKeys.STAGE_FINALIZE }
        val downloadWorkers = workInfos.filter { getStage(it) == DownloadWorkKeys.STAGE_DOWNLOAD }
        val otherWorkers = workInfos.filter { getStage(it) == null }

        // Priority 1: Running finalizer
        finalizeWorkers.find { it.state == WorkInfo.State.RUNNING }?.let { return it }

        // Priority 2: Succeeded finalizer (terminal)
        finalizeWorkers.find { it.state == WorkInfo.State.SUCCEEDED }?.let { return it }

        // Priority 3: Failed worker (any stage)
        workInfos.find { it.state == WorkInfo.State.FAILED }?.let { return it }

        // Priority 4: Running downloader
        downloadWorkers.find { it.state == WorkInfo.State.RUNNING }?.let { return it }
        otherWorkers.find { it.state == WorkInfo.State.RUNNING }?.let { return it }

        // Priority 5: Succeeded downloader (intermediate)
        downloadWorkers.find { it.state == WorkInfo.State.SUCCEEDED }?.let { return it }

        // Priority 6: Enqueued/blocked worker
        workInfos.find { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }?.let { return it }

        // Priority 7: Any non-cancelled worker
        return workInfos.firstOrNull { it.state != WorkInfo.State.CANCELLED }
    }
}
