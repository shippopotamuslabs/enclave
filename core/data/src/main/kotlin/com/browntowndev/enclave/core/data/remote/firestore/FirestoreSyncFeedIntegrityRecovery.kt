package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.domain.port.inference.LoggingPort

/**
 * Recovers from non-dense sync-event feeds by verifying the gap against server metadata,
 * reconciling Room from canonical entity documents, and advancing the cursor to the server
 * tail so replay/live delivery can resume.
 */
internal class FirestoreSyncFeedIntegrityRecovery(
    private val metaClient: FirestoreSyncFeedMetaClient,
    private val callableClient: FirestoreSyncFeedCallableClient,
    private val entityReconciler: FirestoreSyncEntityReconciler,
    private val syncStateStore: FirestoreSyncStateStore,
    private val loggingPort: LoggingPort,
) {
    suspend fun ensureFeedBootstrapped(userId: String) {
        val status = metaClient.readFeedStatus(userId) ?: return
        if (status.feedInitialized) return
        val backfill = callableClient.backfillIfNeeded(userId) ?: return
        loggingPort.info(
            FirestoreSyncContext.TAG,
            "Triggered sync feed backfill for uid=$userId status=${backfill.status} " +
                "events=${backfill.eventsWritten}",
        )
    }

    suspend fun recoverFromSequenceGap(
        userId: String,
        expectedSequence: Long,
    ) {
        if (metaClient.awaitSyncEvent(userId, expectedSequence)) {
            loggingPort.debug(
                FirestoreSyncContext.TAG,
                "Sequence $expectedSequence arrived during gap verification; skipping reconciliation",
            )
            return
        }

        val status =
            metaClient.readFeedStatus(userId)
                ?: throw IllegalStateException("sync feed metadata missing for uid=$userId")

        if (expectedSequence >= status.nextSequence) {
            throw SyncFeedSequenceGapException(
                expectedSequence = expectedSequence,
                receivedSequence = expectedSequence + 1,
            )
        }

        if (expectedSequence < status.oldestRetainedSequence) {
            loggingPort.warning(
                FirestoreSyncContext.TAG,
                "Cursor $expectedSequence is before oldestRetainedSequence=${status.oldestRetainedSequence}; " +
                    "running entity-source reconciliation",
            )
        } else {
            loggingPort.warning(
                FirestoreSyncContext.TAG,
                "Verified missing sync event sequence $expectedSequence (nextSequence=${status.nextSequence}); " +
                    "running entity-source reconciliation",
            )
        }

        entityReconciler.reconcileFromCloudEntitySources(userId)
        val resumeSequence = (status.nextSequence - 1).coerceAtLeast(0L)
        syncStateStore.setLastAppliedSequence(userId, resumeSequence)
        syncStateStore.setFeedSchemaVersion(userId, status.feedEpoch)
        loggingPort.warning(
            FirestoreSyncContext.TAG,
            "Feed integrity recovery advanced cursor to $resumeSequence for uid=$userId",
        )
    }

    suspend fun recoverIfFeedEpochChanged(userId: String) {
        val status = metaClient.readFeedStatus(userId) ?: return
        val localEpoch = syncStateStore.feedSchemaVersion(userId)
        if (localEpoch == status.feedEpoch) return
        loggingPort.warning(
            FirestoreSyncContext.TAG,
            "Feed epoch changed local=$localEpoch server=${status.feedEpoch}; reconciling entity sources",
        )
        entityReconciler.reconcileFromCloudEntitySources(userId)
        val resumeSequence = (status.nextSequence - 1).coerceAtLeast(0L)
        syncStateStore.setLastAppliedSequence(userId, resumeSequence)
        syncStateStore.setFeedSchemaVersion(userId, status.feedEpoch)
    }
}
