package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartCloudSyncListeningUseCaseTest {
    private val cloudSyncPort = mockk<CloudSyncPort>()
    private lateinit var useCase: StartCloudSyncListeningUseCase

    @BeforeEach
    fun setUp() {
        useCase = StartCloudSyncListeningUseCase(cloudSyncPort)
        every { cloudSyncPort.startListening() } returns Unit
    }

    @Test
    fun `SC-ARCH-01-04 uploads then starts listener when uploadLocalFirst true`() =
        runTest {
            coEvery { cloudSyncPort.syncAllLocalDataToCloud() } returns Result.success(Unit)

            val result = useCase(uploadLocalFirst = true)

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { cloudSyncPort.syncAllLocalDataToCloud() }
            verify(exactly = 1) { cloudSyncPort.startListening() }
        }

    @Test
    fun `SC-ARCH-01-05 skips upload when uploadLocalFirst false`() =
        runTest {
            val result = useCase(uploadLocalFirst = false)

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { cloudSyncPort.syncAllLocalDataToCloud() }
            verify(exactly = 1) { cloudSyncPort.startListening() }
        }

    @Test
    fun `SC-ARCH-01-MUTATION still starts listener when upload fails`() =
        runTest {
            coEvery { cloudSyncPort.syncAllLocalDataToCloud() } returns Result.failure(IllegalStateException("upload failed"))

            val result = useCase(uploadLocalFirst = true)

            assertTrue(result.isSuccess)
            verify(exactly = 1) { cloudSyncPort.startListening() }
        }
}
