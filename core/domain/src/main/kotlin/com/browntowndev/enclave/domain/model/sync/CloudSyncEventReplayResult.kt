package com.browntowndev.enclave.domain.model.sync

data class CloudSyncEventReplayResult(
    val eventsApplied: Int,
    val lastAppliedSequence: Long,
    val hasMore: Boolean,
)
