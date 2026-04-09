package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveApiModelConfigurationUseCaseTest {
    private val repository = mockk<ApiModelRepositoryPort>()
    private lateinit var useCase: SaveApiModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveApiModelConfigurationUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveConfiguration`() = runTest {
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("1"), apiCredentialsId = 10, displayName = "Test Config")
        coEvery { repository.getCredentialsById(10) } returns mockk()
        coEvery { repository.saveConfiguration(config) } returns ApiModelConfigurationId("1")

        val result = useCase(config)

        assertTrue(result.isSuccess)
        assertEquals(ApiModelConfigurationId("1"), result.getOrNull())
        coVerify(exactly = 1) { repository.saveConfiguration(config) }
    }

    @Test
    fun `invoke returns failure when parent credentials do not exist`() = runTest {
        val config = ApiModelConfiguration(id = ApiModelConfigurationId("1"), apiCredentialsId = 999, displayName = "Test Config")
        coEvery { repository.getCredentialsById(999) } returns null

        val result = useCase(config)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}