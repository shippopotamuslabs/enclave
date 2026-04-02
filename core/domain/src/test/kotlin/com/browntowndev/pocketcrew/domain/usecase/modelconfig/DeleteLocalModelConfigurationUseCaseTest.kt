package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteLocalModelConfigurationUseCaseTest {
    private val repository = mockk<ModelRegistryPort>()
    private lateinit var useCase: DeleteLocalModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = DeleteLocalModelConfigurationUseCaseImpl(repository)
    }

    @Test
    fun `invoke deletes specific configuration`() = runTest {
        val configId = 1L
        coEvery { repository.deleteConfiguration(configId) } returns Unit

        val result = useCase(configId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.deleteConfiguration(configId) }
    }
}