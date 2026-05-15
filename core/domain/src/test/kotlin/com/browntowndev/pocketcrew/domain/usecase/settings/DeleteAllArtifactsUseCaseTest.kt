package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeleteAllArtifactsUseCaseTest {

    private val cleanupRepository = mockk<CleanupRepository>()
    private val useCase = DeleteAllArtifactsUseCase(cleanupRepository)

    @Test
    fun `S7 delegates deleteAllArtifacts exactly once`() = runTest {
        coEvery { cleanupRepository.deleteAllArtifacts() } returns Unit

        useCase()

        coVerify(exactly = 1) { cleanupRepository.deleteAllArtifacts() }
    }

    @Test
    fun `E4 propagates cleanup failures`() = runTest {
        coEvery { cleanupRepository.deleteAllArtifacts() } throws RuntimeException("io error")

        val thrown = org.junit.jupiter.api.assertThrows<RuntimeException> {
            useCase()
        }
        org.junit.jupiter.api.Assertions.assertEquals("io error", thrown.message)
    }
}
