package com.browntowndev.enclave.domain.model.sync

data class CloudSyncReplayProgress(
    val appliedCount: Long,
    val totalEstimate: Long?,
    val progressFraction: Float?, // null represents indeterminate state
    val status: String,
)
