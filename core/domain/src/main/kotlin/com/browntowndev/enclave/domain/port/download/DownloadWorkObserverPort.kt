package com.browntowndev.enclave.domain.port.download

import com.browntowndev.enclave.domain.model.download.DownloadWorkSnapshot
import kotlinx.coroutines.flow.Flow

/** Observes the unique download work chain as domain snapshots (ENC-11). */
interface DownloadWorkObserverPort {
    fun observeDownloadWork(): Flow<DownloadWorkSnapshot?>
}
