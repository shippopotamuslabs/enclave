package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveLocalModelConfigurationUseCaseTest {
    private val repository = mockk<LocalModelRepositoryPort>()
    private lateinit var useCase: SaveLocalModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveLocalModelConfigurationUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveConfiguration`() = runTest {
        val config = mockk<LocalModelConfiguration>()
        val expectedId = LocalModelConfigurationId("1")
        coEvery { repository.saveConfiguration(config) } returns expectedId

        val result = useCase(config)

        assertTrue(result.isSuccess)
        assertEquals(expectedId, result.getOrNull())
        coVerify(exactly = 1) { repository.saveConfiguration(config) }
    }
}