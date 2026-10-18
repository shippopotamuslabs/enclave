package com.browntowndev.enclave.core.data.download

import androidx.work.Data
import androidx.work.WorkInfo
import com.browntowndev.enclave.domain.model.download.DownloadKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DownloadWorkChainObserverTest {
    private fun workInfo(
        state: WorkInfo.State,
        tags: Set<String> = emptySet(),
        outputData: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
    ): WorkInfo = WorkInfo(UUID.randomUUID(), state, tags, outputData, progress, 0, 0)

    @Test
    fun intermediateDownloadSuccess_isNotTerminal() {
        val workInfo =
            workInfo(
                state = WorkInfo.State.SUCCEEDED,
                tags = setOf(ModelDownloadWorker::class.java.name),
                outputData =
                    Data
                        .Builder()
                        .putString(DownloadWorkKeys.KEY_WORKER_STAGE, DownloadWorkKeys.STAGE_DOWNLOAD)
                        .build(),
            )

        assertTrue(DownloadWorkChainObserver.isIntermediateDownloadSuccess(workInfo))
        assertFalse(DownloadWorkChainObserver.isTerminalSuccess(workInfo))
        assertTrue(DownloadWorkChainObserver.isFinalizing(workInfo))
    }

    @Test
    fun finalizerSuccess_isTerminal() {
        val workInfo =
            workInfo(
                state = WorkInfo.State.SUCCEEDED,
                tags = setOf(DownloadFinalizeWorker::class.java.name),
                outputData =
                    Data
                        .Builder()
                        .putString(DownloadWorkKeys.KEY_WORKER_STAGE, DownloadWorkKeys.STAGE_FINALIZE)
                        .build(),
            )

        assertTrue(DownloadWorkChainObserver.isTerminalSuccess(workInfo))
        assertFalse(DownloadWorkChainObserver.isIntermediateDownloadSuccess(workInfo))
    }

    @Test
    fun readPostProcessProgress_returnsFractionWhenPresent() {
        val workInfo =
            workInfo(
                state = WorkInfo.State.RUNNING,
                progress =
                    Data
                        .Builder()
                        .putLong(DownloadKey.POST_PROCESS_BYTES.key, 500L)
                        .putLong(DownloadKey.POST_PROCESS_TOTAL.key, 1_000L)
                        .build(),
            )

        assertEquals(0.5f, DownloadWorkChainObserver.readPostProcessProgress(workInfo))
    }

    @Test
    fun readPostProcessProgress_returnsNullWhenTotalMissing() {
        val workInfo = workInfo(state = WorkInfo.State.RUNNING, progress = Data.EMPTY)

        assertNull(DownloadWorkChainObserver.readPostProcessProgress(workInfo))
    }
}
