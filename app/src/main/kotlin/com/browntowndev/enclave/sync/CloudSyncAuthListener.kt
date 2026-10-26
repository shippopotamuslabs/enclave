package com.browntowndev.enclave.sync

import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort

/**
 * Application-level policy for when encrypted Firestore sync must stop. Listener **start** is
 * owned by [com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase]
 * so unlock/adopt paths do not race this gate. Extracted for unit tests of
 * [com.browntowndev.enclave.EnclaveApplication] wiring.
 *
 * Only [EntitlementState.NotEntitled] stops sync; [EntitlementState.Unknown] keeps the existing
 * retryable-error behavior and [EntitlementState.SignedOut] keeps the existing sign-in gate.
 */
object CloudSyncAuthListener {
    fun onAuthAndVaultStateChanged(
        authState: AuthState,
        isVaultUnlocked: Boolean,
        entitlementState: EntitlementState,
        cloudSyncPort: CloudSyncPort,
    ) {
        if (entitlementState is EntitlementState.NotEntitled) {
            cloudSyncPort.stopListening()
            return
        }
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
