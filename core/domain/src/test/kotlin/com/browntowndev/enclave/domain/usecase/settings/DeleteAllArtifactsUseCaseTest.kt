package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeCleanupRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DeleteAllArtifactsUseCaseTest {
    private val cleanupRepository = FakeCleanupRepository()
    private val useCase = DeleteAllArtifactsUseCase(cleanupRepository)

    @Test
    fun `S7 delegates deleteAllArtifacts exactly once`() =
        runTest {
            useCase()

            assertEquals(1, cleanupRepository.deleteAllArtifactsCallCount)
        }

    @Test
    fun `E4 propagates cleanup failures`() =
        runTest {
            cleanupRepository.deleteAllArtifactsFailure = RuntimeException("io error")

            val thrown =
                assertThrows<RuntimeException> {
                    useCase()
                }
            assertEquals("io error", thrown.message)
        }
}
