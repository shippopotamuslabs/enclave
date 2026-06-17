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
        if (status.nextSequence > 0L) {
            // Existing accounts already have live trigger events; a full entity scan backfill would
            // duplicate work and block unlock for minutes. Replay those events instead.
            loggingPort.info(
                FirestoreSyncContext.TAG,
                "Skipping full backfill for uid=$userId; replaying ${status.nextSequence} existing events",
            )
            callableClient.markFeedInitializedIfNeeded(userId)
            return
        }
        loggingPort.info(FirestoreSyncContext.TAG, "Running sync feed backfill for uid=$userId")
        val backfill = callableClient.backfillIfNeeded(userId) ?: return
        loggingPort.info(
            FirestoreSyncContext.TAG,
            "Sync feed backfill finished for uid=$userId status=${backfill.status} " +
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
        if (localEpoch == 0L) {
            loggingPort.info(
                FirestoreSyncContext.TAG,
                "Adopting server feed epoch ${status.feedEpoch} for uid=$userId without reconciliation",
            )
            syncStateStore.setFeedSchemaVersion(userId, status.feedEpoch)
            return
        }
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
