package com.browntowndev.pocketcrew.sync

import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort

/**
 * Application-level policy for when encrypted Firestore sync must stop. Listener **start** is
 * owned by [com.browntowndev.pocketcrew.domain.usecase.security.StartCloudSyncListeningUseCase]
 * so unlock/adopt paths do not race this gate. Extracted for unit tests of
 * [com.browntowndev.pocketcrew.PocketCrewApplication] wiring.
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
