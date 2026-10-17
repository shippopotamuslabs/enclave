package com.browntowndev.enclave.sync

import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * BUG-04 — listener start is owned by the use case; Application must not race it on unlock.
 * Drives a REAL [StartCloudSyncListeningUseCase] against a fake [FakeCloudSyncPort] so the test
 * fails if the use case does not invoke `syncRemoteChangesFromCloud()` strictly before
 * `startListening()`.
 */
class CloudSyncStartOrderingTest {
    private val cloudSyncPort = FakeCloudSyncPort()
    private val authenticated = AuthState.Authenticated(UserProfile("uid", "User", "u@example.com", null))

    @Test
    fun `vault unlock starts listener only through use case not application gate`() =
        runTest {
            val realUseCase = StartCloudSyncListeningUseCase(cloudSyncPort)

            CloudSyncAuthListener.onAuthAndVaultStateChanged(authenticated, isVaultUnlocked = true, cloudSyncPort)
            assertEquals(0, cloudSyncPort.count("startListening"))

            val result = realUseCase(uploadLocalFirst = false)
            assertTrue(result.isSuccess)

            // The use case must call replay before listen; otherwise the live listener will race
            // remote events that have not yet been applied to Room.
            val syncOrder = cloudSyncPort.calls.filter { it == "syncRemote" || it == "startListening" }
            assertEquals(listOf("syncRemote", "startListening"), syncOrder)
            assertEquals(0, cloudSyncPort.count("syncAll"))
        }
}
