package com.browntowndev.enclave.sync

import com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * BUG-04 — listener start is owned by the use case; Application must not race it on unlock.
 * Drives a REAL [StartCloudSyncListeningUseCase] against a mocked [CloudSyncPort] so the test
 * fails if the use case does not invoke `syncRemoteChangesFromCloud()` strictly before
 * `startListening()`.
 */
class CloudSyncStartOrderingTest {
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
    private val authenticated = AuthState.Authenticated(UserProfile("uid", "User", "u@example.com", null))

    @Test
    fun `vault unlock starts listener only through use case not application gate`() =
        runTest {
            coEvery { cloudSyncPort.syncAllLocalDataToCloud() } returns Result.success(Unit)
            coEvery { cloudSyncPort.syncRemoteChangesFromCloud() } returns
                Result.success(
                    CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0,
                        hasMore = false,
                    ),
                )
            every { cloudSyncPort.startListening() } returns Unit
            val realUseCase = StartCloudSyncListeningUseCase(cloudSyncPort)

            CloudSyncAuthListener.onAuthAndVaultStateChanged(authenticated, isVaultUnlocked = true, cloudSyncPort)
            verify(exactly = 0) { cloudSyncPort.startListening() }

            val result = realUseCase(uploadLocalFirst = false)
            assert(result.isSuccess)

            // The use case must call replay before listen; otherwise the live listener will race
            // remote events that have not yet been applied to Room.
            coVerifyOrder {
                cloudSyncPort.syncRemoteChangesFromCloud()
                cloudSyncPort.startListening()
            }
            coVerify(exactly = 0) { cloudSyncPort.syncAllLocalDataToCloud() }
        }
}
