package com.browntowndev.enclave.core.data.remote.firestore

internal data class FirestoreSyncFeedStatus(
    val nextSequence: Long,
    val feedInitialized: Boolean,
    val feedEpoch: Long,
    val oldestRetainedSequence: Long,
    val schemaVersion: Long,
)
