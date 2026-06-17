package com.browntowndev.enclave.core.data.download

import androidx.work.Data
import androidx.work.WorkInfo
import com.browntowndev.enclave.domain.model.download.DownloadKey
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadWorkChainObserverTest {
    @Test
    fun intermediateDownloadSuccess_isNotTerminal() {
        val workInfo =
            mockk<WorkInfo> {
                every { state } returns WorkInfo.State.SUCCEEDED
                every { tags } returns setOf(ModelDownloadWorker::class.java.name)
                every { outputData } returns
                    Data
                        .Builder()
                        .putString(DownloadWorkKeys.KEY_WORKER_STAGE, DownloadWorkKeys.STAGE_DOWNLOAD)
                        .build()
                every { progress } returns Data.EMPTY
            }

        assertTrue(DownloadWorkChainObserver.isIntermediateDownloadSuccess(workInfo))
        assertFalse(DownloadWorkChainObserver.isTerminalSuccess(workInfo))
        assertTrue(DownloadWorkChainObserver.isFinalizing(workInfo))
    }

    @Test
    fun finalizerSuccess_isTerminal() {
        val workInfo =
            mockk<WorkInfo> {
                every { state } returns WorkInfo.State.SUCCEEDED
                every { tags } returns setOf(DownloadFinalizeWorker::class.java.name)
                every { outputData } returns
                    Data
                        .Builder()
                        .putString(DownloadWorkKeys.KEY_WORKER_STAGE, DownloadWorkKeys.STAGE_FINALIZE)
                        .build()
                every { progress } returns Data.EMPTY
            }

        assertTrue(DownloadWorkChainObserver.isTerminalSuccess(workInfo))
        assertFalse(DownloadWorkChainObserver.isIntermediateDownloadSuccess(workInfo))
    }

    @Test
    fun readPostProcessProgress_returnsFractionWhenPresent() {
        val workInfo =
            mockk<WorkInfo> {
                every { progress } returns
                    Data
                        .Builder()
                        .putLong(DownloadKey.POST_PROCESS_BYTES.key, 500L)
                        .putLong(DownloadKey.POST_PROCESS_TOTAL.key, 1_000L)
                        .build()
            }

        assertEquals(0.5f, DownloadWorkChainObserver.readPostProcessProgress(workInfo))
    }

    @Test
    fun readPostProcessProgress_returnsNullWhenTotalMissing() {
        val workInfo =
            mockk<WorkInfo> {
                every { progress } returns Data.EMPTY
            }

        assertNull(DownloadWorkChainObserver.readPostProcessProgress(workInfo))
    }
}
