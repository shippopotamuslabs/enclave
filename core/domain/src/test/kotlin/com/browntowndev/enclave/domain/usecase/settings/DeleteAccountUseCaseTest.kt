package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeAuthManager
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteAccountUseCaseTest {
    private val cloudSyncPort = FakeCloudSyncPort()
    private val authManager = FakeAuthManager()
    private val useCase = DeleteAccountUseCase(cloudSyncPort, authManager)

    @Test
    fun `S8 cloud purge succeeds then signOut runs`() =
        runTest {
            val result = useCase()

            assertTrue(result.isSuccess)
            assertEquals(1, authManager.signOutCallCount)
        }

    @Test
    fun `S9 signOut failure is returned after successful purge`() =
        runTest {
            authManager.signOutResult = Result.failure(Exception("session expired"))

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals("session expired", result.exceptionOrNull()?.message)
            assertEquals(1, authManager.signOutCallCount)
        }

    @Test
    fun `E5 cloud purge failure skips signOut`() =
        runTest {
            cloudSyncPort.deleteUserAccountResult = Result.failure(Exception("network error"))

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals("network error", result.exceptionOrNull()?.message)
            assertEquals(0, authManager.signOutCallCount)
        }
}
