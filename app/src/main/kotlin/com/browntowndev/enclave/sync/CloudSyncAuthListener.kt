package com.browntowndev.enclave.sync

import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort

/**
 * Application-level policy for when encrypted Firestore sync must stop. Listener **start** is
 * owned by [com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase]
 * so unlock/adopt paths do not race this gate. Extracted for unit tests of
 * [com.browntowndev.enclave.EnclaveApplication] wiring.
 */
object CloudSyncAuthListener {
    fun onAuthAndVaultStateChanged(
        authState: AuthState,
        isVaultUnlocked: Boolean,
        cloudSyncPort: CloudSyncPort,
    ) {
        when (authState) {
            is AuthState.Authenticated -> {
                if (!isVaultUnlocked) {
                    cloudSyncPort.stopListening()
                }
            }
            is AuthState.Unauthenticated -> {
                cloudSyncPort.stopListening()
            }
            else -> Unit
        }
    }
}
