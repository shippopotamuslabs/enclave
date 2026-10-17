package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.repository.SyncOrchestrator
import com.browntowndev.enclave.domain.port.repository.SyncProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SyncOrchestrator] fake (ENC-28); start behavior is a test-supplied handler. */
class FakeSyncOrchestrator : SyncOrchestrator {
    var syncProgressFlow: Flow<SyncProgress?> = MutableStateFlow(null)
    override val syncProgress: Flow<SyncProgress?>
        get() = syncProgressFlow

    var startHandler: (suspend (Boolean) -> Result<Unit>)? = null
    val startCalls = mutableListOf<Boolean>()

    override suspend fun startInitialSync(uploadLocalFirst: Boolean): Result<Unit> {
        startCalls += uploadLocalFirst
        return startHandler?.invoke(uploadLocalFirst) ?: Result.success(Unit)
    }
}
