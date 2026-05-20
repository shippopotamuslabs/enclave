package com.browntowndev.pocketcrew.sync

import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * TEST-01 — verifies [PocketCrewApplication] cloud sync gate: listeners start only when
 * authenticated and vault unlocked.
 */
class CloudSyncAuthListenerTest {
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
    private val authenticated = AuthState.Authenticated(UserProfile("uid", "User", "u@example.com", null))

    @Test
    fun `authenticated and vault unlocked starts listening`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(authenticated, isVaultUnlocked = true, cloudSyncPort)

        verify(exactly = 1) { cloudSyncPort.stopListening() }
        verify(exactly = 1) { cloudSyncPort.startListening() }
    }

    @Test
    fun `authenticated but vault locked does not start listening`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(authenticated, isVaultUnlocked = false, cloudSyncPort)

        verify(exactly = 1) { cloudSyncPort.stopListening() }
        verify(exactly = 0) { cloudSyncPort.startListening() }
    }

    @Test
    fun `unauthenticated stops listening`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(AuthState.Unauthenticated, isVaultUnlocked = true, cloudSyncPort)

        verify(exactly = 1) { cloudSyncPort.stopListening() }
        verify(exactly = 0) { cloudSyncPort.startListening() }
    }

    @Test
    fun `auth loading does not start listening`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(AuthState.Loading, isVaultUnlocked = true, cloudSyncPort)

        verify(exactly = 0) { cloudSyncPort.startListening() }
    }
}
