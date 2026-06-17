package com.browntowndev.enclave.feature.download

/**
 * Refreshes the download plan from remote catalog before retrying failed downloads.
 * Implemented in the app layer to avoid a Hilt cycle with [ModelDownloadOrchestratorPort].
 */
fun interface DownloadPlanRefresher {
    suspend fun refresh()
}
