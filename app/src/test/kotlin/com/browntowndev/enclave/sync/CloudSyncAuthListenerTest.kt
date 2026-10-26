package com.browntowndev.enclave.sync

import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * BUG-04 — Application-level cloud sync gate stops listeners when unsafe; start is owned by
 * [com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase].
 * Only [EntitlementState.NotEntitled] stops sync; [EntitlementState.Unknown] keeps the existing
 * retryable-error behavior and [EntitlementState.SignedOut] keeps the existing sign-in gate.
 */
class CloudSyncAuthListenerTest {
    private val cloudSyncPort = FakeCloudSyncPort()
    private val authenticated = AuthState.Authenticated(UserProfile("uid", "User", "u@example.com", null))

    @Test
    fun `authenticated and vault unlocked does not start or stop listening`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(
            authenticated,
            isVaultUnlocked = true,
            EntitlementState.Entitled(),
            cloudSyncPort,
        )

        assertEquals(0, cloudSyncPort.count("stopListening"))
        assertEquals(0, cloudSyncPort.count("startListening"))
    }

    @Test
    fun `authenticated but vault locked stops listening without starting`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(
            authenticated,
            isVaultUnlocked = false,
            EntitlementState.Entitled(),
            cloudSyncPort,
        )

        assertEquals(1, cloudSyncPort.count("stopListening"))
        assertEquals(0, cloudSyncPort.count("startListening"))
    }

    @Test
    fun `unauthenticated stops listening`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(
            AuthState.Unauthenticated,
            isVaultUnlocked = true,
            EntitlementState.SignedOut,
            cloudSyncPort,
        )

        assertEquals(1, cloudSyncPort.count("stopListening"))
        assertEquals(0, cloudSyncPort.count("startListening"))
    }

    @Test
    fun `auth loading does not start listening`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(
            AuthState.Loading,
            isVaultUnlocked = true,
            EntitlementState.Unknown,
            cloudSyncPort,
        )

        assertEquals(0, cloudSyncPort.count("startListening"))
    }

    @Test
    fun `lapse mid-session stops listening without starting`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(
            authenticated,
            isVaultUnlocked = true,
            EntitlementState.NotEntitled,
            cloudSyncPort,
        )

        assertEquals(1, cloudSyncPort.count("stopListening"))
        assertEquals(0, cloudSyncPort.count("startListening"))
    }

    @Test
    fun `unknown entitlement keeps existing behavior`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(
            authenticated,
            isVaultUnlocked = true,
            EntitlementState.Unknown,
            cloudSyncPort,
        )

        assertEquals(0, cloudSyncPort.count("stopListening"))
        assertEquals(0, cloudSyncPort.count("startListening"))
    }

    @Test
    fun `signed out entitlement keeps existing unauthenticated behavior`() {
        CloudSyncAuthListener.onAuthAndVaultStateChanged(
            AuthState.Unauthenticated,
            isVaultUnlocked = true,
            EntitlementState.SignedOut,
            cloudSyncPort,
        )

        assertEquals(1, cloudSyncPort.count("stopListening"))
        assertEquals(0, cloudSyncPort.count("startListening"))
    }
}
