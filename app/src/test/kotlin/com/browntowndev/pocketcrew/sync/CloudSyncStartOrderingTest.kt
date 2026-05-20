package com.browntowndev.pocketcrew.sync

import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.usecase.security.StartCloudSyncListeningUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * BUG-04 — listener start is owned by the use case; Application must not race it on unlock.
 */
class CloudSyncStartOrderingTest {
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
    private val startCloudSyncListeningUseCase = mockk<StartCloudSyncListeningUseCase>(relaxed = true)
    private val authenticated = AuthState.Authenticated(UserProfile("uid", "User", "u@example.com", null))

    @Test
    fun `vault unlock starts listener only through use case not application gate`() =
        runTest {
            coEvery { startCloudSyncListeningUseCase(uploadLocalFirst = false) } returns Result.success(Unit)

            CloudSyncAuthListener.onAuthAndVaultStateChanged(authenticated, isVaultUnlocked = true, cloudSyncPort)
            verify(exactly = 0) { cloudSyncPort.startListening() }

            startCloudSyncListeningUseCase(uploadLocalFirst = false)

            coVerify(exactly = 1) { startCloudSyncListeningUseCase(uploadLocalFirst = false) }
            verify(exactly = 0) { cloudSyncPort.startListening() }
        }
}
