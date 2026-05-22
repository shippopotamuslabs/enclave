package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.auth.AuthManager
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteAccountUseCaseTest {
    private val cloudSyncPort = mockk<CloudSyncPort>()
    private val authManager = mockk<AuthManager>()
    private val useCase = DeleteAccountUseCase(cloudSyncPort, authManager)

    @Test
    fun `S8 cloud purge succeeds then signOut runs`() =
        runTest {
            coEvery { cloudSyncPort.deleteUserAccount() } returns Result.success(Unit)
            coEvery { authManager.signOut() } returns Result.success(Unit)

            val result = useCase()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { authManager.signOut() }
        }

    @Test
    fun `S9 signOut failure is returned after successful purge`() =
        runTest {
            coEvery { cloudSyncPort.deleteUserAccount() } returns Result.success(Unit)
            coEvery { authManager.signOut() } returns Result.failure(Exception("session expired"))

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals("session expired", result.exceptionOrNull()?.message)
            coVerify(exactly = 1) { authManager.signOut() }
        }

    @Test
    fun `E5 cloud purge failure skips signOut`() =
        runTest {
            coEvery { cloudSyncPort.deleteUserAccount() } returns Result.failure(Exception("network error"))

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals("network error", result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { authManager.signOut() }
        }
}
