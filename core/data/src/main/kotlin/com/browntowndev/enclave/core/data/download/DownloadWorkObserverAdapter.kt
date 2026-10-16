package com.browntowndev.enclave.core.data.download

import androidx.work.WorkInfo
import com.browntowndev.enclave.core.data.repository.DownloadWorkRepository
import com.browntowndev.enclave.domain.model.download.DownloadKey
import com.browntowndev.enclave.domain.model.download.DownloadWorkLifecycle
import com.browntowndev.enclave.domain.model.download.DownloadWorkSnapshot
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.DownloadWorkObserverPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Maps WorkManager's WorkInfo chain to domain snapshots (ENC-11). */
class DownloadWorkObserverAdapter @Inject constructor(
    private val repository: DownloadWorkRepository,
) : DownloadWorkObserverPort {
    override fun observeDownloadWork(): Flow<DownloadWorkSnapshot?> =
        repository.observeDownloadProgress().map { workInfo -> workInfo?.toSnapshot() }

    private fun WorkInfo.toSnapshot(): DownloadWorkSnapshot =
        DownloadWorkSnapshot(
            id = id.toString(),
            lifecycle =
                when (state) {
                    WorkInfo.State.ENQUEUED -> DownloadWorkLifecycle.ENQUEUED
                    WorkInfo.State.RUNNING -> DownloadWorkLifecycle.RUNNING
                    WorkInfo.State.SUCCEEDED -> DownloadWorkLifecycle.SUCCEEDED
                    WorkInfo.State.FAILED -> DownloadWorkLifecycle.FAILED
                    WorkInfo.State.BLOCKED -> DownloadWorkLifecycle.BLOCKED
                    WorkInfo.State.CANCELLED -> DownloadWorkLifecycle.CANCELLED
                },
            isTerminalSuccess = DownloadWorkChainObserver.isTerminalSuccess(this),
            isTerminalFailure = DownloadWorkChainObserver.isTerminalFailure(this),
            isIntermediateDownloadSuccess = DownloadWorkChainObserver.isIntermediateDownloadSuccess(this),
            isFinalizing = DownloadWorkChainObserver.isFinalizing(this),
            postProcessProgress = DownloadWorkChainObserver.readPostProcessProgress(this),
            aggregateProgress = DownloadWorkChainObserver.readDownloadAggregateProgress(this),
            unassignedFileProgress =
                progress
                    .getStringArray(DownloadKey.FILES_PROGRESS.key)
                    ?.find { it.contains(ModelType.UNASSIGNED.apiValue) },
            progressPercent = progress.getLong(DownloadKey.PROGRESS.key, -1L),
            totalBytes = progress.getLong(DownloadKey.TOTAL_BYTES.key, -1L),
            downloadedBytes = progress.getLong(DownloadKey.BYTES_DOWNLOADED.key, -1L),
        )
}
