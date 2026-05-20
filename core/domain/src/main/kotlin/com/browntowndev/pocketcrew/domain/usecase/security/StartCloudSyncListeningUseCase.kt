package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import javax.inject.Inject

/**
 * Uploads local data when requested, then starts the Firestore listener. Centralizes the
 * post-unlock sync contract shared by main and settings entry points.
 */
class StartCloudSyncListeningUseCase
    @Inject
    constructor(
        private val cloudSyncPort: CloudSyncPort,
    ) {
        suspend operator fun invoke(uploadLocalFirst: Boolean = true): Result<Unit> =
            runCatching {
                if (uploadLocalFirst) {
                    runCatching { cloudSyncPort.syncAllLocalDataToCloud() }
                }
                cloudSyncPort.startListening()
            }
    }
