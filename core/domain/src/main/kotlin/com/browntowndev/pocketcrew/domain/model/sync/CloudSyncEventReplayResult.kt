package com.browntowndev.pocketcrew.domain.model.sync

data class CloudSyncEventReplayResult(
    val eventsApplied: Int,
    val lastAppliedSequence: Long,
    val hasMore: Boolean,
)
