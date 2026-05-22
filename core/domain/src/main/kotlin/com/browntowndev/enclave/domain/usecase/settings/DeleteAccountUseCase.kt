package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.auth.AuthManager
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import javax.inject.Inject

/**
 * Purges cloud account data then signs out locally.
 *
 * Cloud purge failures short-circuit so [AuthManager.signOut] never runs on failure.
 */
class DeleteAccountUseCase
    @Inject
    constructor(
        private val cloudSyncPort: CloudSyncPort,
        private val authManager: AuthManager,
    ) {
        suspend operator fun invoke(): Result<Unit> =
            cloudSyncPort.deleteUserAccount().fold(
                onSuccess = { authManager.signOut() },
                onFailure = { Result.failure(it) },
            )
    }
