package com.browntowndev.enclave.core.testing.download

import com.browntowndev.enclave.domain.model.download.DownloadWorkSnapshot
import com.browntowndev.enclave.domain.port.download.DownloadWorkObserverPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeDownloadWorkObserverPort(
    initial: DownloadWorkSnapshot? = null,
) : DownloadWorkObserverPort {
    private val state = MutableStateFlow(initial)

    override fun observeDownloadWork(): Flow<DownloadWorkSnapshot?> = state.asStateFlow()

    fun seed(snapshot: DownloadWorkSnapshot?) {
        state.value = snapshot
    }
}
