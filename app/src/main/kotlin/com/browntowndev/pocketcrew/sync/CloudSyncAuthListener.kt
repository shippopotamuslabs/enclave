package com.browntowndev.pocketcrew.sync

import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort

/**
 * Application-level policy for when encrypted Firestore sync may run. Extracted for unit tests
 * of [com.browntowndev.pocketcrew.PocketCrewApplication] listener wiring.
 */
object CloudSyncAuthListener {
    fun onAuthAndVaultStateChanged(
        authState: AuthState,
        isVaultUnlocked: Boolean,
        cloudSyncPort: CloudSyncPort,
    ) {
        when (authState) {
            is AuthState.Authenticated -> {
                cloudSyncPort.stopListening()
                if (isVaultUnlocked) {
                    cloudSyncPort.startListening()
                }
            }
            is AuthState.Unauthenticated -> {
                cloudSyncPort.stopListening()
            }
            else -> Unit
        }
    }
}
