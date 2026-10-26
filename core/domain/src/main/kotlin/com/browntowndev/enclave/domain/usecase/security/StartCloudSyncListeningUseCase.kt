package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.model.sync.CloudSyncReplayProgress
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.repository.EntitlementPort
import javax.inject.Inject

/**
 * Uploads local data when requested, replays the remote sync-event feed until drained,
 * then starts the live Firestore listener. Centralizes the post-unlock sync contract
 * shared by main and settings entry points.
 *
 * Reads [EntitlementPort.entitlementState] before touching [CloudSyncPort]: non-subscribers
 * return [StartCloudSyncResult.SubscriptionRequired] so callers stop cleanly instead of
 * retrying against server-side denials; unknown entitlement fails (without starting the
 * listener) so callers keep the existing retryable-error behavior.
 */
open class StartCloudSyncListeningUseCase
    @Inject
    constructor(
        private val cloudSyncPort: CloudSyncPort,
        private val entitlementPort: EntitlementPort,
    ) {
        @Suppress("ReturnCount")
        open suspend operator fun invoke(
            uploadLocalFirst: Boolean = true,
            onProgress: (suspend (CloudSyncReplayProgress) -> Unit)? = null,
        ): StartCloudSyncResult {
            when (entitlementPort.entitlementState.value) {
                is EntitlementState.NotEntitled -> return StartCloudSyncResult.SubscriptionRequired
                is EntitlementState.Unknown ->
                    return StartCloudSyncResult.Failed(
                        IllegalStateException("Entitlement state unknown"),
                    )
                is EntitlementState.SignedOut ->
                    return StartCloudSyncResult.Failed(
                        IllegalStateException("Signed out"),
                    )
                is EntitlementState.Entitled -> Unit
            }

            if (uploadLocalFirst) {
                val uploadResult = cloudSyncPort.syncAllLocalDataToCloud()
                if (uploadResult.isFailure) {
                    return StartCloudSyncResult.Failed(
                        uploadResult.exceptionOrNull() ?: IllegalStateException("upload failed"),
                    )
                }
            }

            val totalEstimate: Long? =
                runCatching {
                    cloudSyncPort.getRemainingEventsCount().getOrNull()?.takeIf { it > 0L }
                }.getOrNull()

            // Report initial progress state
            val initialProgress =
                if (totalEstimate != null) {
                    CloudSyncReplayProgress(
                        appliedCount = 0L,
                        totalEstimate = totalEstimate,
                        progressFraction = 0f,
                        status = "Syncing updates (0/$totalEstimate)...",
                    )
                } else {
                    CloudSyncReplayProgress(
                        appliedCount = 0L,
                        totalEstimate = null,
                        progressFraction = null,
                        status = "Checking for updates...",
                    )
                }
            onProgress?.invoke(initialProgress)

            var safety = 0
            var cumulativeApplied = 0L
            while (true) {
                val replayResult = cloudSyncPort.syncRemoteChangesFromCloud()
                if (replayResult.isFailure) {
                    return StartCloudSyncResult.Failed(
                        replayResult.exceptionOrNull() ?: IllegalStateException("replay failed"),
                    )
                }
                val replay = replayResult.getOrThrow()
                cumulativeApplied += replay.eventsApplied

                val currentEstimate =
                    if (totalEstimate != null) {
                        maxOf(totalEstimate, cumulativeApplied)
                    } else {
                        null
                    }

                val fraction =
                    if (currentEstimate != null && currentEstimate > 0) {
                        val f = cumulativeApplied.toFloat() / currentEstimate
                        if (f > 1.0f) 1.0f else f
                    } else {
                        null
                    }

                val status =
                    if (currentEstimate != null) {
                        "Syncing updates ($cumulativeApplied/$currentEstimate)..."
                    } else {
                        "Syncing updates..."
                    }

                onProgress?.invoke(
                    CloudSyncReplayProgress(
                        appliedCount = cumulativeApplied,
                        totalEstimate = currentEstimate,
                        progressFraction = fraction,
                        status = status,
                    ),
                )

                if (!replay.hasMore) break
                safety += 1
                if (safety > MAX_REPLAY_PAGES) {
                    return StartCloudSyncResult.Failed(IllegalStateException("replay exceeded max pages"))
                }
            }

            val bootstrapResult = cloudSyncPort.bootstrapFeatureSettings()
            if (bootstrapResult.isFailure) {
                return StartCloudSyncResult.Failed(
                    bootstrapResult.exceptionOrNull()
                        ?: IllegalStateException("feature settings bootstrap failed"),
                )
            }

            cloudSyncPort.startListening()
            return StartCloudSyncResult.Started
        }

        private companion object {
            const val MAX_REPLAY_PAGES = 10_000
        }
    }
