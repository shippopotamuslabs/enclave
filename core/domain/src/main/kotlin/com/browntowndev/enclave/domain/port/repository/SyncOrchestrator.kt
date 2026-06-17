package com.browntowndev.enclave.domain.port.repository

import kotlinx.coroutines.flow.Flow

interface SyncOrchestrator {
    /**
     * Emits the current progress of the initial sync work, if active.
     */
    val syncProgress: Flow<SyncProgress?>

    /**
     * Triggers the initial cloud sync. Suspends until complete.
     * Returns success when the sync completes successfully.
     */
    suspend fun startInitialSync(uploadLocalFirst: Boolean): Result<Unit>
}

data class SyncProgress(
    val fraction: Float?,
    val status: String?,
)
