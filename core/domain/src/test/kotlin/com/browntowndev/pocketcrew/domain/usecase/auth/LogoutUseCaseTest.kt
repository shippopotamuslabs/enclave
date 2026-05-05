package com.browntowndev.pocketcrew.domain.usecase.auth

import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LogoutUseCaseTest {

    private val authManager = mockk<AuthManager>()
    private val cleanupRepository = mockk<CleanupRepository>()
    private val cloudSyncPort = mockk<CloudSyncPort>(relaxed = true)
    private val logoutUseCase = LogoutUseCase(authManager, cleanupRepository, cloudSyncPort)

    @Test
    fun `invoke signs out and clears data on success`() = runTest {
        coEvery { authManager.signOut() } returns Result.success(Unit)
        coEvery { cleanupRepository.clearAllData() } returns Unit
        every { cloudSyncPort.stopListening() } returns Unit

        logoutUseCase()

        coVerify { authManager.signOut() }
        coVerify { cloudSyncPort.stopListening() }
        coVerify { cleanupRepository.clearAllData() }
    }

    @Test
    fun `invoke does not clear data on sign out failure`() = runTest {
        coEvery { authManager.signOut() } returns Result.failure(Exception("Sign out failed"))

        logoutUseCase()

        coVerify { authManager.signOut() }
        coVerify(exactly = 0) { cleanupRepository.clearAllData() }
    }
}
