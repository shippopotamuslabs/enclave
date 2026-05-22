package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import javax.inject.Inject

/**
 * Uploads local data when requested, replays the remote sync-event feed until drained,
 * then starts the live Firestore listener. Centralizes the post-unlock sync contract
 * shared by main and settings entry points. Fails (without starting the listener) when
 * any step returns a failure, so callers get exact upstream error propagation.
 */
class StartCloudSyncListeningUseCase
    @Inject
    constructor(
        private val cloudSyncPort: CloudSyncPort,
    ) {
        suspend operator fun invoke(uploadLocalFirst: Boolean = true): Result<Unit> {
            if (uploadLocalFirst) {
                val uploadResult = cloudSyncPort.syncAllLocalDataToCloud()
                if (uploadResult.isFailure) {
                    return Result.failure(
                        uploadResult.exceptionOrNull() ?: IllegalStateException("upload failed"),
                    )
                }
            }
            var safety = 0
            while (true) {
                val replayResult = cloudSyncPort.syncRemoteChangesFromCloud()
                if (replayResult.isFailure) {
                    return Result.failure(
                        replayResult.exceptionOrNull() ?: IllegalStateException("replay failed"),
                    )
                }
                val replay = replayResult.getOrThrow()
                if (!replay.hasMore) break
                safety += 1
                if (safety > MAX_REPLAY_PAGES) {
                    return Result.failure(IllegalStateException("replay exceeded max pages"))
                }
            }
            cloudSyncPort.startListening()
            return Result.success(Unit)
        }

        private companion object {
            const val MAX_REPLAY_PAGES = 10_000
        }
    }
