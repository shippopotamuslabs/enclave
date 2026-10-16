package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.download.DownloadWorkSnapshot
import com.browntowndev.enclave.domain.port.download.DownloadWorkObserverPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDownloadWorkUseCase @Inject constructor(
    private val port: DownloadWorkObserverPort,
) {
    operator fun invoke(): Flow<DownloadWorkSnapshot?> = port.observeDownloadWork()
}
