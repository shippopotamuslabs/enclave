package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveLocalModelConfigurationUseCaseTest {
    private val repository = mockk<ModelRegistryPort>()
    private lateinit var useCase: SaveLocalModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveLocalModelConfigurationUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveConfiguration`() = runTest {
        val config = mockk<LocalModelConfiguration>()
        coEvery { repository.saveConfiguration(config) } returns 1L

        val result = useCase(config)

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        coVerify(exactly = 1) { repository.saveConfiguration(config) }
    }
}