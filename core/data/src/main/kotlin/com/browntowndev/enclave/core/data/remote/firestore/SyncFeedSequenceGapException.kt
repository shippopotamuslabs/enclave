package com.browntowndev.enclave.core.data.remote.firestore

/**
 * Thrown when the sync-event feed is not dense at the consumer cursor. Callers may attempt
 * [FirestoreSyncFeedIntegrityRecovery.recoverFromSequenceGap] before failing permanently.
 */
internal class SyncFeedSequenceGapException(
    val expectedSequence: Long,
    val receivedSequence: Long,
) : IllegalStateException(
        "sync event sequence gap: expected $expectedSequence but received $receivedSequence",
    )
